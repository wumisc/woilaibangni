package io.github.wumisc.woilaibangni.core.ocr

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * PP-OCRv5 的预处理与 DB 后处理。
 *
 * 参考实现（Apache-2.0）：RapidAI/RapidOcrOnnx 的 `OcrLite`。
 * 我们不做四点框的精确回归，先用「二值图连通域的外接矩形」——
 * 手机拍纸面/药盒时文字基本是正的，损失一点精度换来代码量减半；
 * 倾斜严重的场景由 M1 的方向分类模型补。
 */
internal object PpOcrPreprocess {

    const val LIMIT_SIDE = 960
    const val DET_MEAN_R = 0.485f
    const val DET_MEAN_G = 0.456f
    const val DET_MEAN_B = 0.406f
    const val DET_STD_R = 0.229f
    const val DET_STD_G = 0.224f
    const val DET_STD_B = 0.225f

    data class DetInput(val data: FloatArray, val width: Int, val height: Int, val scale: Float)

    /** 等比缩放（最长边不超过 960）并补齐到 32 的倍数，输出 NCHW。 */
    fun toDetInput(bitmap: Bitmap): DetInput {
        val longSide = maxOf(bitmap.width, bitmap.height)
        val scale = if (longSide > LIMIT_SIDE) LIMIT_SIDE.toFloat() / longSide else 1f
        val dstW = (bitmap.width * scale).toInt().coerceAtLeast(32).let { (it / 32) * 32 }
        val dstH = (bitmap.height * scale).toInt().coerceAtLeast(32).let { (it / 32) * 32 }

        val scaled = Bitmap.createScaledBitmap(bitmap, dstW, dstH, true)
        val pixels = IntArray(dstW * dstH)
        scaled.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
        if (scaled !== bitmap) scaled.recycle()

        val out = FloatArray(3 * dstW * dstH)
        val plane = dstW * dstH
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF) / 255f
            val g = ((p shr 8) and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            out[i] = (r - DET_MEAN_R) / DET_STD_R
            out[plane + i] = (g - DET_MEAN_G) / DET_STD_G
            out[2 * plane + i] = (b - DET_MEAN_B) / DET_STD_B
        }
        return DetInput(out, dstW, dstH, scale)
    }

    /** 二值化 + 连通域 → 外接矩形（坐标已映射回原图）。 */
    fun toBoxes(prob: FloatArray, mapW: Int, mapH: Int, scale: Float, threshold: Float = 0.3f, minArea: Int = 12): List<Rect> {
        val bin = BooleanArray(mapW * mapH)
        for (i in bin.indices) bin[i] = prob.getOrElse(i) { 0f } > threshold

        val labels = IntArray(bin.size) { -1 }
        val boxes = mutableListOf<Rect>()
        val stack = IntArray(bin.size)
        var label = 0

        for (start in bin.indices) {
            if (!bin[start] || labels[start] != -1) continue
            label++
            var sp = 0
            stack[sp++] = start
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            var area = 0

            while (sp > 0) {
                val idx = stack[--sp]
                if (labels[idx] != -1) continue
                labels[idx] = label
                val x = idx % mapW
                val y = idx / mapW
                area++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= mapW || ny >= mapH) continue
                    val nIdx = ny * mapW + nx
                    if (bin[nIdx] && labels[nIdx] == -1) stack[sp++] = nIdx
                }
            }

            if (area < minArea) continue
            val w = maxX - minX + 1
            val h = maxY - minY + 1
            if (w < 4 || h < 4) continue

            // unclip：按 面积/周长 的比例外扩，相当于还原文字外框
            val ratio = 1.6f
            val perimeter = 2 * (w + h).toFloat().coerceAtLeast(1f)
            val offset = (area * ratio / perimeter).coerceIn(1f, 8f)
            val rect = Rect(
                ((minX - offset) / scale).toInt(),
                ((minY - offset) / scale).toInt(),
                ((maxX + offset) / scale).toInt(),
                ((maxY + offset) / scale).toInt(),
            )
            boxes += rect
        }
        return boxes
    }

    /** 识别分支：高 48、宽按比例、上限 320，归一化到 [-1, 1]。 */
    fun toRecInput(source: Bitmap, box: Rect): Pair<FloatArray, Int> {
        val rect = Rect(
            box.left.coerceIn(0, source.width - 1),
            box.top.coerceIn(0, source.height - 1),
            box.right.coerceIn(1, source.width),
            box.bottom.coerceIn(1, source.height),
        )
        if (rect.width() < 2 || rect.height() < 2) return FloatArray(0) to 0

        val dstH = 48
        val dstW = (rect.width() * dstH.toFloat() / rect.height()).toInt().coerceIn(16, 320)
        val crop = Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height())
        val scaled = Bitmap.createScaledBitmap(crop, dstW, dstH, true)
        crop.recycle()

        val pixels = IntArray(dstW * dstH)
        scaled.getPixels(pixels, 0, dstW, 0, 0, dstW, dstH)
        scaled.recycle()

        val out = FloatArray(3 * dstW * dstH)
        val plane = dstW * dstH
        for (i in pixels.indices) {
            val p = pixels[i]
            out[i] = (((p shr 16) and 0xFF) / 255f - 0.5f) / 0.5f
            out[plane + i] = (((p shr 8) and 0xFF) / 255f - 0.5f) / 0.5f
            out[2 * plane + i] = ((p and 0xFF) / 255f - 0.5f) / 0.5f
        }
        return out to dstW
    }
}

/**
 * CTC 解码：blank = 0，字符索引 = argmax - 1 对应字典行号。
 * 连续重复只保留一次（CTC 的 collapse 规则）。
 */
object CtcDecoder {

    data class Decoded(val text: String, val confidence: Float)

    fun decode(logits: FloatArray, timeSteps: Int, classes: Int, dict: List<String>, minConfidence: Float = 0.5f): Decoded {
        val chars = StringBuilder()
        var prev = -1
        var sum = 0f
        var count = 0

        for (t in 0 until timeSteps) {
            val base = t * classes
            var best = 0
            var bestScore = logits[base]
            for (c in 1 until classes) {
                val v = logits[base + c]
                if (v > bestScore) {
                    bestScore = v
                    best = c
                }
            }
            if (best != 0 && best != prev) {
                val idx = best - 1
                dict.getOrNull(idx)?.let { chars.append(it) }
                sum += bestScore
                count++
            }
            prev = best
        }

        val confidence = if (count == 0) 0f else sum / count
        return Decoded(chars.toString(), confidence)
    }
}
