package io.github.wumisc.woilaibangni.core.asr

import android.os.SystemClock
import android.util.Log
import io.github.wumisc.woilaibangni.core.audio.PcmRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

/**
 * 一次「对话会话」：录音 → 能量 VAD 断句 → 识别引擎 → 字幕状态。
 *
 * 说明：
 * - 这里用的是最朴素的能量 VAD，先跑通时序；M0 后期换成 silero VAD（sherpa-onnx 内置）。
 * - 所有延迟打点都在本类内完成，UI 只读 [state]，不在界面里算时间。
 */
class AsrSession(
    private val recorder: PcmRecorder = PcmRecorder(),
    private val engine: StreamingAsrEngine = DemoAsrEngine(),
    private val silenceHoldMs: Long = 600,
    private val speechThreshold: Float = 0.012f,
) {

    data class UiState(
        val listening: Boolean = false,
        val speaking: Boolean = false,
        val partial: String = "",
        val committed: String = "",
        val error: String? = null,
        /** VAD 判定说话开始 → 第一个中间结果上屏的毫秒数。 */
        val firstPartialLatencyMs: Long? = null,
        /** VAD 判定说话结束 → 定稿回填的毫秒数。 */
        val finalizeLatencyMs: Long? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var lastVoiceAt = 0L
    private var utteranceStartedAt = 0L
    private var firstPartialLogged = false
    private val pcm = StringBuilder()

    fun start(): Boolean {
        if (_state.value.listening) return true
        val ok = recorder.start { frame -> onFrame(frame) }
        _state.value = _state.value.copy(listening = ok, error = if (ok) null else "无法打开麦克风，请检查录音权限")
        return ok
    }

    fun stop() {
        recorder.stop()
        _state.value = _state.value.copy(listening = false, speaking = false)
    }

    fun release() {
        stop()
        engine.release()
    }

    private fun onFrame(frame: ShortArray) {
        val rms = rms(frame)
        val now = SystemClock.elapsedRealtime()

        if (rms >= speechThreshold) {
            lastVoiceAt = now
            if (!_state.value.speaking) {
                utteranceStartedAt = now
                firstPartialLogged = false
                pcm.clear()
                _state.value = _state.value.copy(speaking = true, partial = "")
            }
            engine.feed(frame, SAMPLE_RATE) { partial ->
                if (partial.isNotBlank()) {
                    val latency = if (!firstPartialLogged) now - utteranceStartedAt else null
                    if (!firstPartialLogged) firstPartialLogged = true
                    if (latency != null) Log.i(PERF_TAG, "first_partial_ms=$latency")
                    _state.value = _state.value.copy(
                        partial = partial,
                        firstPartialLatencyMs = latency ?: _state.value.firstPartialLatencyMs,
                    )
                }
            }
        } else if (_state.value.speaking && now - lastVoiceAt > silenceHoldMs) {
            val finalStart = now
            val text = engine.finishUtterance()
            if (text.isNotBlank()) {
                pcm.append(text)
                val finalizeMs = SystemClock.elapsedRealtime() - finalStart
                Log.i(PERF_TAG, "finalize_ms=$finalizeMs chars=${text.length}")
                _state.value = _state.value.copy(
                    speaking = false,
                    partial = "",
                    committed = text,
                    finalizeLatencyMs = finalizeMs,
                )
            } else {
                _state.value = _state.value.copy(speaking = false, partial = "")
            }
        }
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
    }
}
