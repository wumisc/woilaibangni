package io.github.wumisc.woilaibangni

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 主界面算好的「该显示什么」，悬浮字幕直接抄过去。
 *
 * 为什么要这个：翻译只该算一次。如果服务自己再翻译一遍，
 * 同一个句子会走两次网络、耗两次电，还可能翻得不一样。
 */
object CaptionBus {

    /** 大字幕（可能是译文）。 */
    val main = MutableStateFlow("")

    /** 小字原文，翻译模式下给用户对照。 */
    val original = MutableStateFlow("")
}
