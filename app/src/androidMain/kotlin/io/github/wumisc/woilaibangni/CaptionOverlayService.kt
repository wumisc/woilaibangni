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
import android.view.MotionEvent
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
    private var lp: WindowManager.LayoutParams? = null
    private var session: AsrSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        running = true
        LogHub.app(TAG, "悬浮字幕服务启动")
        startAsForeground()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlay()
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        LogHub.app(TAG, "悬浮字幕服务停止")
        scope.cancel()
        // 不释放共用会话，主界面还在用
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
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels - dp(24)
        val view = TextView(this).apply {
            setTextColor(Color.parseColor("#FFD75E"))
            textSize = 26f
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setBackgroundColor(Color.parseColor("#CC000000"))
            text = "正在启动字幕……"
            gravity = Gravity.CENTER
            isClickable = true
        }
        val lp = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // 只挡焦点不挡触摸：字幕条可以拖着走，不会吃掉整屏
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = (metrics.heightPixels * 0.72f).toInt()
        }

        // 拖动：按住字幕条上下左右挪，位置限制在屏幕内
        var startX = 0
        var startY = 0
        var downRawX = 0f
        var downRawY = 0f
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxY = metrics.heightPixels - view.height
                    lp.x = (startX + (event.rawX - downRawX).toInt()).coerceIn(-width / 3, metrics.widthPixels - width / 3)
                    lp.y = (startY + (event.rawY - downRawY).toInt()).coerceIn(0, maxY.coerceAtLeast(0))
                    runCatching { windowManager.updateViewLayout(view, lp) }
                    true
                }
                else -> false
            }
        }

        runCatching {
            windowManager.addView(view, lp)
            captionView = view
            this.lp = lp
            LogHub.app(TAG, "悬浮条已添加 ${width}px，可拖动")
        }.onFailure {
            LogHub.error(LogHub.Channel.APP, TAG, "悬浮窗添加失败", it)
            stopSelf()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun startListening() {
        val asr = AsrRuntime.session(this)
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
        kotlinx.coroutines.coroutineScope {
            launch {
                CaptionBus.main.collectLatest { text ->
                    if (text.isNotBlank()) captionView?.text = text
                }
            }
            launch {
                collectState(asr)
            }
        }
    }

    private suspend fun collectState(asr: AsrSession) {
        asr.state.collectLatest { state ->
            val view = captionView ?: return@collectLatest
            if (CaptionBus.main.value.isNotBlank()) return@collectLatest
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
            if (running) {
                LogHub.app(TAG, "悬浮字幕已在运行，忽略重复启动")
                return
            }
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
