package com.amlzq.csle.inspection

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

class CsleBundle : DynamicBundle("messages.CsleBundle") {
    companion object {
        private val INSTANCE = CsleBundle()

        @JvmStatic
        fun message(key: String, vararg params: Any): @Nls String {
            return INSTANCE.getMessage(key, params)
        }

        @JvmStatic
        fun messagePointer(key: String, vararg params: Any): Supplier<@Nls String> {
            return INSTANCE.getLazyMessage(key, params)
        }
    }
}