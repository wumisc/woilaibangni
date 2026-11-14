package io.github.wumisc.woilaibangni

import android.content.Context
import io.github.wumisc.woilaibangni.core.asr.AsrSession
import io.github.wumisc.woilaibangni.core.asr.SherpaOfflineAsrEngine
import io.github.wumisc.woilaibangni.core.asr.SherpaStreamingAsrEngine
import io.github.wumisc.woilaibangni.core.asr.TwoPassAsrEngine
import java.io.File

/**
 * 全 App 唯一的 ASR 会话。
 *
 * 为什么必须共用一个：Android 只允许一处 AudioRecord 真正拿到声音，
 * 开第二路就是静音（m11 悬浮字幕不出字就是这么来的）。
 * 所以主界面和悬浮字幕共享同一个 [AsrSession]：一路录音、两处显示。
 */
object AsrRuntime {

    private var session: AsrSession? = null

    fun session(context: Context): AsrSession = session ?: AsrSession(
        engine = TwoPassAsrEngine(SherpaStreamingAsrEngine(), SherpaOfflineAsrEngine()),
        label = "app",
    ).also { session = it }

    /** 模型目录，主界面和悬浮字幕用同一套。 */
    fun streamDir(context: Context): String = File(context.filesDir, "models/asr-stream").absolutePath

    fun accurateDir(context: Context): String = File(context.filesDir, "models/asr-accurate").absolutePath
}
