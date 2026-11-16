package io.github.wumisc.woilaibangni

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import androidx.core.content.ContextCompat
import io.github.wumisc.woilaibangni.core.aac.AacDeck
import io.github.wumisc.woilaibangni.core.aac.BoardDefaults
import io.github.wumisc.woilaibangni.core.asr.AsrSession
import io.github.wumisc.woilaibangni.core.log.LogHub
import io.github.wumisc.woilaibangni.core.ocr.PpOcrTextReader
import io.github.wumisc.woilaibangni.core.ocr.ReadingPipeline
import io.github.wumisc.woilaibangni.core.tts.AndroidTextSpeaker
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class Mode(
    val title: String,
    val tabLabel: String,
    val headline: String,
    val lines: List<String>,
    val talkbackHint: String,
)

private val modes = listOf(
    Mode(
        title = "面对面",
        tabLabel = "对话",
        headline = "把手机放在两人中间",
        lines = listOf(
            "上半屏倒转给对方看，下半屏留给自己。",
            "对方说话 → 实时字幕；你打字或点短句 → 手机替你发声。",
        ),
        talkbackHint = "当前为面对面模式，屏幕上下分栏",
    ),
    Mode(
        title = "只听",
        tabLabel = "只听",
        headline = "课堂、会议、柜台",
        lines = listOf(
            "全屏大字幕，2~4 米拾音。",
            "对方说到你的名字或预设词时，手机会震动提醒。",
        ),
        talkbackHint = "当前为只听模式，全屏字幕",
    ),
    Mode(
        title = "短句板",
        tabLabel = "短句",
        headline = "说不出话时，点一下就发声",
        lines = listOf(
            "预置分类：疼痛 / 就医 / 出行 / 购物 / 求助 / 情绪。",
            "按钮不小于 64dp，家属和言语治疗师可自行增删。",
        ),
        talkbackHint = "当前为短句板模式",
    ),
    Mode(
        title = "看世界",
        tabLabel = "看世界",
        headline = "拍一下，念给你听",
        lines = listOf(
            "药盒、说明书、快递单、菜单、门牌、电梯按钮。",
            "离线 OCR，不出网；拍歪了会提示你换个角度。",
        ),
        talkbackHint = "当前为看世界模式，拍照朗读",
    ),
    Mode(
        title = "语音输入",
        tabLabel = "语音",
        headline = "说一句，先念给你确认，再发出去",
        lines = listOf(
            "给打字困难的视障用户准备。",
            "只往输入框填字，不点按、不提交，绝不替你操作。",
        ),
        talkbackHint = "当前为语音输入模式",
    ),
)

/** 明暗两套颜色，界面所有颜色都从这里取，不再写死。 */
internal data class Palette(
    val background: Color,
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val hint: Color,
    val accent: Color,
    val accentSoft: Color,
    val captionBg: Color,
    val captionText: Color,
    val captionSub: Color,
    val danger: Color,
)

private val LightPalette = Palette(
    background = Color(0xFFF7F8FA),
    card = Color.White,
    textPrimary = Color(0xFF111318),
    textSecondary = Color(0xFF3D4353),
    hint = Color(0xFF8A8F9C),
    accent = Color(0xFF0A6CFF),
    accentSoft = Color(0xFFEAF1FF),
    captionBg = Color(0xFF0E1116),
    captionText = Color(0xFFFFD75E),
    captionSub = Color(0xFF9AA3B2),
    danger = Color(0xFFE5484D),
)

private val DarkPalette = Palette(
    background = Color(0xFF0B0C0F),
    card = Color(0xFF16181D),
    textPrimary = Color(0xFFF2F3F5),
    textSecondary = Color(0xFFC2C8D2),
    hint = Color(0xFF8B93A1),
    accent = Color(0xFF4C8DFF),
    accentSoft = Color(0xFF1B2740),
    captionBg = Color(0xFF05060A),
    captionText = Color(0xFFFFD75E),
    captionSub = Color(0xFF9AA3B2),
    danger = Color(0xFFFF6B6E),
)

@Composable
fun App() {
    MiuixTheme {
        val context = LocalContext.current
        remember { runCatching { LogHub.init(context) }; true }
        val scope = rememberCoroutineScope()

        val palette = if (isSystemInDarkTheme()) DarkPalette else LightPalette

        val asr = remember { AsrRuntime.session(context) }
        val speaker = remember { AndroidTextSpeaker(context) }
        val ocrReader = remember { PpOcrTextReader() }
        val reading = remember { ReadingPipeline(ocrReader) }
        val deck = remember { BoardDefaults.deck() }

        var engineReady by remember { mutableStateOf(false) }
        var setupHint by remember { mutableStateOf<String?>(null) }
        var notReadyReason by remember { mutableStateOf<String?>(null) }
        var readText by remember { mutableStateOf("") }
        var readHint by remember { mutableStateOf<String?>(null) }
        var logDir by remember { mutableStateOf("") }
        var modelStatus by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            logDir = LogHub.rootPath
            // full 包：首次启动把内置模型解包到私有目录（约 210MB，一次性）
            val modelsDir = File(context.filesDir, "models")
            val bundled = BundledModels.hasBundled(context)
            if (bundled) {
                setupHint = "正在安装内置模型 0%"
                var ok = BundledModels.install(context, modelsDir) { pct -> setupHint = "正在安装内置模型 $pct%" }
                if (!ok) {
                    // 上次可能被中途杀掉留下半包，强制重来一次
                    ok = BundledModels.install(context, modelsDir, force = true) { pct ->
                        setupHint = "正在重装模型 $pct%"
                    }
                }
                setupHint = null
                if (!ok) setupHint = "内置模型解包失败，详见 app.log"
            }
            modelStatus = if (bundled) BundledModels.describe(modelsDir) else "slim 包：未内置模型"
            runCatching {
                ocrReader.prepare(File(context.filesDir, "models/ocr").absolutePath)
            }
            runCatching {
                val models = File(context.filesDir, "models")
                engineReady = asr.prepare(
                    File(models, "asr-stream").absolutePath,
                    File(models, "asr-accurate").absolutePath,
                )
                if (!engineReady) {
                    val dir = File(models, "asr-stream")
                    val bundled = BundledModels.hasBundled(context)
                    notReadyReason = if (!bundled) {
                            "这是轻量包（slim），没有内置模型。请安装 full 包（227MB），" +
                                "或把 encoder/decoder/joiner/tokens 放进 $dir"
                        } else {
                            "模型文件不完整：$dir（解包可能失败了，看 app.log）"
                        }
                    LogHub.asr("WOILAI_ASR", "not ready: $notReadyReason")
                }
            }.onFailure {
                notReadyReason = it.message ?: "对话引擎初始化失败"
                LogHub.error(LogHub.Channel.ASR, "WOILAI_ASR", "启动准备失败", it)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                // 会话是全进程共用的，Activity 退到后台不释放，免得悬浮字幕断掉
                speaker.shutdown()
                reading.release()
            }
        }

        val asrState by asr.state.collectAsState()
        var selected by remember { mutableIntStateOf(0) }
        var overlayRunning by remember { mutableStateOf(CaptionOverlayService.running) }

        fun given(permission: String) = ContextCompat.checkSelfPermission(
            context, permission
        ) == PackageManager.PERMISSION_GRANTED

        val micPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                scope.launch {
                    // 先确保模型就绪再开麦，否则录到的音频没人接
                    if (!engineReady) {
                        val models = File(context.filesDir, "models")
                        engineReady = asr.prepare(
                            File(models, "asr-stream").absolutePath,
                            File(models, "asr-accurate").absolutePath,
                        )
                    }
                    if (!asr.start()) notReadyReason = asrState.error ?: "无法打开麦克风"
                }
            }
            else notReadyReason = "没有录音权限，无法开始对话"
        }

        val shotFile = remember { File(context.cacheDir, "shots/ocr-shot.jpg").apply { parentFile?.mkdirs() } }
        val camera = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { ok ->
            val bitmap = if (!ok) null else loadPhoto(shotFile)
            if (bitmap == null) {
                readHint = if (ok) "照片读不出来，可能存储空间不足" else "没有拿到照片，可能取消了拍摄"
                LogHub.ocr("WOILAI_OCR", "相机返回空 bitmap ok=$ok 文件=${shotFile.length() / 1024}KB")
            } else {
                LogHub.ocr("WOILAI_OCR", "相机返回 ${bitmap.width}x${bitmap.height}")
                scope.launch {
                    runCatching {
                        val result = reading.read(bitmap)
                        readText = result.paragraphs.joinToString("\n")
                        readHint = if (result.paragraphs.isEmpty()) {
                            "这张图里没识别到文字，靠近一点、光线亮一点再试"
                        } else null
                        if (result.paragraphs.isNotEmpty()) {
                            speaker.speak(result.paragraphs.first())
                        }
                    }.onFailure {
                        readHint = "识别失败：${it.message ?: "未知原因"}"
                        LogHub.error(LogHub.Channel.OCR, "WOILAI_OCR", "拍照识别失败", it)
                    }
                }
            }
        }

        val notificationPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        val overlayPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (Settings.canDrawOverlays(context)) {
                CaptionOverlayService.start(context)
                overlayRunning = true
            }
        }

        val cameraPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            // 声明了 CAMERA 权限就必须先拿到授权，否则启动系统相机直接抛 SecurityException
            if (granted) {
                runCatching { camera.launch(shotUri(context, shotFile)) }
                    .onFailure {
                        readHint = "无法打开相机：${it.message ?: "未知原因"}"
                        LogHub.error(LogHub.Channel.APP, "WOILAI_CAMERA", "启动相机失败", it)
                    }
            } else {
                readHint = "没有相机权限，无法拍照"
            }
        }

        Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
            Content(
                mode = modes[selected],
                deck = deck,
                palette = palette,
                engineReady = engineReady,
                notReadyReason = notReadyReason,
                setupHint = setupHint,
                asrState = asrState,
                readText = readText,
                readHint = readHint,
                modelStatus = modelStatus,
                logDir = logDir,
                onRefreshLog = {
                    LogHub.init(context)
                    logDir = LogHub.rootPath
                },
                onToggleMic = {
                    if (asrState.listening) {
                        asr.stop()
                    } else if (!given(Manifest.permission.RECORD_AUDIO)) {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        scope.launch {
                            if (!engineReady) {
                                val models = File(context.filesDir, "models")
                                engineReady = asr.prepare(
                                    File(models, "asr-stream").absolutePath,
                                    File(models, "asr-accurate").absolutePath,
                                )
                            }
                            if (!asr.start()) notReadyReason = asrState.error ?: "无法打开麦克风"
                        }
                    }
                },
                onCapture = {
                    if (!given(Manifest.permission.CAMERA)) {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    } else {
                        runCatching { camera.launch(shotUri(context, shotFile)) }
                            .onFailure {
                                readHint = "无法打开相机：${it.message ?: "未知原因"}"
                                LogHub.error(LogHub.Channel.APP, "WOILAI_CAMERA", "启动相机失败", it)
                            }
                    }
                },
                onSpeak = { speaker.speak(it) },
                overlayRunning = overlayRunning,
                onToggleOverlay = {
                    if (overlayRunning) {
                        CaptionOverlayService.stop(context)
                        overlayRunning = false
                    } else if (!Settings.canDrawOverlays(context)) {
                        runCatching {
                            overlayPermission.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }
                    } else {
                        // 前台服务的常驻通知在 Android 13+ 需要通知权限，否则用户看不到"正在听"
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !given(Manifest.permission.POST_NOTIFICATIONS)
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        // 共用同一个 AsrSession，不再另起录音，避免抢麦
                        CaptionOverlayService.start(context)
                        overlayRunning = true
                    }
                },
                modifier = Modifier.weight(1f),
            )
            TabBar(selected = selected, palette = palette, onSelect = { selected = it })
        }
    }
}

@Composable
private fun Content(
    mode: Mode,
    deck: AacDeck,
    palette: Palette,
    engineReady: Boolean,
    notReadyReason: String?,
    setupHint: String?,
    asrState: AsrSession.UiState,
    readText: String,
    readHint: String?,
    modelStatus: String,
    logDir: String,
    onRefreshLog: () -> Unit,
    onToggleMic: () -> Unit,
    onCapture: () -> Unit,
    onSpeak: (String) -> Unit,
    overlayRunning: Boolean,
    onToggleOverlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp)
            .semantics { contentDescription = mode.talkbackHint },
    ) {
        BasicText(
            text = mode.title,
            style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = palette.textPrimary),
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        BasicText(text = mode.headline, style = TextStyle(fontSize = 18.sp, color = palette.textSecondary))
        Spacer(Modifier.height(20.dp))

        setupHint?.let {
            BasicText(text = it, style = TextStyle(fontSize = 15.sp, color = palette.accent))
            Spacer(Modifier.height(12.dp))
        }
        if (modelStatus.isNotBlank()) {
            BasicText(text = "模型：$modelStatus", style = TextStyle(fontSize = 12.sp, color = palette.hint))
            Spacer(Modifier.height(10.dp))
        }

        when (mode.title) {
            "面对面", "只听" -> LiveCaptionCard(
                state = asrState,
                ready = engineReady,
                notReadyReason = notReadyReason,
                palette = palette,
                overlayRunning = overlayRunning,
                onToggleMic = onToggleMic,
                onToggleOverlay = onToggleOverlay,
            )
            "看世界" -> ReadCard(readText, readHint, palette, onCapture, onSpeak)
            "语音输入" -> VoiceInputCard(
                state = asrState,
                ready = engineReady,
                notReadyReason = notReadyReason,
                palette = palette,
                onToggleMic = onToggleMic,
                onSpeak = onSpeak,
            )
            "短句板" -> BoardScreen(deck = deck, palette = palette, onSpeak = onSpeak)
            else -> Unit
        }

        Spacer(Modifier.height(16.dp))
        mode.lines.forEach { line ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(palette.card, RoundedCornerShape(14.dp))
                    .padding(16.dp),
            ) {
                BasicText(text = line, style = TextStyle(fontSize = 17.sp, color = palette.textPrimary))
            }
        }
        Spacer(Modifier.height(10.dp))
        LogFooter(palette, logDir, onRefreshLog)
    }
}

@Composable
private fun LogFooter(palette: Palette, logDir: String, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val label = logDir.ifBlank { LogHub.rootPath }
    BasicText(
            text = "日志目录：${label.ifBlank { "未初始化" }}",
            style = TextStyle(fontSize = 12.sp, color = palette.hint),
            modifier = Modifier.semantics { contentDescription = "日志目录，$label" },
        )
        if (LogHub.degraded) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "（${LogHub.degradedReason}）点这里授予「所有文件访问权限」",
                style = TextStyle(fontSize = 12.sp, color = palette.danger),
                modifier = Modifier.clickable {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }
                    }
                },
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                text = "授权后点这里刷新目录",
                style = TextStyle(fontSize = 12.sp, color = palette.accent),
                modifier = Modifier.clickable { onRefresh() },
            )
        }
}

@Composable
private fun LiveCaptionCard(
    state: AsrSession.UiState,
    ready: Boolean,
    notReadyReason: String?,
    palette: Palette,
    overlayRunning: Boolean,
    onToggleMic: () -> Unit,
    onToggleOverlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.captionBg, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        BasicText(
            text = when {
                state.error != null -> state.error!!
                !ready -> notReadyReason ?: "对话引擎还没就绪"
                state.partial.isNotBlank() -> state.partial
                state.committed.isNotBlank() -> state.committed
                state.listening -> "正在听……"
                else -> "点下面的按钮开始"
            },
            style = TextStyle(fontSize = 22.sp, color = palette.captionText),
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = buildString {
                state.firstPartialLatencyMs?.let { append("首字 ${it}ms  ") }
                state.finalizeLatencyMs?.let { append("定稿 ${it}ms") }
            },
            style = TextStyle(fontSize = 13.sp, color = palette.captionSub),
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    if (state.listening) palette.danger else palette.accent,
                    RoundedCornerShape(14.dp),
                )
                .semantics { contentDescription = if (state.listening) "停止录音" else "开始录音" }
                .clickable(onClick = onToggleMic),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = if (state.listening) "停止" else "开始对话",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
        }
        if (state.listening) {
            Spacer(Modifier.height(10.dp))
            BasicText(
                text = if (state.speaking) "检测到声音，正在识别" else "已开始录音，等待声音",
                style = TextStyle(fontSize = 13.sp, color = palette.captionSub),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                .semantics { contentDescription = if (overlayRunning) "关闭悬浮字幕" else "开启悬浮字幕" }
                .clickable(onClick = onToggleOverlay),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = if (overlayRunning) "关闭悬浮字幕" else "悬浮字幕（显示在其他应用上层）",
                style = TextStyle(fontSize = 15.sp, color = palette.captionText),
            )
        }
        BasicText(
            text = "小米/红米：除了「显示在其他应用上层」，还要在 设置→应用管理→我来帮你→权限 里打开「后台弹出界面」，否则切到别的 App 字幕会消失。字幕条可按住拖动。",
            style = TextStyle(fontSize = 11.sp, color = palette.captionSub),
        )
    }
}

@Composable
private fun ReadCard(
    text: String,
    hint: String?,
    palette: Palette,
    onCapture: () -> Unit,
    onSpeak: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(palette.accent, RoundedCornerShape(16.dp))
                .semantics { contentDescription = "拍照识字并朗读" }
                .clickable(onClick = onCapture),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "拍照 · 念给我听",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
        }
        hint?.let {
            Spacer(Modifier.height(12.dp))
            BasicText(text = it, style = TextStyle(fontSize = 15.sp, color = palette.danger))
        }
        if (text.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            BasicText(text = text, style = TextStyle(fontSize = 20.sp, color = palette.textPrimary))
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(palette.accentSoft, RoundedCornerShape(14.dp))
                    .semantics { contentDescription = "重新朗读识别结果" }
                    .clickable { onSpeak(text) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(text = "再念一遍", style = TextStyle(fontSize = 17.sp, color = palette.accent))
            }
        }
    }
}

/**
 * 语音输入：说一句 → 上屏 → 你可以复制、也可以直接念一遍。
 * 目前不自动往别的 App 输入框填字（那要走 AccessibilityService，需要用户单独授权）。
 */
@Composable
private fun VoiceInputCard(
    state: AsrSession.UiState,
    ready: Boolean,
    notReadyReason: String?,
    palette: Palette,
    onToggleMic: () -> Unit,
    onSpeak: (String) -> Unit,
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val text = state.committed.ifBlank { state.partial }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.card, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        BasicText(
            text = when {
                state.error != null -> state.error!!
                !ready -> notReadyReason ?: "对话引擎还没就绪"
                text.isNotBlank() -> text
                state.listening -> "正在听……"
                else -> "点下面的按钮，说完会显示在这里"
            },
            style = TextStyle(fontSize = 20.sp, color = palette.textPrimary),
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(if (state.listening) palette.danger else palette.accent, RoundedCornerShape(14.dp))
                .semantics { contentDescription = if (state.listening) "停止录音" else "开始录音" }
                .clickable(onClick = onToggleMic),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = if (state.listening) "停止" else "开始说话",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
        }
        if (text.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .background(palette.accentSoft, RoundedCornerShape(14.dp))
                        .semantics { contentDescription = "复制识别结果" }
                        .clickable {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("我来帮你", text))
                            copied = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = if (copied) "已复制" else "复制",
                        style = TextStyle(fontSize = 16.sp, color = palette.accent),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .background(palette.accentSoft, RoundedCornerShape(14.dp))
                        .semantics { contentDescription = "朗读识别结果" }
                        .clickable { onSpeak(text) },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(text = "念一遍", style = TextStyle(fontSize = 16.sp, color = palette.accent))
                }
            }
        }
    }
}

@Composable
private fun TabBar(selected: Int, palette: Palette, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.card)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        modes.forEachIndexed { index, mode ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics {
                        role = Role.Tab
                        this.selected = isSelected
                        contentDescription = if (isSelected) "${mode.tabLabel}，已选中" else mode.tabLabel
                    }
                    .clickable { onSelect(index) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = mode.tabLabel,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) palette.accent else palette.hint,
                    ),
                )
            }
        }
    }
}

/** 相机原图可能几千像素，OCR 不需要那么大；长边压到 1600 以内。 */
private fun loadPhoto(file: File): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val source = ImageDecoder.createSource(file)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // ImageDecoder 会按 EXIF 自动转正，这里只限尺寸
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 1600) {
                val scale = 1600f / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}.getOrNull()

private fun shotUri(context: android.content.Context, file: File) =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
