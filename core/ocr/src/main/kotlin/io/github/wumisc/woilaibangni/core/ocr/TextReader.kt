package io.github.wumisc.woilaibangni.core.ocr

import android.graphics.Bitmap

/** 一行文字及其位置；位置用于"按阅读顺序朗读"和"只读某个区域"。 */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

interface TextReader {
    /** 是否已加载模型。 */
    val isReady: Boolean

    suspend fun prepare(modelDir: String): Boolean

    /** 识别一张图；调用方负责回收 bitmap。 */
    suspend fun read(bitmap: Bitmap): List<OcrLine>

    fun release()
}

/**
 * 行 → 段落的重排。盲人听读时，"顺序错了"比"错字"更致命。
 * 规则：先按 y 中心聚类成行，再按 x 排序；行距小于 1.5 倍字高视为同一段。
 */
object LineGrouper {

    fun toParagraphs(lines: List<OcrLine>, lineGapFactor: Float = 1.5f): List<String> {
        if (lines.isEmpty()) return emptyList()
        val sorted = lines.sortedWith(compareBy({ it.centerY }, { it.left }))
        val paragraphs = mutableListOf<MutableList<OcrLine>>()
        var current = mutableListOf(sorted.first())
        var lastY = sorted.first().centerY
        var lastH = sorted.first().height.coerceAtLeast(1)

        for (line in sorted.drop(1)) {
            val rowTolerance = (lastH * 0.6f).toInt().coerceAtLeast(1)
            val sameRow = kotlin.math.abs(line.centerY - lastY) <= rowTolerance
            val paragraphBreak = line.centerY - lastY > (lastH * lineGapFactor)

            if (!sameRow && paragraphBreak) {
                paragraphs += current
                current = mutableListOf(line)
            } else {
                current += line
            }
            lastY = line.centerY
            lastH = line.height.coerceAtLeast(1)
        }
        paragraphs += current
        return paragraphs.map { group ->
            group.sortedBy { it.left }.joinToString(" ") { it.text.trim() }
        }.filter { it.isNotBlank() }
    }
}

/**
 * PP-OCRv5 mobile（det + rec，int8）实现占位。
 *
 * 接入方式（M0 待办）：
 * 1. 依赖 `com.microsoft.onnxruntime:onnxruntime-android`；
 * 2. det 模型输出文本框，做 DB 后处理得到四点框；
 * 3. rec 模型按框裁剪、归一化后识别，charset 从 zip 内 dict.txt 读取；
 * 4. 全部在 IO 线程，单页 1080p 目标 ≤ 800ms。
 */
class PpOcrTextReader : TextReader {
    override val isReady: Boolean = false

    override suspend fun prepare(modelDir: String): Boolean = false

    override suspend fun read(bitmap: Bitmap): List<OcrLine> =
        error("PP-OCRv5 尚未接入，M0 完成后启用")

    override fun release() = Unit
}

/** 链路自检用：返回固定文案，验证"识别→分段→朗读"通路。 */
class DemoTextReader : TextReader {
    override val isReady: Boolean = true
    override suspend fun prepare(modelDir: String): Boolean = true
    override suspend fun read(bitmap: Bitmap): List<OcrLine> = listOf(
        OcrLine("阿莫西林胶囊", 40, 40, 320, 80),
        OcrLine("0.25g  口服  一日三次", 40, 100, 400, 140),
        OcrLine("饭后服用，连服七天", 40, 160, 360, 200),
    )
    override fun release() = Unit
}
