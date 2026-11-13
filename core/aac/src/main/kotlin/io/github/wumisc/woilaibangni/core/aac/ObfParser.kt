package io.github.wumisc.woilaibangni.core.aac

import org.json.JSONArray
import org.json.JSONObject

/**
 * OBF（Open Board Format）解析。
 *
 * 兼容两种常见写法：
 * 1. 单板文件：顶层就有 `grid` / `buttons`；
 * 2. 多板文件：顶层 `boards` 数组，每项是一块板。
 *
 * 规范里 `grid.order` 的每种元素是 `[row, column]` 或 `null`，按钮按出现顺序与之一一对应；
 * 我们额外容忍「只有 buttons 没有 order」的简化文件，此时按行列数顺序铺。
 */
object ObfParser {

    fun parse(json: String, resolveImage: ((String) -> String?)? = null): AacDeck {
        val root = JSONObject(json)
        val deckId = root.optString("id").ifBlank { "imported" }
        val deckName = root.optString("name").ifBlank { "导入的板" }
        val topImages = indexImages(root.optJSONArray("images"))

        val boardObjects: List<JSONObject> = root.optJSONArray("boards")
            ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it) } }
            ?: listOf(root)

        val boards = boardObjects.mapIndexed { index, obj ->
            parseBoard(obj, topImages, index, resolveImage)
        }.filter { it.buttons.isNotEmpty() || it.rows > 0 }

        require(boards.isNotEmpty()) { "文件里没有可用的板" }
        return AacDeck(id = deckId, name = deckName, boards = boards)
    }

    private fun parseBoard(
        obj: JSONObject,
        topImages: Map<String, String>,
        index: Int,
        resolveImage: ((String) -> String?)?,
    ): AacBoard {
        val grid = obj.optJSONObject("grid")
        val rows = grid?.optInt("rows", 0) ?: 0
        val columns = grid?.optInt("columns", 0) ?: 0
        val order = grid?.optJSONArray("order")

        val images = topImages + indexImages(obj.optJSONArray("images"))
        val buttonsJson = obj.optJSONArray("buttons") ?: JSONArray()

        val slots = mutableListOf<Pair<Int, Int>?>()
        if (order != null) {
            for (i in 0 until order.length()) {
                val cell = order.optJSONArray(i)
                slots += if (cell == null) null else cell.optInt(0) to cell.optInt(1)
            }
        }

        val buttons = mutableListOf<BoardButton>()
        for (i in 0 until buttonsJson.length()) {
            val b = buttonsJson.optJSONObject(i) ?: continue
            val slot = slots.getOrNull(i)
            // 没有 order 时退化为顺序铺格
            val defaultSlot = if (columns > 0) (i / columns) to (i % columns) else 0 to i
            val (row, column) = slot ?: defaultSlot

            val imageId = b.optString("image_id").ifBlank { null }
            val rawPath = imageId?.let { images[it] }
            val resolved = rawPath?.let { resolveImage?.invoke(it) ?: it }

            buttons += BoardButton(
                id = b.optString("id").ifBlank { "${obj.optString("id")}-$row-$column" },
                label = b.optString("label"),
                vocalization = b.optString("vocalization"),
                imageId = imageId,
                imagePath = resolved,
                loadBoardId = b.optJSONObject("load_board")?.optString("id")?.ifBlank { null },
                action = b.optString("action").ifBlank { null },
                row = row,
                column = column,
                backgroundColor = b.optString("background_color").ifBlank { null },
            )
        }

        return AacBoard(
            id = obj.optString("id").ifBlank { "board-$index" },
            name = obj.optString("name").ifBlank { "板 ${index + 1}" },
            rows = rows.coerceAtLeast(if (columns > 0) (buttons.size + columns - 1) / columns else 1),
            columns = columns.coerceAtLeast(if (rows > 0) (buttons.size + rows - 1) / rows else 1),
            buttons = buttons,
        )
    }

    private fun indexImages(arr: JSONArray?): Map<String, String> {
        if (arr == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (i in 0 until arr.length()) {
            val img = arr.optJSONObject(i) ?: continue
            val id = img.optString("id").ifBlank { continue }
            val path = img.optString("path").ifBlank { img.optString("url") }
            if (path.isNotBlank()) map[id] = path
        }
        return map
    }
}
