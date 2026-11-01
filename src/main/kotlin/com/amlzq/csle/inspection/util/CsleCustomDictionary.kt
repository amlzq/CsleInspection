package com.amlzq.csle.inspection.util

import com.amlzq.csle.inspection.settings.CsleGlyphs
import com.github.houbb.opencc4j.core.impl.ZhConvertBootstrap
import com.github.houbb.opencc4j.support.datamap.IDataMap
import com.github.houbb.opencc4j.support.datamap.impl.AbstractDataMap
import com.github.houbb.opencc4j.support.datamap.impl.DataMaps
import com.github.houbb.opencc4j.support.segment.Segment

/**
 * 用户自定义惯用语词典，沿用 OpenCC 词典文件的格式：
 * UTF-8 文本，每行 `原词<Tab>目标词 [候选2 ...]`，多个候选词用空格分隔，第一个候选词生效；
 * `#` 开头的行和空行会被忽略。
 *
 * 词典按“快速修复的目标字形”分别保存：原词就是代码里的原文，不管它属于哪种字形，
 * 所以只需要区分目标。转换时用户词条优先于 Opencc4j 内置词典。
 */
object CsleCustomDictionary {
    data class ParseError(val line: Int, val content: String)

    /** [entries] 是 原词 -> 候选词；同一原词重复出现时以后面的为准，次数记在 [duplicates] */
    class ParseResult(
        val entries: Map<String, List<String>>,
        val errors: List<ParseError>,
        val duplicates: Int,
    )

    const val TEMPLATE_FILE_NAME = "csle-custom-dictionary.txt"

    /** 下载给用户的模板：带填写说明，示例行以 # 紧跟内容的形式注释掉，导入后不生效 */
    val TEMPLATE: String = listOf(
        "# Chinese Expression Inspection - custom phrase dictionary (OpenCC dictionary format)",
        "# 自定义惯用语词典（OpenCC 词典格式）",
        "#",
        "# How to fill in / 填写说明",
        "# 1. UTF-8 text. One entry per line: <source phrase><Tab><target phrase>.",
        "#    UTF-8 文本，每行一条：原词<Tab>目标词。",
        "# 2. Use a Tab between the two columns. If a line has no Tab, the first run of spaces is used instead.",
        "#    两列之间用 Tab 分隔；如果一行里没有 Tab，则以第一段空格作为分隔。原词本身含空格时必须使用 Tab。",
        "# 3. To list several candidates, separate them with spaces; only the first one is used.",
        "#    有多个候选词时用空格分隔，只有第一个会被使用。",
        "# 4. Import this file while the matching Quick fix target is selected: the file only applies to that",
        "#    target (the source phrase can be in any script). Each target keeps its own dictionary.",
        "#    请在选中对应的“快速修复”目标字形时导入：这份词典只对该目标字形生效（原词可以是任意字形），",
        "#    每个目标字形各自保存一份词典。",
        "# 5. Lines starting with # and blank lines are ignored. If a source phrase appears more than once, the",
        "#    last one wins. Entries override the built-in dictionary.",
        "#    以 # 开头的行和空行会被忽略；同一个原词出现多次时以最后一次为准。这里的词条优先于内置词典。",
        "# 6. To use an example below, delete the leading # (the example lines start with # and no space).",
        "#    要使用下面的示例，请删掉行首的 #（示例行的 # 后面没有空格）。",
        "#",
        "# Examples / 示例 (Quick fix target: Traditional (Taiwan) / 快速修复目标：繁体（台湾）)",
        "#服务器\t伺服器",
        "#内存\t記憶體",
        "#软件\t軟體 软件",
        "#",
        "# Example for Quick fix target Simplified / 快速修复目标为简体时的示例：",
        "#程式\t程序",
    ).joinToString("\n") + "\n"

    fun parse(text: String): ParseResult {
        val entries = LinkedHashMap<String, List<String>>()
        val errors = mutableListOf<ParseError>()
        var duplicates = 0
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim('\uFEFF', '\r')
            if (line.isBlank() || line.trimStart().startsWith("#")) return@forEachIndexed
            val (key, rest) = splitKeyAndValues(line.trim())
            val values = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (key.isEmpty() || values.isEmpty()) {
                errors += ParseError(index + 1, raw)
            } else {
                if (entries.put(key, values) != null) duplicates++
            }
        }
        return ParseResult(entries, errors, duplicates)
    }

    /** 有 Tab 时按第一个 Tab 切分（原词可含空格）；否则按第一段空白切分 */
    private fun splitKeyAndValues(line: String): Pair<String, String> {
        val tab = line.indexOf('\t')
        if (tab >= 0) return line.substring(0, tab).trim() to line.substring(tab + 1).trim()
        val space = line.indexOfFirst { it.isWhitespace() }
        if (space < 0) return line to ""
        return line.substring(0, space) to line.substring(space + 1).trim()
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
        val entries = parse(dictionaryText).entries
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
