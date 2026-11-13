package io.github.wumisc.woilaibangni

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.core.content.ContextCompat
import io.github.wumisc.woilaibangni.core.asr.AsrSession
import io.github.wumisc.woilaibangni.core.ocr.DemoTextReader
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

@Composable
fun App() {
    MiuixTheme {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val asr = remember { AsrSession() }
        val speaker = remember { AndroidTextSpeaker(context) }
        val reading = remember { ReadingPipeline(DemoTextReader()) }
        DisposableEffect(Unit) {
            onDispose {
                asr.release()
                speaker.shutdown()
                reading.release()
            }
        }

        val asrState by asr.state.collectAsState()
        var readText by remember { mutableStateOf("") }
        var selected by remember { mutableIntStateOf(0) }

        val micPermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> if (granted) asr.start() }

        val camera = rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicturePreview()
        ) { bitmap ->
            if (bitmap != null) {
                scope.launch {
                    val result = reading.read(bitmap)
                    readText = result.paragraphs.joinToString("\n")
                    speaker.speak(result.paragraphs.firstOrNull() ?: "")
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF7F8FA))) {
            Content(
                mode = modes[selected],
                asrState = asrState,
                readText = readText,
                onToggleMic = {
                    if (asrState.listening) {
                        asr.stop()
                    } else {
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) asr.start()
                        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onCapture = { camera.launch(null) },
                onSpeak = { speaker.speak(it) },
                modifier = Modifier.weight(1f),
            )
            TabBar(selected = selected, onSelect = { selected = it })
        }
    }
}

@Composable
private fun Content(
    mode: Mode,
    asrState: AsrSession.UiState,
    readText: String,
    onToggleMic: () -> Unit,
    onCapture: () -> Unit,
    onSpeak: (String) -> Unit,
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
            style = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111318)),
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        BasicText(text = mode.headline, style = TextStyle(fontSize = 18.sp, color = Color(0xFF3D4353)))
        Spacer(Modifier.height(20.dp))

        when (mode.title) {
            "面对面", "只听" -> LiveCaptionCard(asrState, onToggleMic)
            "看世界" -> ReadCard(readText, onCapture, onSpeak)
            else -> Unit
        }

        Spacer(Modifier.height(16.dp))
        mode.lines.forEach { line ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(16.dp),
            ) {
                BasicText(text = line, style = TextStyle(fontSize = 17.sp, color = Color(0xFF1F2430)))
            }
        }
        Spacer(Modifier.height(20.dp))
        BasicText(
            text = if (mode.title == "看世界")
                "当前 OCR 为演示实现：通路已跑通，PP-OCRv5 模型待接入。"
            else
                "当前识别为演示引擎：录音→VAD→上屏的通路已跑通，sherpa-onnx 待接入。",
            style = TextStyle(fontSize = 13.sp, color = Color(0xFF8A8F9C)),
        )
    }
}

@Composable
private fun LiveCaptionCard(state: AsrSession.UiState, onToggleMic: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E1116), RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        BasicText(
            text = when {
                state.error != null -> state.error!!
                state.partial.isNotBlank() -> state.partial
                state.committed.isNotBlank() -> state.committed
                state.listening -> "正在听……"
                else -> "点下面的按钮开始"
            },
            style = TextStyle(fontSize = 22.sp, color = Color(0xFFFFD75E)),
        )
        Spacer(Modifier.height(12.dp))
        BasicText(
            text = buildString {
                state.firstPartialLatencyMs?.let { append("首字 ${it}ms  ") }
                state.finalizeLatencyMs?.let { append("定稿 ${it}ms") }
            },
            style = TextStyle(fontSize = 13.sp, color = Color(0xFF9AA3B2)),
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(
                    if (state.listening) Color(0xFFE5484D) else Color(0xFF0A6CFF),
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
    }
}

@Composable
private fun ReadCard(text: String, onCapture: () -> Unit, onSpeak: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color(0xFF0A6CFF), RoundedCornerShape(16.dp))
                .semantics { contentDescription = "拍照识字并朗读" }
                .clickable(onClick = onCapture),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "拍照 · 念给我听",
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White),
            )
        }
        if (text.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            BasicText(text = text, style = TextStyle(fontSize = 20.sp, color = Color(0xFF111318)))
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(Color(0xFFEAF1FF), RoundedCornerShape(14.dp))
                    .semantics { contentDescription = "重新朗读识别结果" }
                    .clickable { onSpeak(text) },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(text = "再念一遍", style = TextStyle(fontSize = 17.sp, color = Color(0xFF0A6CFF)))
            }
        }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
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
                        color = if (isSelected) Color(0xFF0A6CFF) else Color(0xFF6B7280),
                    ),
                )
            }
        }
    }
}
