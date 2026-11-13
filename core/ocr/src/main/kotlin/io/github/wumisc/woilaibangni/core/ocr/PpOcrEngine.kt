package io.github.wumisc.woilaibangni.core.ocr

import io.github.wumisc.woilaibangni.core.log.LogHub
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import android.graphics.Rect
import java.io.File
import java.nio.FloatBuffer

/**
 * PP-OCRv5 mobile（det + rec）的 ONNX Runtime 推理。
 *
 * 模型文件约定（放在 `modelDir` 下）：
 * - `det.onnx`：DB 检测
 * - `rec.onnx`：CTC 识别
 * - `ppocr_keys_v1.txt` / `keys.txt`：字典，一行一个字
 *
 * 为什么不用 Paddle Lite：ONNX 版本体积更小（int8 约 15~25MB），
 * 且和 sherpa-onnx 共用同一套运行时思路，少引一个 native 依赖。
 */
class PpOcrEngine(private val modelDir: String) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var dict: List<String> = emptyList()

    var isReady: Boolean = false
        private set

    fun load(): Boolean {
        if (isReady) return true
        val det = File(modelDir, "det.onnx")
        val rec = File(modelDir, "rec.onnx")
        if (!det.isFile || !rec.isFile) {
            LogHub.ocr(TAG, "缺模型文件：$modelDir")
            return false
        }
        val keys = listOf("ppocr_keys_v1.txt", "keys.txt", "dict.txt")
            .map { File(modelDir, it) }
            .firstOrNull { it.isFile }
        if (keys == null) {
            LogHub.ocr(TAG, "缺字典文件")
            return false
        }

        return try {
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            detSession = env.createSession(det.absolutePath, options)
            recSession = env.createSession(rec.absolutePath, options)
            dict = keys.readLines(Charsets.UTF_8).map { it.trimEnd('\r', '\n') }
            isReady = true
            LogHub.ocr(TAG, "OCR 模型已加载 dict=${dict.size}")
            true
        } catch (t: Throwable) {
            LogHub.error(LogHub.Channel.OCR, TAG, "OCR 初始化失败", t)
            close()
            false
        }
    }

    fun detect(bitmap: Bitmap): List<Rect> {
        val session = detSession ?: return emptyList()
        val input = PpOcrPreprocess.toDetInput(bitmap)
        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(input.data),
            longArrayOf(1, 3, input.height.toLong(), input.width.toLong()),
        )
        tensor.use {
            session.run(mapOf(session.inputNames.first() to it)).use { result ->
                val out = result[0] as OnnxTensor
                val shape = out.info.shape
                val mapH = shape[shape.size - 2].toInt()
                val mapW = shape[shape.size - 1].toInt()
                val probBuf = out.floatBuffer
                val prob = FloatArray(mapW * mapH)
                probBuf.get(prob)
                return PpOcrPreprocess.toBoxes(prob, mapW, mapH, input.scale)
            }
        }
    }

    fun recognize(bitmap: Bitmap, box: Rect): CtcDecoder.Decoded? {
        val session = recSession ?: return null
        val (data, width) = PpOcrPreprocess.toRecInput(bitmap, box)
        if (data.isEmpty() || width <= 0) return null

        val tensor = OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(data),
            longArrayOf(1, 3, 48, width.toLong()),
        )
        tensor.use {
            session.run(mapOf(session.inputNames.first() to it)).use { result ->
                val out = result[0] as OnnxTensor
                val shape = out.info.shape
                val timeSteps = shape[shape.size - 2].toInt()
                val classes = shape[shape.size - 1].toInt()
                val logits = FloatArray(timeSteps * classes)
                out.floatBuffer.get(logits)
                return CtcDecoder.decode(logits, timeSteps, classes, dict)
            }
        }
    }

    fun close() {
        runCatching { detSession?.close() }
        runCatching { recSession?.close() }
        detSession = null
        recSession = null
        isReady = false
    }

    companion object {
        private const val TAG = "WOILAI_OCR"
    }
}
