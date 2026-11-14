package com.amlzq.csle.inspection.util

import com.amlzq.csle.inspection.settings.CsleGlyphs
import com.github.houbb.opencc4j.core.impl.ZhConvertBootstrap
import com.github.houbb.opencc4j.support.datamap.IDataMap
import com.github.houbb.opencc4j.support.datamap.impl.AbstractDataMap
import com.github.houbb.opencc4j.support.datamap.impl.DataMaps
import com.github.houbb.opencc4j.support.segment.Segment

/**
 * 用户自定义词典，沿用 OpenCC 词典文件的格式：
 * UTF-8 文本，每行 `原词<Tab>目标词`，`#` 开头的行和空行会被忽略。
 * 目标词是一个不含空白的词；OpenCC 词典里的多个候选词只保留第一个。
 *
 * 词典按“快速修复的目标字形”分别保存：原词就是代码里的原文，不管它属于哪种字形，
 * 所以只需要区分目标。转换时用户词条优先于 Opencc4j 内置词典。
 */
object CsleCustomDictionary {
    data class ParseError(val line: Int, val content: String)

    /** [entries] 是 原词 -> 目标词；同一原词重复出现时以后面的为准，次数记在 [duplicates] */
    class ParseResult(
        val entries: Map<String, String>,
        val errors: List<ParseError>,
        val duplicates: Int,
    )

    const val TEMPLATE_FILE_NAME = "csle-custom-dictionary.txt"

    /** 各目标字形的示例词条（原词, 目标词） */
    private fun examplesFor(glyph: CsleGlyphs): List<Pair<String, String>> = when (glyph) {
        CsleGlyphs.SIMPLIFIED -> listOf("程式" to "程序", "軟體" to "软件", "記憶體" to "内存")
        CsleGlyphs.TAIWAN -> listOf("程序" to "程式", "软件" to "軟體", "内存" to "記憶體")
        CsleGlyphs.HONGKONG -> listOf("程序" to "程式", "软件" to "軟件", "内存" to "記憶體")
        CsleGlyphs.TRADITIONAL -> listOf("服务器" to "服務器", "软件" to "軟件", "内存" to "內存")
    }

    /** 下载给用户的模板：简短说明加上该目标字形的示例；示例行以 # 紧跟内容的形式注释掉，导入后不生效 */
    fun templateFor(glyph: CsleGlyphs): String = (
        listOf(
            "# Custom dictionary, Quick fix target: ${glyph.label} / 自定义词典，快速修复目标：${glyph.label}",
            "# One entry per line: <source><Tab><target>. 每行一条：原词<Tab>目标词。",
            "# The target is a single phrase without spaces. 目标词是一个不含空格的词。",
            "# Lines starting with # are comments; for a repeated source the last entry wins.",
            "# 以 # 开头的行是注释；原词重复时以最后一条为准。",
            "# To use an example, delete the leading # (no space after it). 去掉示例行行首的 # 即可使用。",
            "#",
        ) + examplesFor(glyph).map { (source, target) -> "#$source\t$target" }
        ).joinToString("\n") + "\n"

    fun parse(text: String): ParseResult {
        val entries = LinkedHashMap<String, String>()
        val errors = mutableListOf<ParseError>()
        var duplicates = 0
        text.lineSequence().forEachIndexed { index, raw ->
            val line = raw.trim('\uFEFF', '\r')
            if (line.isBlank() || line.trimStart().startsWith("#")) return@forEachIndexed
            val (key, rest) = splitKeyAndValues(line.trim())
            // OpenCC 词典的多个候选词用空格分隔，这里只取第一个
            val value = rest.split(Regex("\\s+")).firstOrNull { it.isNotEmpty() }
            if (key.isEmpty() || value == null) {
                errors += ParseError(index + 1, raw)
            } else {
                if (entries.put(key, value) != null) duplicates++
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

    enum class ValidationError {
        EMPTY_SOURCE, EMPTY_TARGET,

        /** 原词以 # 开头会被当成注释行 */
        COMMENT_PREFIX,

        /** 原词含有 Tab、换行，会破坏词典格式 */
        INVALID_CHARACTER,

        /** 目标词只能是一个不含空白的词 */
        TARGET_HAS_SPACE,
    }

    /** 校验一条词条，合法返回 null */
    fun validate(source: String, target: String): ValidationError? {
        val key = source.trim()
        val value = target.trim()
        return when {
            key.isEmpty() -> ValidationError.EMPTY_SOURCE
            value.isEmpty() -> ValidationError.EMPTY_TARGET
            key.startsWith("#") -> ValidationError.COMMENT_PREFIX
            key.any { it == '\t' || it == '\n' || it == '\r' } -> ValidationError.INVALID_CHARACTER
            value.any { it.isWhitespace() } -> ValidationError.TARGET_HAS_SPACE
            else -> null
        }
    }

    /** 输出为词典文本（每条一行，注释和空行不保留） */
    fun format(entries: Map<String, String>): String =
        entries.entries.joinToString("") { "${it.key}\t${it.value}\n" }

    /** 往词典文本里加入一条；原词已存在时覆盖。返回新文本和是否覆盖了已有词条 */
    fun put(text: String, source: String, target: String): Pair<String, Boolean> {
        val entries = LinkedHashMap(parse(text).entries)
        val key = source.trim()
        val overwritten = entries.containsKey(key)
        entries[key] = target.trim()
        return format(entries) to overwritten
    }

    private class Key(val glyph: CsleGlyphs, val source: CsleGlyphs, val text: String)

    private class Cached(val key: Key, val bootstrap: ZhConvertBootstrap, val userWords: LongestMatchSegment)

    @Volatile
    private var cached: Cached? = null

    /**
     * 用户词典 + 内置词典转换文本。词典为空或没有有效词条时返回 null，由调用方使用内置转换器。
     *
     * [source] 为检查字形：转简体时据此选用台湾/香港内置词典，让地区用词转成大陆用词；
     * 繁体之间互转时先在原文上匹配用户词条，其余片段按源字形转成简体，再转成目标繁体。
     */
    fun convert(
        glyph: CsleGlyphs,
        dictionaryText: String,
        text: String,
        source: CsleGlyphs = CsleGlyphs.SIMPLIFIED,
    ): String? {
        if (dictionaryText.isBlank()) return null
        val c = cachedFor(glyph, source, dictionaryText) ?: return null
        if (glyph == CsleGlyphs.SIMPLIFIED) return c.bootstrap.toSimple(text)
        if (!CsleUtils.isTraditionalPivot(source, glyph)) return c.bootstrap.toTraditional(text)

        // 原词是代码里的原文（繁体），转成简体后就匹配不上，所以先在原文上切出用户词条
        val out = StringBuilder()
        val pending = StringBuilder()
        fun flush() {
            if (pending.isNotEmpty()) {
                out.append(c.bootstrap.toTraditional(CsleUtils.toSimple(source, pending.toString())))
                pending.setLength(0)
            }
        }
        for (piece in c.userWords.seg(text)) {
            val value = c.userWords.valueOf(piece)
            if (value != null) {
                flush()
                out.append(value)
            } else {
                pending.append(piece)
            }
        }
        flush()
        return out.toString()
    }

    @Synchronized
    private fun cachedFor(glyph: CsleGlyphs, source: CsleGlyphs, dictionaryText: String): Cached? {
        // 转繁体时内置词典只由目标决定，源字形不参与，归一化后避免无谓地重建缓存
        val dictSource = if (glyph == CsleGlyphs.SIMPLIFIED) source else CsleGlyphs.SIMPLIFIED
        cached?.let {
            if (it.key.glyph == glyph && it.key.source == dictSource && it.key.text == dictionaryText) return it
        }
        val parsed = parse(dictionaryText).entries
        if (parsed.isEmpty()) return null
        val entries = parsed.mapValues { listOf(it.value) }

        val builtIn: IDataMap = when (if (glyph == CsleGlyphs.SIMPLIFIED) dictSource else glyph) {
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
            .segment(LongestMatchSegment(merged.stPhrase().keys + merged.tsPhrase().keys))
            .dataMap(merged)
            .init()
        return Cached(Key(glyph, dictSource, dictionaryText), bootstrap, LongestMatchSegment(parsed.keys, parsed))
            .also { cached = it }
    }

    /**
     * 按词典词组做最长匹配分词。
     *
     * 不用 Opencc4j 的 dataMapFastForward：它的前缀树是静态共享的，第一次构建之后，
     * 用户词典变化（或切换目标字形）时仍会沿用旧词组，导致自定义词条不生效。
     */
    private class LongestMatchSegment(
        private val words: Set<String>,
        private val values: Map<String, String> = emptyMap(),
    ) : Segment {
        fun valueOf(word: String): String? = values[word]

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
