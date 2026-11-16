package io.github.wumisc.woilaibangni.core.asr

import android.os.SystemClock
import io.github.wumisc.woilaibangni.core.audio.PcmRecorder
import io.github.wumisc.woilaibangni.core.log.LogHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * 一次「对话会话」：录音 → 能量 VAD 断句 → 两遍识别 → 字幕状态。
 *
 * 两遍策略：
 * - 说话过程中只用流式引擎出中间结果（不碰第二遍，避免抢占 CPU）；
 * - 静音 600ms 判定一句结束，先拿流式定稿**立刻上屏**；
 * - 同时在后台把整句 PCM 交给 [OfflineAsrEngine] 重跑，成功后替换文案并标记 `revised`。
 *
 * 所有延迟打点都在本类内完成，UI 只读 [state]。
 */
class AsrSession(
    private val recorder: PcmRecorder = PcmRecorder(),
    private val engine: StreamingAsrEngine = DemoAsrEngine(),
    private val silenceHoldMs: Long = 400,
    private val speechThreshold: Float = 0.004f,
    /** 日志前缀，区分主界面与悬浮字幕两路会话。 */
    private val label: String = "main",
) {

    data class UiState(
        val listening: Boolean = false,
        val speaking: Boolean = false,
        val partial: String = "",
        val committed: String = "",
        val error: String? = null,
        /** 定稿是否已被第二遍识别替换过。 */
        val revised: Boolean = false,
        /** VAD 判定说话开始 → 第一个中间结果上屏的毫秒数。 */
        val firstPartialLatencyMs: Long? = null,
        /** VAD 判定说话结束 → 定稿回填的毫秒数。 */
        val finalizeLatencyMs: Long? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 防止 start/stop 重入：连点按钮、服务与界面同时调用都只生效一次。 */
    private val startGuard = AtomicBoolean(false)

    private var lastVoiceAt = 0L
    private var utteranceStartedAt = 0L
    private var firstPartialLogged = false
    private var lastLevelLogAt = 0L
    private var windowPeak = 0f
    private var frameCount = 0
    private var noiseFloor = 0.0015f
    private var lastGain = 1f
    private var lastPartialLogAt = 0L

    /** 当前这句的 PCM，供第二遍识别使用；按块累积，避免整段复制。 */
    private val utteranceChunks = ArrayList<ShortArray>()
    private var utteranceSamples = 0

    /** 两个模型包分别准备；只装对话包也能用。 */
    suspend fun prepare(streamDir: String, offlineDir: String): Boolean =
        (engine as? TwoPassAsrEngine)?.prepare(streamDir, offlineDir) ?: engine.prepare(streamDir)

    fun start(): Boolean {
        if (!startGuard.compareAndSet(false, true)) return _state.value.listening
        // 每次开麦都从干净状态开始，避免上一轮残留的 VAD 计数把句子判错
        lastVoiceAt = 0L
        utteranceStartedAt = 0L
        firstPartialLogged = false
        utteranceChunks.clear()
        utteranceSamples = 0
        val ok = runCatching { recorder.start { frame -> onFrame(frame) } }.getOrElse {
            LogHub.error(LogHub.Channel.ASR, "WOILAI_ASR", "[$label] 录音启动异常", it)
            false
        }
        if (!ok) startGuard.set(false)
        LogHub.asr("WOILAI_ASR", "[$label] recorder start=$ok")
        _state.value = _state.value.copy(
            listening = ok,
            speaking = false,
            partial = "",
            error = if (ok) null else "无法打开麦克风，请检查录音权限",
        )
        return ok
    }

    fun stop() {
        if (!startGuard.compareAndSet(true, false)) {
            // 没在录音，但可能残留 listening=true，顺手清掉
            if (_state.value.listening) _state.value = _state.value.copy(listening = false, speaking = false, partial = "")
            return
        }
        val pending = _state.value.speaking || utteranceSamples > 0
        recorder.stop()
        // 手动停止时也把这句定稿，不依赖 VAD 判定
        if (pending) {
            val text = runCatching { engine.finishUtterance() }.getOrDefault("")
            if (text.isNotBlank()) {
                val captured = buildUtterance()
                _state.value = _state.value.copy(
                    listening = false,
                    speaking = false,
                    partial = "",
                    committed = text,
                )
                refineAsync(text, captured)
            } else {
                _state.value = _state.value.copy(listening = false, speaking = false, partial = "")
            }
        }
        LogHub.asr("WOILAI_ASR", "[$label] recorder stopped")
        _state.value = _state.value.copy(listening = false, speaking = false)
    }

    fun release() {
        stop()
        scope.cancel()
        engine.release()
    }

    private fun onFrame(frame: ShortArray) {
        val now = SystemClock.elapsedRealtime()
        val rms = rms(frame)

        // 每秒打一次电平，用来判断"到底有没有收到声音"
        frameCount++
        if (rms > windowPeak) windowPeak = rms
        if (now - lastLevelLogAt >= 1000) {
            LogHub.asr(
                "WOILAI_ASR",
                "[$label] level peak=${"%.4f".format(windowPeak)} noise=${"%.4f".format(noiseFloor)} gain=${"%.1f".format(lastGain)} speaking=${_state.value.speaking}",
            )
            lastLevelLogAt = now
            windowPeak = 0f
            frameCount = 0
        }

        // 说话时实时估计底噪；静音段的均值就是环境噪声
        if (!_state.value.speaking) noiseFloor = noiseFloor * 0.9f + rms * 0.1f
        // 阈值跟着底噪走：安静房间用 0.0025，嘈杂环境自动抬高，避免被噪声一直判成"在说话"
        val threshold = maxOf(speechThreshold, noiseFloor * 4f)

        // 手机放桌上、离嘴半米时原始 RMS 常常只有 0.003~0.008，直接喂给模型基本识别不出
        val gain = (TARGET_RMS / rms.coerceAtLeast(1e-4f)).coerceIn(1f, MAX_GAIN)
        lastGain = gain
        val boosted = amplify(frame, gain)

        // 全程都喂：断着喂会让流式解码器丢上下文，中间结果永远出不来
        engine.feed(boosted, SAMPLE_RATE) { partial ->
            if (partial.isBlank() || !_state.value.speaking) return@feed
            val latency = if (!firstPartialLogged) now - utteranceStartedAt else null
            if (!firstPartialLogged) {
                firstPartialLogged = true
                if (latency != null) LogHub.perf(PERF_TAG, "first_partial_ms=$latency")
            }
            _state.value = _state.value.copy(
                partial = partial,
                firstPartialLatencyMs = latency ?: _state.value.firstPartialLatencyMs,
            )
            if (now - lastPartialLogAt >= 1000) {
                lastPartialLogAt = now
                LogHub.asr("WOILAI_ASR", "[$label] partial=$partial")
            }
        }

        if (rms >= threshold) {
            lastVoiceAt = now
            if (!_state.value.speaking) {
                utteranceStartedAt = now
                firstPartialLogged = false
                utteranceChunks.clear()
                utteranceSamples = 0
                _state.value = _state.value.copy(speaking = true, partial = "", revised = false)
            }
            appendAudio(boosted)
        } else if (_state.value.speaking && now - lastVoiceAt > silenceHoldMs) {
            val finalStart = now
            val text = engine.finishUtterance()
            val captured = buildUtterance()

            if (text.isNotBlank()) {
                val finalizeMs = SystemClock.elapsedRealtime() - finalStart
                LogHub.perf(PERF_TAG, "finalize_ms=$finalizeMs chars=${text.length}")
                LogHub.asr("WOILAI_ASR", "[$label] committed=$text")
                _state.value = _state.value.copy(
                    speaking = false,
                    partial = "",
                    committed = text,
                    revised = false,
                    finalizeLatencyMs = finalizeMs,
                )
                refineAsync(text, captured)
            } else {
                LogHub.asr("WOILAI_ASR", "[$label] 一句结束但没识别出文字（可能是噪声或音量太低）")
                _state.value = _state.value.copy(speaking = false, partial = "")
            }
        }
    }

    /** 线性增益 + 削顶，别让声音越大越失真。 */
    private fun amplify(frame: ShortArray, gain: Float): ShortArray {
        if (gain <= 1.01f) return frame
        val out = ShortArray(frame.size)
        for (i in frame.indices) {
            out[i] = (frame[i] * gain).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    /** 第二遍识别：成功且与第一遍不同才替换，避免把好结果改坏。 */
    private fun refineAsync(firstPass: String, captured: ShortArray) {
        if (captured.isEmpty()) return
        scope.launch {
            val started = SystemClock.elapsedRealtime()
            val better = runCatching { engine.correct(captured, SAMPLE_RATE) }.getOrNull()
            if (better.isNullOrBlank()) return@launch
            if (better == firstPass) return@launch
            // 只在用户还没开始下一句时替换，否则会把新字幕冲掉
            val current = _state.value
            if (current.committed != firstPass || current.speaking) return@launch
            val cost = SystemClock.elapsedRealtime() - started
            LogHub.perf(PERF_TAG, "revise_ms=$cost chars=${better.length}")
            _state.value = current.copy(committed = better, revised = true)
        }
    }

    private fun appendAudio(frame: ShortArray) {
        if (utteranceSamples + frame.size > MAX_UTTERANCE_SAMPLES) return
        utteranceChunks += frame.copyOf()
        utteranceSamples += frame.size
    }

    private fun buildUtterance(): ShortArray {
        if (utteranceSamples == 0) return ShortArray(0)
        val out = ShortArray(utteranceSamples)
        var offset = 0
        for (chunk in utteranceChunks) {
            chunk.copyInto(out, offset, 0, minOf(chunk.size, utteranceSamples - offset))
            offset += chunk.size
        }
        return out
    }

    private fun rms(frame: ShortArray): Float {
        if (frame.isEmpty()) return 0f
        var sum = 0.0
        for (s in frame) {
            val v = s / 32768.0
            sum += v * v
        }
        return sqrt(sum / frame.size).toFloat()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val PERF_TAG = "WOILAI_PERF"

        /** 单句上限 60 秒，防止 VAD 失效时把内存吃满。 */
        private const val MAX_UTTERANCE_SAMPLES = SAMPLE_RATE * 60

        /** 目标电平：把用户声音拉到这个 RMS 附近再交给模型。 */
        private const val TARGET_RMS = 0.06f

        /** 最多放大 8 倍，再大就是纯噪声了。 */
        private const val MAX_GAIN = 8f
    }
}
