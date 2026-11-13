package io.github.wumisc.woilaibangni.core.asr

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

    override suspend fun prepare(modelDir: String): Boolean = prepare(modelDir, modelDir)

    /**
     * 两个包分开准备：对话包（80MB 流式）和高精度包（230MB SenseVoice）是独立下载的，
     * 只装前者也能用，只是少了句末校正。
     */
    suspend fun prepare(streamDir: String, offlineDir: String): Boolean {
        val first = runCatching { streaming.prepare(streamDir) }.getOrDefault(false)
        offlineReady = runCatching { offline.prepare(offlineDir) }.getOrDefault(false)
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
class SherpaOfflineAsrEngine(private val threads: Int = 2) : OfflineAsrEngine {

    private var recognizer: OfflineRecognizer? = null
    private var modelReady = false

    override val isReady: Boolean get() = modelReady

    override suspend fun prepare(modelDir: String): Boolean = withContext(Dispatchers.IO) {
        if (modelReady) return@withContext true
        val model = listOf("model.onnx", "sensevoice.onnx", "model.int8.onnx")
            .map { File(modelDir, it) }
            .firstOrNull { it.isFile }
        val tokens = File(modelDir, "tokens.txt")
        if (model == null || !tokens.isFile) {
            Log.w(TAG, "高精度模型不完整：$modelDir")
            return@withContext false
        }

        runCatching {
            val config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = model.absolutePath,
                        language = "auto",
                        useInverseTextNormalization = true,
                    ),
                    tokens = tokens.absolutePath,
                    numThreads = threads,
                    modelType = "sensevoice",
                ),
                decodingMethod = "greedy_search",
            )
            recognizer = OfflineRecognizer(config = config)
            modelReady = true
            Log.i(TAG, "高精度识别已加载 model=${model.name}")
            true
        }.getOrElse {
            Log.e(TAG, "高精度识别初始化失败", it)
            modelReady = false
            false
        }
    }

    override suspend fun transcribe(samples: ShortArray, sampleRate: Int): String? = withContext(Dispatchers.Default) {
        val rec = recognizer ?: return@withContext null
        if (samples.isEmpty()) return@withContext null
        runCatching {
            val stream = rec.createStream()
            try {
                stream.acceptWaveform(samples.toFloatArray(), sampleRate)
                rec.decode(stream)
                rec.getResult(stream).text.trim().ifBlank { null }
            } finally {
                stream.release()
            }
        }.getOrElse {
            Log.e(TAG, "第二遍识别失败", it)
            null
        }
    }

    override fun release() {
        runCatching { recognizer?.release() }
        recognizer = null
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

private fun ShortArray.toFloatArray(): FloatArray {
    val out = FloatArray(size)
    for (i in out.indices) out[i] = this[i] / 32768f
    return out
}
