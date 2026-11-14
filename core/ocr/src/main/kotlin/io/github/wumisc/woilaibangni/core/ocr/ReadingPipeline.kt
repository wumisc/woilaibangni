package io.github.wumisc.woilaibangni.core.ocr

import android.graphics.Bitmap
import android.os.SystemClock
import io.github.wumisc.woilaibangni.core.log.LogHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ReadingResult(
    val lines: List<OcrLine>,
    val paragraphs: List<String>,
    val elapsedMs: Long,
)

/**
 * 拍照朗读流水线：识别 → 按阅读顺序重排 → 交段落。
 * 朗读粒度由调用方决定（逐行 / 逐段 / 整页），这里只负责"顺序一定对"。
 */
class ReadingPipeline(private val reader: TextReader) {

    val isReady: Boolean get() = reader.isReady

    suspend fun prepare(modelDir: String): Boolean = reader.prepare(modelDir)

    suspend fun read(bitmap: Bitmap): ReadingResult = withContext(Dispatchers.Default) {
        val started = SystemClock.elapsedRealtime()
        LogHub.ocr("WOILAI_OCR", "read 开始 ${bitmap.width}x${bitmap.height} ready=$isReady")
        val lines = reader.read(bitmap)
        val paragraphs = LineGrouper.toParagraphs(lines)
        val cost = SystemClock.elapsedRealtime() - started
        LogHub.ocr(
            "WOILAI_OCR",
            "read 完成 lines=${lines.size} paragraphs=${paragraphs.size} cost=${cost}ms text=${paragraphs.joinToString(" | ").take(200)}",
        )
        ReadingResult(lines = lines, paragraphs = paragraphs, elapsedMs = cost)
    }

    fun release() = reader.release()
}
