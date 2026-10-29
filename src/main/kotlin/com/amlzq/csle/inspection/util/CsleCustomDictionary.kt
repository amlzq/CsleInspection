package com.amlzq.csle.inspection.util

import com.amlzq.csle.inspection.settings.CsleGlyphs
import com.github.houbb.opencc4j.core.impl.ZhConvertBootstrap
import com.github.houbb.opencc4j.support.datamap.IDataMap
import com.github.houbb.opencc4j.support.datamap.impl.AbstractDataMap
import com.github.houbb.opencc4j.support.datamap.impl.DataMaps
import com.github.houbb.opencc4j.support.segment.Segment

/**
 * 用户自定义惯用语词典。
 *
 * 每行是同一个概念的四种写法，用 Tab 分隔，列顺序固定，与设置里的字形下拉顺序一致：
 * 简体、繁体(台湾)、繁体(香港)、繁体；某一列留空表示该字形下不处理。`#` 开头为注释。
 * 转换时，一行里除目标字形列以外的写法都会被替换成目标字形列的写法，且优先于内置词典。
 */
object CsleCustomDictionary {
    data class ParseError(val line: Int, val content: String)

    /** 一个词条：按 [CsleGlyphs.entries] 的顺序保存四列，缺省或留空的列为 null */
    class Row(val cells: List<String?>)

    class ParseResult(val entries: List<Row>, val errors: List<ParseError>)

    const val TEMPLATE_FILE_NAME = "csle-custom-dictionary.tsv"

    private const val COLUMN_COUNT = 4

    /** 下载给用户的模板：带填写说明，示例行都已注释，导入后不生效 */
    val TEMPLATE: String = listOf(
        "# Chinese Expression Inspection - custom phrase dictionary",
        "# 自定义惯用语词典",
        "#",
        "# How to fill in / 填写说明",
        "# 1. One concept per line, four columns separated by a Tab (not spaces), in this fixed order:",
        "#    每行一个概念，共四列，列之间用 Tab（不是空格）分隔，列顺序固定为：",
        "#      Simplified | Traditional (Taiwan) | Traditional (Hong Kong) | Traditional",
        "#      简体       | 繁体（台湾）          | 繁体（香港）            | 繁体",
        "# 2. Leave a column empty if you don't need that script; keep the Tabs so the columns stay aligned.",
        "#    不需要的字形可以留空，但要保留 Tab，保证列对齐；每行至少填写两列。",
        "# 3. The Inspect and Quick fix settings pick the columns automatically: the phrase found in the",
        "#    code is replaced with the phrase in the Quick fix column. If that column is empty, the line is skipped.",
        "#    设置里的“检查”和“快速修复”会自动选列：代码中找到的写法会被替换为“快速修复”所选字形那一列的写法；该列为空则跳过这一行。",
        "# 4. Lines starting with # and blank lines are ignored. Entries override the built-in dictionary.",
        "#    以 # 开头的行和空行会被忽略。这里的词条优先于内置词典。",
        "# 5. To use an example, remove the leading \"# \" and make sure the columns are separated by Tabs.",
        "#    要使用下面的示例，请去掉行首的“# ”，并确认列之间是 Tab。",
        "#",
        "# Examples / 示例",
        "# 1) All four scripts / 四列都填写：",
        "# 服务器\t伺服器\t伺服器\t服務器",
        "# 2) Only Simplified -> Traditional (Taiwan) / 只处理简体→繁体（台湾），其余列留空：",
        "# 内存\t記憶體\t\t",
        "# 3) Only Simplified -> Traditional (Hong Kong) / 只处理简体→繁体（香港）：",
        "# 软件\t\t軟件\t",
        "# 4) Traditional (Taiwan) -> Simplified: fill the Simplified column too / 繁体（台湾）→简体，同样要填写简体列：",
        "# 程序\t程式\t\t",
    ).joinToString("\n") + "\n"

    fun parse(text: String): ParseResult {
        val entries = mutableListOf<Row>()
        val errors = mutableListOf<ParseError>()
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim('\uFEFF', '\r')
            if (line.isBlank() || line.trimStart().startsWith("#")) return@forEachIndexed
            val parts = line.split('\t')
            val cells = List(COLUMN_COUNT) { parts.getOrNull(it)?.trim()?.takeIf { cell -> cell.isNotEmpty() } }
            if (parts.size > COLUMN_COUNT || cells.count { it != null } < 2) {
                errors += ParseError(index + 1, raw)
            } else {
                entries += Row(cells)
            }
        }
        return ParseResult(entries, errors)
    }

    /** 展开成转换用的映射：目标列有值的行，其它列的写法 -> 目标列的写法 */
    private fun mappingFor(glyph: CsleGlyphs, rows: List<Row>): Map<String, List<String>> {
        val target = CsleGlyphs.entries.indexOf(glyph)
        val result = LinkedHashMap<String, List<String>>()
        for (row in rows) {
            val to = row.cells[target] ?: continue
            row.cells.forEachIndexed { i, from ->
                if (i != target && from != null && from != to) result[from] = listOf(to)
            }
        }
        return result
    }

    private class Key(val glyph: CsleGlyphs, val text: String)

    @Volatile
    private var cached: Pair<Key, ZhConvertBootstrap>? = null

    /**
     * 用户词典 + 内置词典转换文本。词典为空或没有有效词条时返回 null，由调用方使用内置转换器。
     */
    fun convert(glyph: CsleGlyphs, dictionaryText: String, text: String): String? {
        if (dictionaryText.isBlank()) return null
        val bootstrap = bootstrapFor(glyph, dictionaryText) ?: return null
        return when (glyph) {
            CsleGlyphs.SIMPLIFIED -> bootstrap.toSimple(text)
            else -> bootstrap.toTraditional(text)
        }
    }

    @Synchronized
    private fun bootstrapFor(glyph: CsleGlyphs, dictionaryText: String): ZhConvertBootstrap? {
        cached?.let { (key, bs) ->
            if (key.glyph == glyph && key.text == dictionaryText) return bs
        }
        val entries = mappingFor(glyph, parse(dictionaryText).entries)
        if (entries.isEmpty()) return null

        val builtIn: IDataMap = when (glyph) {
            CsleGlyphs.SIMPLIFIED, CsleGlyphs.TRADITIONAL -> DataMaps.defaults()
            CsleGlyphs.TAIWAN -> DataMaps.taiwan()
            CsleGlyphs.HONGKONG -> DataMaps.hongKong()
        }
        val empty = emptyMap<String, List<String>>()
        val custom = object : AbstractDataMap() {
            override fun tsPhrase(): Map<String, List<String>> = if (glyph == CsleGlyphs.SIMPLIFIED) entries else empty
            override fun tsChar(): Map<String, List<String>> = empty
            override fun stPhrase(): Map<String, List<String>> = if (glyph == CsleGlyphs.SIMPLIFIED) empty else entries
            override fun stChar(): Map<String, List<String>> = empty
        }
        // merge 中后者覆盖前者，所以用户词典放在最后
        val merged = DataMaps.merge(builtIn, custom)
        val bootstrap = ZhConvertBootstrap.newInstance()
            .segment(LongestMatchSegment(merged))
            .dataMap(merged)
            .init()
        cached = Key(glyph, dictionaryText) to bootstrap
        return bootstrap
    }

    /**
     * 按词典词组做最长匹配分词。
     *
     * 不用 Opencc4j 的 dataMapFastForward：它的前缀树是静态共享的，第一次构建之后，
     * 用户词典变化（或切换目标字形）时仍会沿用旧词组，导致自定义词条不生效。
     */
    private class LongestMatchSegment(dataMap: IDataMap) : Segment {
        private val words: Set<String> = HashSet<String>().apply {
            addAll(dataMap.stPhrase().keys)
            addAll(dataMap.tsPhrase().keys)
        }
        private val maxLength: Int = words.maxOfOrNull { it.length } ?: 1

        override fun seg(original: String?): List<String> {
            if (original.isNullOrEmpty()) return emptyList()
            val result = ArrayList<String>()
            var i = 0
            while (i < original.length) {
                var end = minOf(original.length, i + maxLength)
                while (end > i + 1 && original.substring(i, end) !in words) end--
                result += original.substring(i, end)
                i = end
            }
            return result
        }
    }
}
