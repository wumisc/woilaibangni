package io.github.wumisc.woilaibangni

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.wumisc.woilaibangni.core.asr.AsrSession
import io.github.wumisc.woilaibangni.core.asr.SherpaOfflineAsrEngine
import io.github.wumisc.woilaibangni.core.asr.SherpaStreamingAsrEngine
import io.github.wumisc.woilaibangni.core.asr.TwoPassAsrEngine
import io.github.wumisc.woilaibangni.core.log.LogHub
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * 悬浮字幕：把识别结果画在**别的 App 上面**（视频、微信、会议都行）。
 *
 * 为什么必须是前台服务：从 Android 9 起后台麦克风基本拿不到；Android 14 还要求
 * `foregroundServiceType="microphone"`。悬浮窗本身只需要 SYSTEM_ALERT_WINDOW。
 *
 * 权限链路：设置里给「显示在其他应用上层」→ 点开始 → 系统弹麦克风授权。
 */
class CaptionOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var captionView: TextView? = null
    private var session: AsrSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        running = true
        startAsForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlay()
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        scope.cancel()
        session?.release()
        session = null
        captionView?.let { view -> runCatching { windowManager.removeView(view) } }
        captionView = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮字幕",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setSound(null, null) }
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("我来帮你 · 悬浮字幕")
            .setContentText("正在听，字幕显示在其他应用上层")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setSilent(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
    }

    private fun addOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            LogHub.app(TAG, "没有悬浮窗权限，无法显示字幕")
            stopSelf()
            return
        }
        val view = TextView(this).apply {
            setTextColor(Color.parseColor("#FFD75E"))
            textSize = 26f
            setPadding(36, 28, 36, 28)
            setBackgroundColor(Color.parseColor("#CC000000"))
            text = "正在启动字幕……"
            gravity = Gravity.CENTER
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // 不抢焦点、不吃触摸，避免挡住下方 App 的操作
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = 220
        }
        runCatching {
            windowManager.addView(view, lp)
            captionView = view
        }.onFailure {
            LogHub.error(LogHub.Channel.APP, TAG, "悬浮窗添加失败", it)
            stopSelf()
        }
    }

    private fun startListening() {
        val asr = AsrSession(
            engine = TwoPassAsrEngine(SherpaStreamingAsrEngine(), SherpaOfflineAsrEngine()),
        )
        session = asr

        scope.launch { collect(asr) }
        scope.launch {
            val models = File(filesDir, "models")
            val ok = runCatching {
                asr.prepare(
                    File(models, "asr-stream").absolutePath,
                    File(models, "asr-accurate").absolutePath,
                )
            }.getOrDefault(false)
            if (!ok) {
                captionView?.text = "没有找到对话模型，请先装 full 包或放入模型"
                LogHub.asr(TAG, "悬浮字幕：模型未就绪")
            } else if (asr.start()) {
                captionView?.text = "在听……"
                LogHub.asr(TAG, "悬浮字幕：开始录音")
            } else {
                captionView?.text = "打不开麦克风，请检查录音权限"
            }
        }
    }

    private suspend fun collect(asr: AsrSession) {
        asr.state.collectLatest { state ->
            val view = captionView ?: return@collectLatest
            val text = when {
                state.error != null -> state.error!!
                state.partial.isNotBlank() -> state.partial
                state.committed.isNotBlank() -> state.committed
                state.listening -> "在听……"
                else -> "等待声音"
            }
            view.text = text
        }
    }

    companion object {
        const val TAG = "WOILAI_OVERLAY"
        private const val CHANNEL_ID = "caption_overlay"
        private const val NOTIFICATION_ID = 2001

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CaptionOverlayService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CaptionOverlayService::class.java))
        }
    }
}
