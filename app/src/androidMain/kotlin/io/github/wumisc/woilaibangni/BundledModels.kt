package io.github.wumisc.woilaibangni

import android.content.Context
import android.content.res.AssetManager
import io.github.wumisc.woilaibangni.core.log.LogHub
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * full 包里模型是内置在 assets 里的，但 sherpa-onnx 与 OCR 都只吃文件路径，
 * 所以首次启动要把 assets/models 原样搬到 filesDir/models。
 *
 * slim 包里 assets 没有 models 目录，[install] 会直接返回 false，不产生任何拷贝。
 */
object BundledModels {

    private const val ASSET_ROOT = "models"
    private const val STAMP = ".bundled"

    /** 是否有内置模型（full flavor 为 true）。 */
    fun hasBundled(context: Context): Boolean = runCatching {
        context.assets.list(ASSET_ROOT)?.isNotEmpty() == true
    }.getOrDefault(false)

    /**
     * 把内置模型复制到 [targetRoot]（默认 filesDir/models）。
     *
     * @param onProgress 0~100，用于首启进度提示
     * @return true 表示模型已就位（可能是本次复制完成的，也可能是之前复制过）
     */
    suspend fun install(
        context: Context,
        targetRoot: File = File(context.filesDir, ASSET_ROOT),
        force: Boolean = false,
        onProgress: (Int) -> Unit = {},
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (!hasBundled(context)) return@runCatching false
            val stamp = File(targetRoot, STAMP)
            if (stamp.exists() && !force) return@runCatching true
            if (force) stamp.delete()

            val entries = mutableListOf<Pair<String, Long>>()
            collect(context.assets, ASSET_ROOT, entries)
            val total = entries.sumOf { it.second }.coerceAtLeast(1L)
            var done = 0L
            var lastPct = -1

            entries.forEach { (assetPath, size) ->
                val relative = assetPath.removePrefix("$ASSET_ROOT/")
                val dest = File(targetRoot, relative)
                dest.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    dest.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                }
                done += size
                val pct = ((done * 100) / total).toInt()
                if (pct != lastPct) {
                    lastPct = pct
                    onProgress(pct)
                }
            }
            // 校验：别让半包当成成功
            val missing = REQUIRED.filterNot { File(targetRoot, it).let { f -> f.isFile && f.length() > 0 } }
            if (missing.isNotEmpty()) {
                stamp.delete()
                LogHub.app("WOILAI_LOG", "内置模型解包不完整，缺：${missing.joinToString()}")
                LogHub.asr("WOILAI_ASR", "内置模型解包不完整，缺：${missing.joinToString()}")
                return@runCatching false
            }
            stamp.writeText(System.currentTimeMillis().toString())
            LogHub.app("WOILAI_LOG", "内置模型已解包：${targetRoot.absolutePath}")
            LogHub.asr("WOILAI_ASR", "内置模型已解包：${targetRoot.absolutePath}")
            true
        }.getOrElse { e ->
            LogHub.error(LogHub.Channel.APP, "WOILAI_LOG", "内置模型解包失败", e)
            false
        }
    }

    /** 模型文件现状，直接显示给用户，不用去翻日志。 */
    fun describe(targetRoot: File): String {
        val items = REQUIRED.map { rel ->
            val f = File(targetRoot, rel)
            if (f.isFile && f.length() > 0) "$rel ${f.length() / 1024 / 1024}MB" else "$rel 缺失"
        }
        return items.joinToString(" · ")
    }

    val REQUIRED = listOf(
        "asr-stream/encoder.onnx",
        "asr-stream/decoder.onnx",
        "asr-stream/joiner.onnx",
        "asr-stream/tokens.txt",
        "ocr/det.onnx",
        "ocr/rec.onnx",
        "ocr/ppocr_keys_v1.txt",
    )

    private fun collect(assets: AssetManager, path: String, out: MutableList<Pair<String, Long>>) {
        val children = runCatching { assets.list(path) }.getOrNull() ?: return
        children.forEach { child ->
            val childPath = "$path/$child"
            val grandChildren = runCatching { assets.list(childPath) }.getOrNull()
            if (grandChildren.isNullOrEmpty()) {
                val size = runCatching { assets.open(childPath).use { it.available().toLong() } }
                    .getOrDefault(0L)
                out += childPath to size
            } else {
                collect(assets, childPath, out)
            }
        }
    }
}
