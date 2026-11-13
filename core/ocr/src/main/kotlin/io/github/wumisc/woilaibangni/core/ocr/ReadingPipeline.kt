package io.github.wumisc.woilaibangni.core.ocr

import android.graphics.Bitmap
import android.os.SystemClock
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
        val lines = reader.read(bitmap)
        val paragraphs = LineGrouper.toParagraphs(lines)
        ReadingResult(lines = lines, paragraphs = paragraphs, elapsedMs = SystemClock.elapsedRealtime() - started)
    }

    fun release() = reader.release()
}
