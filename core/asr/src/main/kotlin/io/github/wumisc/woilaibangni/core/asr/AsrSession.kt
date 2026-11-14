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
    private val silenceHoldMs: Long = 600,
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

    private var lastVoiceAt = 0L
    private var utteranceStartedAt = 0L
    private var firstPartialLogged = false
    private var lastLevelLogAt = 0L
    private var windowPeak = 0f
    private var frameCount = 0

    /** 当前这句的 PCM，供第二遍识别使用；按块累积，避免整段复制。 */
    private val utteranceChunks = ArrayList<ShortArray>()
    private var utteranceSamples = 0

    /** 两个模型包分别准备；只装对话包也能用。 */
    suspend fun prepare(streamDir: String, offlineDir: String): Boolean =
        (engine as? TwoPassAsrEngine)?.prepare(streamDir, offlineDir) ?: engine.prepare(streamDir)

    fun start(): Boolean {
        if (_state.value.listening) return true
        val ok = recorder.start { frame -> onFrame(frame) }
        LogHub.asr("WOILAI_ASR", "[$label] recorder start=$ok")
        _state.value = _state.value.copy(listening = ok, error = if (ok) null else "无法打开麦克风，请检查录音权限")
        return ok
    }

    fun stop() {
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
        val rms = rms(frame)
        val now = SystemClock.elapsedRealtime()

        // 每秒打一次电平，用来判断"到底有没有收到声音"
        frameCount++
        if (rms > windowPeak) windowPeak = rms
        if (now - lastLevelLogAt >= 1000) {
            LogHub.asr("WOILAI_ASR", "[$label] level peak=${"%.4f".format(windowPeak)} speaking=${_state.value.speaking}")
            lastLevelLogAt = now
            windowPeak = 0f
            frameCount = 0
        }

        if (rms >= speechThreshold) {
            lastVoiceAt = now
            if (!_state.value.speaking) {
                utteranceStartedAt = now
                firstPartialLogged = false
                utteranceChunks.clear()
                utteranceSamples = 0
                _state.value = _state.value.copy(speaking = true, partial = "", revised = false)
            }
            appendAudio(frame)

            engine.feed(frame, SAMPLE_RATE) { partial ->
                if (partial.isNotBlank()) {
                    val latency = if (!firstPartialLogged) now - utteranceStartedAt else null
                    if (!firstPartialLogged) firstPartialLogged = true
                    if (latency != null) LogHub.perf(PERF_TAG, "first_partial_ms=$latency")
                    _state.value = _state.value.copy(
                        partial = partial,
                        firstPartialLatencyMs = latency ?: _state.value.firstPartialLatencyMs,
                    )
                }
            }
        } else if (_state.value.speaking && now - lastVoiceAt > silenceHoldMs) {
            val finalStart = now
            val text = engine.finishUtterance()
            val captured = buildUtterance()

            if (text.isNotBlank()) {
                val finalizeMs = SystemClock.elapsedRealtime() - finalStart
                LogHub.perf(PERF_TAG, "finalize_ms=$finalizeMs chars=${text.length}")
                _state.value = _state.value.copy(
                    speaking = false,
                    partial = "",
                    committed = text,
                    revised = false,
                    finalizeLatencyMs = finalizeMs,
                )
                refineAsync(text, captured)
            } else {
                _state.value = _state.value.copy(speaking = false, partial = "")
            }
        }
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
    }
}
