package io.github.wumisc.woilaibangni.core.aac

/**
 * 内置中文板：没有导入 OBZ 时先用这套，保证装上就能说话。
 *
 * 设计原则（来自言语治疗师的常见意见）：
 * - 一级只有 6 个场景，减少寻找成本；
 * - 每个场景 6~9 句，都是「一个字就能被听懂」的完整句，不含缩写；
 * - 发声文本 = 显示文本，避免念出来和看到的不一致。
 */
object BoardDefaults {

    private fun button(id: String, label: String, row: Int, col: Int, load: String? = null) =
        BoardButton(id = id, label = label, vocalization = label, row = row, column = col, loadBoardId = load)

    private fun category(id: String, name: String, phrases: List<String>): AacBoard {
        val cols = 3
        val buttons = phrases.mapIndexed { i, text ->
            button("$id-$i", text, i / cols, i % cols, load = ":home")
        }
        return AacBoard(id = id, name = name, rows = (phrases.size + cols - 1) / cols, columns = cols, buttons = buttons)
    }

    fun deck(): AacDeck {
        val root = AacBoard(
            id = "root",
            name = "首页",
            rows = 3,
            columns = 2,
            buttons = listOf(
                button("c-pain", "疼痛", 0, 0, "pain"),
                button("c-doctor", "就医", 0, 1, "doctor"),
                button("c-travel", "出行", 1, 0, "travel"),
                button("c-shop", "买东西", 1, 1, "shop"),
                button("c-help", "求助", 2, 0, "help"),
                button("c-feel", "心情", 2, 1, "feel"),
            ),
        )

        return AacDeck(
            id = "builtin-zh",
            name = "内置中文板",
            boards = listOf(
                root,
                category("pain", "疼痛", listOf(
                    "我疼", "很疼", "疼得厉害", "这里疼", "头疼", "肚子疼", "牙疼", "浑身都疼", "疼多久了",
                )),
                category("doctor", "就医", listOf(
                    "我要看医生", "挂哪个科", "我有医保", "我对青霉素过敏", "这个药怎么吃", "一天吃几次", "饭前还是饭后", "我听力不好，请写下来",
                )),
                category("travel", "出行", listOf(
                    "去火车站", "去机场", "这趟车到不到", "我要下车", "还有几站", "帮我叫辆车", "多少钱", "请慢一点",
                )),
                category("shop", "买东西", listOf(
                    "这个多少钱", "我要这个", "可以扫码吗", "能便宜点吗", "给我开发票", "有别的颜色吗", "我不要了",
                )),
                category("help", "求助", listOf(
                    "请帮帮我", "我听不见", "我说不出话", "请写给我看", "请打这个电话", "我迷路了", "请叫工作人员", "我不舒服",
                )),
                category("feel", "心情", listOf(
                    "我很好", "我有点难受", "我很害怕", "我累了", "我想回家", "谢谢你", "别担心", "我想静一静",
                )),
            ),
        )
    }
}
