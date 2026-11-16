package io.github.wumisc.woilaibangni.core.translate

import io.github.wumisc.woilaibangni.core.log.LogHub
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 字幕语言模式，界面上是一组单选。 */
enum class TranslationMode(val label: String) {
    ORIGINAL("原声"),
    EN_TO_ZH("英→中"),
    ZH_TO_EN("中→英"),
}

/**
 * 在线翻译。两个通道：
 * 1. `gtx`（Google 网页版接口）：延迟几十毫秒，适合"边说话边翻"，做主力；
 * 2. MyMemory：免 key 的公共接口，gtx 失败时兜底。
 *
 * 任何一步失败都返回 null，调用方回落到原文 —— 字幕不能因为翻译挂掉而空着。
 * 离线模型（opus-mt 英中/中英）以后接进来时，接口保持不变。
 */
class OnlineTranslator {

    suspend fun translate(text: String, mode: TranslationMode): String? = withContext(Dispatchers.IO) {
        if (text.isBlank() || mode == TranslationMode.ORIGINAL) return@withContext null
        val (from, to) = when (mode) {
            TranslationMode.EN_TO_ZH -> "en" to "zh-CN"
            TranslationMode.ZH_TO_EN -> "zh-CN" to "en"
            TranslationMode.ORIGINAL -> return@withContext null
        }
        val started = System.currentTimeMillis()
        val fast = fetch(
            "https://translate.googleapis.com/translate_a/single?client=gtx&dt=t&sl=$from&tl=$to&q=" +
                URLEncoder.encode(text, "UTF-8"),
            timeoutMs = 3000,
        )?.let { parseGtx(it) }
        if (fast != null) {
            LogHub.app(TAG, "gtx $from->$to ${System.currentTimeMillis() - started}ms len=${text.length}")
            return@withContext fast
        }
        val slow = fetch(
            "https://api.mymemory.translated.net/get?q=" + URLEncoder.encode(text, "UTF-8") +
                "&langpair=" + URLEncoder.encode("$from|$to", "UTF-8"),
            timeoutMs = 5000,
        )?.let { parseMyMemory(it) }
        if (slow != null) {
            LogHub.app(TAG, "mymemory $from->$to ${System.currentTimeMillis() - started}ms len=${text.length}")
        } else {
            LogHub.app(TAG, "翻译全部失败，回落原文 len=${text.length}")
        }
        slow
    }

    private fun fetch(url: String, timeoutMs: Int): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs + 1000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) woilaibangni")
        }
        try {
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader().use(BufferedReader::readText)
        } finally {
            conn.disconnect()
        }
    }.getOrElse {
        LogHub.app(TAG, "翻译请求异常：${it.message}")
        null
    }

    /**
     * gtx 返回 `[[["译文","原文",null,null,10],...],null,"en",...]`，
     * 每段前两个字符串依次是译文、原文，取偶数位即译文。
     */
    private fun parseGtx(body: String): String? {
        val quoted = QUOTED.findAll(body).map { it.groupValues[1] }.toList()
        if (quoted.isEmpty()) return null
        val sb = StringBuilder()
        quoted.forEachIndexed { index, raw -> if (index % 2 == 0) sb.append(unescape(raw)) }
        return sb.toString().ifBlank { null }
    }

    /** MyMemory：只取 responseData.translatedText。 */
    private fun parseMyMemory(body: String): String? {
        val key = "\"translatedText\":\""
        val start = body.indexOf(key)
        if (start < 0) return null
        val sb = StringBuilder()
        var i = start + key.length
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> {
                    val esc = body[i + 1]
                    when (esc) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'u' -> if (i + 5 < body.length) {
                            runCatching { sb.append(body.substring(i + 2, i + 6).toInt(16).toChar()) }
                            i += 4
                        }
                        else -> sb.append(esc)
                    }
                    i += 2
                }
                c == '"' -> break
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        val text = sb.toString()
        if (text.contains("MYMEMORY WARNING", ignoreCase = true)) return null
        return text.ifBlank { null }
    }

    private fun unescape(raw: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (raw[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'u' -> if (i + 5 < raw.length) {
                        runCatching { sb.append(raw.substring(i + 2, i + 6).toInt(16).toChar()) }
                        i += 4
                    }
                    else -> sb.append(raw[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    companion object {
        private const val TAG = "WOILAI_TRANSLATE"

        /** 匹配字符串字面量，内部允许转义。 */
        private val QUOTED = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
    }
}
