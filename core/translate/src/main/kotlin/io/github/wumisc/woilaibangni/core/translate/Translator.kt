package io.github.wumisc.woilaibangni.core.translate

import io.github.wumisc.woilaibangni.core.log.LogHub
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 字幕语言模式，界面上是一组单选。 */
enum class TranslationMode(val label: String) {
    ORIGINAL("原声"),
    EN_TO_ZH("英→中"),
    ZH_TO_EN("中→英"),
}

/**
 * 翻译器。当前实现在线翻译（MyMemory 公共接口，无需注册、无需 key），
 * 失败或断网时返回 null，由调用方回落到原文 —— 绝不能因为翻译失败让字幕空掉。
 *
 * 离线模型（opus-mt 英中/中英，各约 40MB）作为下一步单独接入，
 * 接口保持一样，换实现即可。
 */
class OnlineTranslator {

    suspend fun translate(text: String, mode: TranslationMode): String? = withContext(Dispatchers.IO) {
        if (text.isBlank() || mode == TranslationMode.ORIGINAL) return@withContext null
        val pair = when (mode) {
            TranslationMode.EN_TO_ZH -> "en|zh-CN"
            TranslationMode.ZH_TO_EN -> "zh-CN|en"
            TranslationMode.ORIGINAL -> return@withContext null
        }
        runCatching {
            val url = "https://api.mymemory.translated.net/get?q=" +
                URLEncoder.encode(text, "UTF-8") + "&langpair=" + URLEncoder.encode(pair, "UTF-8")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "woilaibangni/0.6")
            }
            try {
                if (conn.responseCode != 200) {
                    LogHub.app(TAG, "在线翻译 HTTP ${conn.responseCode}")
                    return@runCatching null
                }
                val body = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val translated = parseResponse(body)
                LogHub.app(TAG, "在线翻译 ${pair} len=${text.length} -> ${translated?.length ?: 0}")
                translated
            } finally {
                conn.disconnect()
            }
        }.getOrElse {
            LogHub.app(TAG, "在线翻译失败：${it.message}")
            null
        }
    }

    /** MyMemory 返回体很小，只取 responseData.translatedText，避免引 JSON 库。 */
    private fun parseResponse(body: String): String? {
        val key = "\"translatedText\":\""
        val start = body.indexOf(key)
        if (start < 0) return null
        val sb = StringBuilder()
        var i = start + key.length
        while (i < body.length) {
            val c = body[i]
            when {
                c == '\\' && i + 1 < body.length -> {
                    when (body[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 5 < body.length) {
                                runCatching {
                                    sb.append(body.substring(i + 2, i + 6).toInt(16).toChar())
                                }
                                i += 4
                            }
                        }
                        else -> sb.append(body[i + 1])
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
        // MyMemory 偶尔把原始文本原样返回（配额用尽时的提示也在这里）
        val text = sb.toString()
        if (text.contains("MYMEMORY WARNING", ignoreCase = true)) return null
        return text.ifBlank { null }
    }

    companion object {
        private const val TAG = "WOILAI_TRANSLATE"
    }
}
