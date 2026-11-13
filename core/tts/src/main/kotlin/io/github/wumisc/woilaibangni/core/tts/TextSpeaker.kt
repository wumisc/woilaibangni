package io.github.wumisc.woilaibangni.core.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import java.util.UUID

/** 全 App 唯一的发声出口；同类实现必须共用 [SpeechArbiter] 的仲裁。 */
interface TextSpeaker {
    val ready: StateFlow<Boolean>
    fun speak(text: String)
    fun stop()
    fun shutdown()
    /** 0.5x ~ 2.0x，视障用户常调到 1.6x 以上。 */
    fun setRate(rate: Float)
}

/**
 * 系统 TTS 实现。零体积、可挂第三方离线引擎（CloneTTS / 讯飞离线包）。
 * 若系统没有中文引擎，UI 应引导用户安装，或改用离线 VITS。
 */
class AndroidTextSpeaker(context: Context) : TextSpeaker {

    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready

    private var rate: Float = 1.0f

    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val lang = runCatching { tts.setLanguage(Locale.SIMPLIFIED_CHINESE) }.getOrDefault(-1)
                _ready.value = lang != TextToSpeech.LANG_MISSING_DATA &&
                    lang != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                _ready.value = false
            }
        }
        runCatching {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = Unit
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = Unit
            })
        }
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        tts.setSpeechRate(rate)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "u-${UUID.randomUUID()}")
    }

    override fun stop() {
        runCatching { tts.stop() }
    }

    override fun shutdown() {
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
    }

    override fun setRate(rate: Float) {
        this.rate = rate.coerceIn(0.5f, 2.0f)
        runCatching { tts.setSpeechRate(this.rate) }
    }
}
