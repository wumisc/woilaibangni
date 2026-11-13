package io.github.wumisc.woilaibangni.core.log

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 落盘日志。
 *
 * 目标目录：`/storage/emulated/0/Android/helpup/`
 *
 * 现实约束（Android 11+ 分区存储）：`/storage/emulated/0/Android/` 根目录下的自建目录
 * 默认不可写，只有拿到「所有文件访问权限」（MANAGE_EXTERNAL_STORAGE）才行。
 * 所以这里按优先级降级：
 * 1. `/storage/emulated/0/Android/helpup`（有权限时）
 * 2. `/storage/emulated/0/Android/data/<包名>/files/logs`（adb 可直接 pull，无需 root）
 * 3. `/data/data/<包名>/files/logs`（最兜底）
 *
 * 日志同时镜像到 logcat，原来 `adb logcat -s WOILAI_PERF` 的用法不受影响。
 */
object LogHub {

    enum class Channel(val fileName: String) {
        APP("app.log"),
        PERF("perf.log"),
        ASR("asr.log"),
        OCR("ocr.log"),
    }

    /** 实际落盘目录；UI 上要显示给用户，方便他直接去拉文件。 */
    @Volatile
    var rootPath: String = ""
        private set

    /** true 表示没拿到目标目录，落到了备用目录。 */
    @Volatile
    var degraded: Boolean = false
        private set

    /** 降级原因，给人看的。 */
    @Volatile
    var degradedReason: String = ""
        private set

    private const val PRIMARY_DIR = "/storage/emulated/0/Android/helpup"
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024
    private const val MAX_ROTATED = 2

    private val lock = Any()
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var dir: File? = null

    fun init(context: Context) {
        synchronized(lock) {
            // 已经落在目标目录且没降级，不用重复初始化；
            // 降级状态每次调用都重试，用户刚授完权就能升级到目标目录
            if (dir != null && !degraded) return
            val candidates = listOf(
                File(PRIMARY_DIR),
                File(context.getExternalFilesDir(null) ?: context.filesDir, "logs"),
                File(context.filesDir, "logs"),
            )
            val chosen = candidates.firstOrNull { canWrite(it) }
            if (chosen == null) {
                degraded = true
                degradedReason = "所有候选目录都不可写"
                return
            }
            dir = chosen
            rootPath = chosen.absolutePath
            degraded = chosen.absolutePath != PRIMARY_DIR
            if (degraded) degradedReason = "目标目录不可写，已落到 $rootPath"

            installCrashHandler()
            for (channel in Channel.entries) {
                write(channel, "WOILAI_LOG", "日志开始 dir=$rootPath sdk=${Build.VERSION.SDK_INT}")
            }
        }
    }

    fun app(tag: String, message: String) = write(Channel.APP, tag, message)
    fun perf(tag: String, message: String) = write(Channel.PERF, tag, message)
    fun asr(tag: String, message: String) = write(Channel.ASR, tag, message)
    fun ocr(tag: String, message: String) = write(Channel.OCR, tag, message)

    fun error(channel: Channel, tag: String, message: String, throwable: Throwable) {
        val stack = StringWriter().also { sw -> PrintWriter(sw).use { throwable.printStackTrace(it) } }.toString()
        write(channel, tag, "$message\n$stack")
        Log.e(tag, message, throwable)
    }

    private fun write(channel: Channel, tag: String, message: String) {
        // 镜像到 logcat，保持既有调试习惯
        if (channel == Channel.APP) Log.i(tag, message) else Log.i(tag, message)

        synchronized(lock) {
            val target = dir ?: return
            runCatching {
                target.mkdirs()
                val file = File(target, channel.fileName)
                rotateIfNeeded(file)
                val line = "${timeFormat.format(Date())} [${channel.name}] $tag: $message\n"
                file.appendText(line, Charsets.UTF_8)
            }
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.isFile || file.length() < MAX_FILE_BYTES) return
        for (i in MAX_ROTATED downTo 2) {
            val from = File(file.parentFile, "${file.name}.${i - 1}")
            val to = File(file.parentFile, "${file.name}.$i")
            if (from.isFile) from.renameTo(to)
        }
        file.renameTo(File(file.parentFile, "${file.name}.1"))
    }

    private fun canWrite(candidate: File): Boolean = runCatching {
        candidate.mkdirs()
        if (!candidate.isDirectory) return@runCatching false
        val probe = File(candidate, ".probe")
        probe.writeText("ok")
        probe.delete()
        true
    }.getOrDefault(false)

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val header = buildString {
                    append("崩溃 thread=${thread.name}")
                    append(" sdk=${Build.VERSION.SDK_INT}")
                    append(" device=${Build.MANUFACTURER}/${Build.MODEL}")
                    append(" abi=${Build.SUPPORTED_ABIS.joinToString()}")
                }
                val stack = StringWriter().also { sw -> PrintWriter(sw).use { throwable.printStackTrace(it) } }.toString()
                write(Channel.APP, "WOILAI_CRASH", "$header\n$stack")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
