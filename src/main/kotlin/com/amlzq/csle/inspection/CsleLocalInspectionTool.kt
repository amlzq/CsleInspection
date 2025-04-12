package com.amlzq.csle.inspection

import com.intellij.codeInspection.LocalInspectionTool
import java.util.regex.Pattern

/**
 * 汉字检查器
 *
 * 在字符串表达式，如果含有汉字则显示警告，并提供简体字/繁体字之间的转换。
 */
abstract class CsleLocalInspectionTool : LocalInspectionTool() {
    /**
     * 匹配字符串中的英文字母、数字、标点、空格，以及中文的标点符号
     *
     * \\p{Punct} 匹配所有的标点符号字符
     * \\s 匹配任何空白字符
     */
    val cleanPattern =
        Pattern.compile("[A-Za-z0-9\\p{Punct}\\s\u3000\u2014\u2018\u2019\u201C\u201D\uFF08\uFF09\uFF1B\uFF1A\uFF1F\uFF01\u3001\uFF0C\u3002]")
}