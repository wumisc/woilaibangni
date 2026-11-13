package io.github.wumisc.woilaibangni.core.aac

/**
 * AAC 板块的数据模型，对齐 Open Board Format（OBF）0.1。
 *
 * 为什么不自造格式：OBF/OBZ 是 OpenAAC / CoughDrop / LibreAAC 共用的开放格式，
 * 直接支持它就能导入现成的中文板和符号板，家属和言语治疗师也不用重学一套编辑器。
 * 参考：[OBF 规范](https://www.openboardformat.org/)
 */
data class BoardButton(
    val id: String,
    val label: String,
    /** 按下后朗读的文字；为空则读 [label]。 */
    val vocalization: String = "",
    val imageId: String? = null,
    /** 已解析到本地文件的图片绝对路径（OBZ 解包后才有）。 */
    val imagePath: String? = null,
    /** 非空表示这是一个「跳到另一块板」的按钮。 */
    val loadBoardId: String? = null,
    /** ":back" / ":home" / ":speak" / ":clear" 这类动作按钮。 */
    val action: String? = null,
    val row: Int = 0,
    val column: Int = 0,
    val backgroundColor: String? = null,
) {
    val spokenText: String get() = vocalization.ifBlank { label }
}

data class AacBoard(
    val id: String,
    val name: String,
    val rows: Int,
    val columns: Int,
    val buttons: List<BoardButton>,
) {
    fun buttonAt(row: Int, column: Int): BoardButton? = buttons.firstOrNull { it.row == row && it.column == column }

    /** 按扫描顺序（行优先）排列，跳过空位。 */
    val scanOrder: List<BoardButton>
        get() = buttons.sortedWith(compareBy({ it.row }, { it.column }))
}

data class AacDeck(
    val id: String,
    val name: String,
    val boards: List<AacBoard>,
    /** OBZ 解包目录；用于加载图片。 */
    val baseDir: String? = null,
) {
    val rootBoard: AacBoard? get() = boards.firstOrNull()
    fun board(id: String?): AacBoard? = boards.firstOrNull { it.id == id } ?: rootBoard
}
