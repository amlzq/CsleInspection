package com.amlzq.csle.inspection

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

class CsleBundle private constructor() {
    companion object {
        private const val BUNDLE: @NonNls String = "messages.CsleBundle"
        private val INSTANCE = DynamicBundle(CsleBundle::class.java, BUNDLE)

        @JvmStatic
        fun message(
            @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any
        ): @Nls String {
            return INSTANCE.getMessage(key, params)
        }

        @JvmStatic
        fun messagePointer(
            @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any
        ): Supplier<@Nls String> {
            return INSTANCE.getLazyMessage(key, params)
        }
    }
}