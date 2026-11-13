package io.github.wumisc.woilaibangni.core.aac

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 板文件导入：`.obf`（单个 JSON）与 `.obz`（zip，含 board.obf 与 images/）。
 *
 * 不用猜后缀，先看文件头：`50 4B` 就是 zip。用户从 CoughDrop / OpenAAC 导出的文件
 * 经常名字乱，靠魔数判断更稳。
 */
object BoardImporter {

    private const val ZIP_MAGIC_1 = 0x50
    private const val ZIP_MAGIC_2 = 0x4B

    suspend fun import(context: Context, uri: Uri): AacDeck {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取该文件")
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "imported"
        return import(bytes, File(context.filesDir, "aac/$name"))
    }

    fun import(bytes: ByteArray, workDir: File): AacDeck {
        val isZip = bytes.size > 2 && bytes[0].toInt() == ZIP_MAGIC_1 && bytes[1].toInt() == ZIP_MAGIC_2
        return if (isZip) importZip(bytes, workDir) else importObf(bytes, workDir)
    }

    private fun importObf(bytes: ByteArray, workDir: File): AacDeck {
        val json = bytes.toString(Charsets.UTF_8)
        return ObfParser.parse(json) { path -> resolveLocal(workDir, path) }
    }

    private fun importZip(bytes: ByteArray, workDir: File): AacDeck {
        workDir.deleteRecursively()
        workDir.mkdirs()
        var rootObf: String? = null
        var fallbackObf: ByteArray? = null

        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.substringAfterLast('/').takeIf { it.isNotBlank() }
                if (!entry.isDirectory && entryName != null) {
                    // 防 zip slip：只取文件名，忽略压缩包内的相对路径穿越
                    val target = File(workDir, entryName)
                    target.outputStream().use { out -> zip.copyTo(out) }
                    if (entryName.endsWith(".obf")) {
                        val text = target.readText(Charsets.UTF_8)
                        if (entryName.equals("board.obf", true) || rootObf == null) rootObf = text
                        if (fallbackObf == null) fallbackObf = target.readBytes()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val json = rootObf ?: error("压缩包里没有 .obf 文件")
        return ObfParser.parse(json) { path -> resolveLocal(workDir, path) }
    }

    /** OBZ 里的 `path` 是包内相对路径；解包后只剩文件名，按文件名找回。 */
    private fun resolveLocal(workDir: File, path: String): String? {
        val fileName = path.substringAfterLast('/')
        val file = File(workDir, fileName)
        return if (file.isFile) file.absolutePath else null
    }
}
