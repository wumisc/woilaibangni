package io.github.wumisc.woilaibangni.core.aac

/**
 * 开关扫描（switch scanning）：给只能控制一个按键的用户使用。
 *
 * 两种模式：
 * - [ScanMode.LINEAR]：逐个格子轮巡，按一下选中当前格子；
 * - [ScanMode.ROW_COLUMN]：先扫行、再扫列，按键次数少，适合格子多的板。
 *
 * 这是一个纯状态机，不依赖 UI 与定时器：由调用方按 [intervalMs] 调 [next]，
 * 按一下调 [select]。这样单测可以直接跑逻辑，不必等真实的时钟。
 */
enum class ScanMode { LINEAR, ROW_COLUMN }

class SwitchScanner(
    private val board: AacBoard,
    val mode: ScanMode = ScanMode.LINEAR,
    val intervalMs: Long = 1200L,
) {

    private enum class Phase { ROW, COLUMN }

    private var phase = Phase.ROW
    private var rowIndex = 0
    private var columnIndex = -1
    private var linearIndex = 0

    val cells: List<BoardButton> get() = board.scanOrder

    /** 当前高亮的格子；行/列阶段返回该行/列的第一个格子用于高亮整行整列。 */
    fun highlight(): BoardButton? = when (mode) {
        ScanMode.LINEAR -> cells.getOrNull(linearIndex % cells.size.coerceAtLeast(1))
        ScanMode.ROW_COLUMN -> if (phase == Phase.ROW) {
            cells.firstOrNull { it.row == rowIndex }
        } else {
            cells.firstOrNull { it.row == rowIndex && it.column == columnIndex }
        }
    }

    fun highlightedRow(): Int = if (mode == ScanMode.ROW_COLUMN && phase == Phase.ROW) rowIndex else -1
    fun highlightedColumn(): Int = if (mode == ScanMode.ROW_COLUMN && phase == Phase.COLUMN) columnIndex else -1

    fun next() {
        when (mode) {
            ScanMode.LINEAR -> linearIndex = (linearIndex + 1) % cells.size.coerceAtLeast(1)
            ScanMode.ROW_COLUMN -> if (phase == Phase.ROW) {
                rowIndex = (rowIndex + 1) % board.rows.coerceAtLeast(1)
            } else {
                val rowButtons = cells.filter { it.row == rowIndex }
                if (rowButtons.isEmpty()) {
                    phase = Phase.ROW
                    rowIndex = 0
                    columnIndex = -1
                } else {
                    val cols = rowButtons.map { it.column }
                    val cur = cols.indexOf(columnIndex)
                    columnIndex = cols[(cur + 1) % cols.size]
                    if (columnIndex == cols.first() && cur == cols.lastIndex) {
                        // 这一行扫完，回到行扫描
                        phase = Phase.ROW
                        columnIndex = -1
                    }
                }
            }
        }
    }

    /** 按下开关。行阶段进入列扫描，列阶段返回选中的按钮。 */
    fun select(): BoardButton? = when (mode) {
        ScanMode.LINEAR -> cells.getOrNull(linearIndex)
        ScanMode.ROW_COLUMN -> if (phase == Phase.ROW) {
            val rowButtons = cells.filter { it.row == rowIndex }
            if (rowButtons.isEmpty()) {
                null
            } else {
                phase = Phase.COLUMN
                columnIndex = rowButtons.first().column
                null
            }
        } else {
            val picked = cells.firstOrNull { it.row == rowIndex && it.column == columnIndex }
            phase = Phase.ROW
            columnIndex = -1
            picked
        }
    }

    fun reset() {
        phase = Phase.ROW
        rowIndex = 0
        columnIndex = -1
        linearIndex = 0
    }
}
