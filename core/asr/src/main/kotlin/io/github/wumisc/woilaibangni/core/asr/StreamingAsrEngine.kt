package io.github.wumisc.woilaibangni.core.asr

/**
 * 流式识别引擎接口。
 *
 * 实现约定：
 * - [feed] 在录音线程调用，必须非阻塞、尽快返回；
 * - 中间结果允许抖动，调用方不得把它当作最终文案；
 * - [finishUtterance] 由 VAD 判定一句话结束后调用，返回定稿文本。
 */
interface StreamingAsrEngine {
    val isReady: Boolean

    /** 加载模型，耗时操作，必须在 IO 线程调用。 */
    suspend fun prepare(modelDir: String): Boolean

    fun feed(samples: ShortArray, sampleRate: Int, onPartial: (String) -> Unit)

    fun finishUtterance(): String

    /**
     * 两遍识别的第二遍：把整句 PCM 交给高精度引擎重新识别。
     * 返回 null 表示没有校正（引擎不可用或结果为空），调用方应保留第一遍结果。
     */
    suspend fun correct(samples: ShortArray, sampleRate: Int): String? = null

    fun release()
}

/** 供链路自检与单测使用：不推理，只验证采集→VAD→UI 通路。 */
class DemoAsrEngine : StreamingAsrEngine {
    override val isReady: Boolean = true
    private var chunk = 0

    override suspend fun prepare(modelDir: String): Boolean = true

    override fun feed(samples: ShortArray, sampleRate: Int, onPartial: (String) -> Unit) {
        chunk++
        if (chunk % 6 == 0) onPartial("（演示）正在听……")
    }

    override fun finishUtterance(): String = "（演示）这一段是链路自检文本，接入模型后会替换成真实识别结果。"

    override fun release() {
        chunk = 0
    }
}
