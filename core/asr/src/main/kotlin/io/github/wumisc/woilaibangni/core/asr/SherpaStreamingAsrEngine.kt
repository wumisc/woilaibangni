package io.github.wumisc.woilaibangni.core.asr

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 流式识别（第一遍）：sherpa-onnx 的 streaming zipformer transducer。
 *
 * 模型目录约定：
 * ```
 * encoder.onnx  decoder.onnx  joiner.onnx  tokens.txt
 * ```
 * 用 [`sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20`] 这类双语流式模型，
 * 中文和英文混说不需要切模型——这对「面对面」场景是硬要求。
 */
class SherpaStreamingAsrEngine(private val threads: Int = 2) : StreamingAsrEngine {

    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null

    override var isReady: Boolean = false
        private set

    override suspend fun prepare(modelDir: String): Boolean = withContext(Dispatchers.IO) {
        if (isReady) return@withContext true
        val encoder = File(modelDir, "encoder.onnx")
        val decoder = File(modelDir, "decoder.onnx")
        val joiner = File(modelDir, "joiner.onnx")
        val tokens = File(modelDir, "tokens.txt")
        if (!encoder.isFile || !decoder.isFile || !joiner.isFile || !tokens.isFile) {
            Log.w(TAG, "流式模型不完整：$modelDir")
            return@withContext false
        }

        runCatching {
            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = encoder.absolutePath,
                        decoder = decoder.absolutePath,
                        joiner = joiner.absolutePath,
                    ),
                    tokens = tokens.absolutePath,
                    numThreads = threads,
                    modelType = "zipformer",
                ),
                decodingMethod = "greedy_search",
                enableEndpoint = false, // 断句由我们的能量 VAD 负责，避免两套端点打架
            )
            val rec = OnlineRecognizer(config = config)
            recognizer = rec
            stream = rec.createStream()
            isReady = true
            Log.i(TAG, "流式识别已加载 threads=$threads")
            true
        }.getOrElse {
            Log.e(TAG, "流式识别初始化失败", it)
            isReady = false
            false
        }
    }

    override fun feed(samples: ShortArray, sampleRate: Int, onPartial: (String) -> Unit) {
        val rec = recognizer ?: return
        val s = stream ?: return
        s.acceptWaveform(samples.toFloatArray(), sampleRate)
        while (rec.isReady(s)) rec.decode(s)
        val text = rec.getResult(s).text
        if (text.isNotBlank()) onPartial(text)
    }

    override fun finishUtterance(): String {
        val rec = recognizer ?: return ""
        val s = stream ?: return ""
        return runCatching {
            s.inputFinished()
            while (rec.isReady(s)) rec.decode(s)
            val text = rec.getResult(s).text
            s.release()
            stream = rec.createStream()
            text
        }.getOrElse {
            Log.e(TAG, "定稿失败", it)
            ""
        }
    }

    override fun release() {
        runCatching { stream?.release() }
        runCatching { recognizer?.release() }
        stream = null
        recognizer = null
        isReady = false
    }

    companion object {
        private const val TAG = "WOILAI_ASR"
    }
}

private fun ShortArray.toFloatArray(): FloatArray {
        val out = FloatArray(size)
        for (i in out.indices) out[i] = this[i] / 32768f
        return out
}
