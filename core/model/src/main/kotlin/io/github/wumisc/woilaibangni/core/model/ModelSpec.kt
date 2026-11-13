package io.github.wumisc.woilaibangni.core.model

/** 模型用途分类：按人群分包下载，避免一次性 450MB。 */
enum class ModelKind {
    /** 流式识别，边说边出字，约 80MB。 */
    ASR_STREAM,

    /** 非流式高精度识别 + 标点，约 230MB。 */
    ASR_ACCURATE,

    /** 离线发声（系统未装中文语音包时兜底），约 100MB。 */
    TTS_OFFLINE,

    /** 离线 OCR（视障读纸面必备），约 20MB。 */
    OCR,
}

/**
 * 一个可下载模型包的描述。
 *
 * 约定：我们自己在 GitHub Release 上重新打包成 **zip**（内含模型文件），
 * 不用上游的 tar.bz2 —— Android 端没有内置 bz2 解压，避免为此引入 native 库。
 */
data class ModelSpec(
    val id: String,
    val kind: ModelKind,
    val displayName: String,
    val approxBytes: Long,
    val url: String,
    val sha256: String = "",
    val requiredFor: String = "",
)

object ModelCatalog {

    val streamingAsr = ModelSpec(
        id = "asr-stream-zipformer-zh-en-int8",
        kind = ModelKind.ASR_STREAM,
        displayName = "对话包 · 实时字幕",
        approxBytes = 80L * 1024 * 1024,
        url = "https://github.com/wumisc/woilaibangni/releases/download/models-v1/asr-stream.zip",
        requiredFor = "面对面 / 只听",
    )

    val accurateAsr = ModelSpec(
        id = "asr-sensevoice-zh-int8",
        kind = ModelKind.ASR_ACCURATE,
        displayName = "高精度包 · 句末校正",
        approxBytes = 230L * 1024 * 1024,
        url = "https://github.com/wumisc/woilaibangni/releases/download/models-v1/asr-accurate.zip",
        requiredFor = "嘈杂环境 / 方言",
    )

    val offlineTts = ModelSpec(
        id = "tts-vits-zh-aishell3",
        kind = ModelKind.TTS_OFFLINE,
        displayName = "发言包 · 离线音色",
        approxBytes = 100L * 1024 * 1024,
        url = "https://github.com/wumisc/woilaibangni/releases/download/models-v1/tts-offline.zip",
        requiredFor = "沟通板 / 短句发声",
    )

    val ocr = ModelSpec(
        id = "ocr-ppocrv5-mobile-int8",
        kind = ModelKind.OCR,
        displayName = "朗读包 · 拍照识字",
        approxBytes = 20L * 1024 * 1024,
        url = "https://github.com/wumisc/woilaibangni/releases/download/models-v1/ocr.zip",
        requiredFor = "看世界",
    )

    val all: List<ModelSpec> = listOf(streamingAsr, accurateAsr, offlineTts, ocr)
}
