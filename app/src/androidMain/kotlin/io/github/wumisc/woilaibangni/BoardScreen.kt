package io.github.wumisc.woilaibangni

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wumisc.woilaibangni.core.aac.AacBoard
import io.github.wumisc.woilaibangni.core.aac.AacDeck
import io.github.wumisc.woilaibangni.core.aac.BoardButton
import io.github.wumisc.woilaibangni.core.aac.ScanMode
import io.github.wumisc.woilaibangni.core.aac.SwitchScanner
import kotlinx.coroutines.delay

/**
 * 短句板：直接渲染 OBF/OBZ 板块。
 *
 * 两个使用态（来自 LibreAAC 的经验）：
 * - 日常态：只有格子，点一下就读出来；
 * - 开关扫描态：格子自动轮巡，按一下「选择」代表点中当前格子——
 *   给手抬不起来、只能按一个开关的用户用。
 */
@Composable
fun BoardScreen(
    deck: AacDeck,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var boardId by remember(deck) { mutableStateOf(deck.rootBoard?.id) }
    var phrase by remember { mutableStateOf("") }
    var scanEnabled by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf<BoardButton?>(null) }

    val board = deck.board(boardId)
    if (board == null) {
        BasicText("没有可用的板", style = TextStyle(fontSize = 18.sp, color = Color(0xFF6B7280)))
        return
    }
    val scanner = remember(board) { SwitchScanner(board, ScanMode.LINEAR) }

    LaunchedEffect(scanEnabled, board) {
        if (!scanEnabled) {
            highlight = null
            return@LaunchedEffect
        }
        scanner.reset()
        highlight = scanner.highlight()
        while (scanEnabled) {
            delay(scanner.intervalMs)
            scanner.next()
            highlight = scanner.highlight()
        }
    }

    fun activate(button: BoardButton) {
        when {
            button.loadBoardId == ":home" -> boardId = deck.rootBoard?.id
            button.loadBoardId != null -> boardId = button.loadBoardId
            button.action == ":back" -> boardId = deck.rootBoard?.id
            button.action == ":speak" -> if (phrase.isNotBlank()) {
                onSpeak(phrase)
                phrase = ""
            }
            else -> {
                val spoken = button.spokenText
                if (spoken.isNotBlank()) {
                    phrase = if (phrase.isBlank()) spoken else "$phrase$spoken"
                    onSpeak(spoken)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().semantics { contentDescription = "短句板，当前板：${board.name}" }) {
        // 组句栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E1116), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            BasicText(
                text = phrase.ifBlank { "点格子组句，或直接点一句说出来" },
                style = TextStyle(
                    fontSize = 18.sp,
                    color = if (phrase.isBlank()) Color(0xFF9AA3B2) else Color(0xFFFFD75E),
                ),
            )
        }
        Spacer(Modifier.height(10.dp))

        // 格子
        for (row in 0 until board.rows) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                for (column in 0 until board.columns) {
                    val button = board.buttonAt(row, column)
                    val isHighlighted = highlight?.id == button?.id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(74.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                when {
                                    button == null -> Color(0x00000000)
                                    isHighlighted -> Color(0xFF0A6CFF)
                                    else -> Color.White
                                },
                                RoundedCornerShape(14.dp),
                            )
                            .then(
                                if (button != null) Modifier.border(0.5.dp, Color(0xFFE3E6ED), RoundedCornerShape(14.dp))
                                else Modifier
                            )
                            .semantics {
                                if (button != null) contentDescription = "${button.spokenText}${if (isHighlighted) "，当前选中" else ""}"
                            }
                            .clickable(enabled = button != null) { button?.let(::activate) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (button != null) {
                            BasicText(
                                text = button.label,
                                style = TextStyle(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isHighlighted) Color.White else Color(0xFF161A22),
                                ),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .background(if (scanEnabled) Color(0xFF0A6CFF) else Color(0xFFEDF0F5), RoundedCornerShape(14.dp))
                    .semantics { contentDescription = if (scanEnabled) "关闭开关扫描" else "开启开关扫描" }
                    .clickable { scanEnabled = !scanEnabled },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = if (scanEnabled) "扫描中 · 关闭" else "开关扫描",
                    style = TextStyle(fontSize = 16.sp, color = if (scanEnabled) Color.White else Color(0xFF3D4353)),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .background(Color(0xFF0A6CFF), RoundedCornerShape(14.dp))
                    .semantics { contentDescription = "选择当前高亮的格子" }
                    .clickable(enabled = scanEnabled) {
                        val picked = scanner.select()
                        highlight = scanner.highlight()
                        picked?.let(::activate)
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText("选择", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .background(Color(0xFFF5F6F9), RoundedCornerShape(12.dp))
                    .clickable { boardId = deck.rootBoard?.id },
                contentAlignment = Alignment.Center,
            ) { BasicText("回首页", style = TextStyle(fontSize = 15.sp, color = Color(0xFF3D4353))) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .background(Color(0xFFF5F6F9), RoundedCornerShape(12.dp))
                    .clickable { phrase = "" },
                contentAlignment = Alignment.Center,
            ) { BasicText("清空句子", style = TextStyle(fontSize = 15.sp, color = Color(0xFF3D4353))) }
        }
    }
}
