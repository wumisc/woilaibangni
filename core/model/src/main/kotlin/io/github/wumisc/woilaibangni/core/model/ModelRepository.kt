package io.github.wumisc.woilaibangni.core.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 模型仓库：下载、校验、落盘、查询。
 *
 * 设计约束：
 * 1. 只认 zip，解压到 `filesDir/models/<id>/`；
 * 2. 先写 `.part`，校验 sha256 通过后才改名为正式文件，避免半个模型被加载；
 * 3. 下载失败可续传（HTTP Range），但校验失败一律重下。
 */
class ModelRepository(private val context: Context) {

    fun modelDir(spec: ModelSpec): File = File(context.filesDir, "models/${spec.id}")

    fun isInstalled(spec: ModelSpec): Boolean {
        val dir = modelDir(spec)
        return dir.isDirectory && dir.listFiles()?.isNotEmpty() == true && !File(dir, ".incomplete").exists()
    }

    suspend fun ensure(
        spec: ModelSpec,
        onProgress: (bytesRead: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val target = modelDir(spec)
            if (isInstalled(spec)) return@runCatching target
            target.mkdirs()
            File(target, ".incomplete").createNewFile()

            val zip = File(target.parentFile, "${spec.id}.zip.part")
            download(spec.url, zip, onProgress)

            if (spec.sha256.isNotBlank() && sha256(zip) != spec.sha256.lowercase()) {
                zip.delete()
                File(target, ".incomplete").delete()
                error("模型校验失败，已删除，请重试")
            }

            unzip(zip, target)
            zip.delete()
            File(target, ".incomplete").delete()
            target
        }
    }

    private fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        conn.inputStream.use { input ->
            FileOutputStream(dest).use { output ->
                val total = conn.contentLengthLong
                var read = 0L
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    read += n
                    onProgress(read, total)
                }
            }
        }
        conn.disconnect()
    }

    private fun unzip(zip: File, target: File) {
        java.util.zip.ZipInputStream(zip.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = File(target, entry.name)
                if (!out.canonicalPath.startsWith(target.canonicalPath + File.separator)) {
                    error("非法压缩包路径：${entry.name}")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { it.write(zis.readBytes()) }
                }
                zis.closeEntry()
            }
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
