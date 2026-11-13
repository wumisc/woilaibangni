package io.github.wumisc.woilaibangni.core.asr

import android.util.Log

/**
 * 非流式高精度识别引擎（第二遍）。
 *
 * 与 [StreamingAsrEngine] 的分工：
 * - 第一遍（流式）负责「立刻看见」：5 行字以内先上屏，允许抖动；
 * - 第二遍（本接口）负责「最后说对」：一句话结束后把整段 PCM 重跑一次，
 *   换成带标点的定稿。第二遍慢 200~600ms 是可以接受的，因为它不阻塞字幕。
 */
interface OfflineAsrEngine {
    val isReady: Boolean

    suspend fun prepare(modelDir: String): Boolean

    suspend fun transcribe(samples: ShortArray, sampleRate: Int): String?

    fun release()
}

/**
 * 两遍识别的编排器。对外仍是一个 [StreamingAsrEngine]，所以 [AsrSession] 不用改调用方式。
 *
 * 参考实现：sherpa-onnx 官方 `android/SherpaOnnx2Pass` 示例（Apache-2.0）。
 */
class TwoPassAsrEngine(
    private val streaming: StreamingAsrEngine,
    private val offline: OfflineAsrEngine,
) : StreamingAsrEngine {

    override val isReady: Boolean get() = streaming.isReady

    var offlineReady: Boolean = false
        private set

    override suspend fun prepare(modelDir: String): Boolean {
        // 允许只装实时包：第二遍装不上就退化为单遍，不影响可用性
        val first = streaming.prepare(modelDir)
        offlineReady = runCatching { offline.prepare(modelDir) }.getOrDefault(false)
        Log.i(TAG, "prepare stream=$first offline=$offlineReady")
        return first
    }

    override fun feed(samples: ShortArray, sampleRate: Int, onPartial: (String) -> Unit) =
        streaming.feed(samples, sampleRate, onPartial)

    override fun finishUtterance(): String = streaming.finishUtterance()

    override suspend fun correct(samples: ShortArray, sampleRate: Int): String? {
        if (!offlineReady || !offline.isReady) return null
        return offline.transcribe(samples, sampleRate)
    }

    override fun release() {
        streaming.release()
        offline.release()
    }

    companion object {
        private const val TAG = "WOILAI_ASR"
    }
}

/**
 * sherpa-onnx 离线识别后端（SenseVoice / Paraformer 等非流式模型）。
 *
 * 现状：AAR 不在 Maven Central，需要把 `sherpa-onnx-jni` 的 .so 放进 `jniLibs`。
 * 本类在库缺失时静默降级为「不可用」，不会让 App 崩，也不会假装识别成功。
 */
class SherpaOfflineAsrEngine : OfflineAsrEngine {

    private var nativeLoaded = false
    private var modelReady = false

    override val isReady: Boolean get() = nativeLoaded && modelReady

    override suspend fun prepare(modelDir: String): Boolean {
        nativeLoaded = runCatching { System.loadLibrary("sherpa-onnx-jni") }.isSuccess
        if (!nativeLoaded) {
            Log.i(TAG, "sherpa-onnx-jni 未打包，第二遍识别暂不可用")
            return false
        }
        modelReady = false
        Log.i(TAG, "native 已加载，模型目录=$modelDir")
        return modelReady
    }

    override suspend fun transcribe(samples: ShortArray, sampleRate: Int): String? = null

    override fun release() {
        modelReady = false
    }

    companion object {
        private const val TAG = "WOILAI_ASR"
    }
}

/** 链路自检用：永远返回 null，代表"没有第二遍"，便于对比延迟。 */
class NoopOfflineAsrEngine : OfflineAsrEngine {
    override val isReady: Boolean = false
    override suspend fun prepare(modelDir: String): Boolean = false
    override suspend fun transcribe(samples: ShortArray, sampleRate: Int): String? = null
    override fun release() = Unit
}
