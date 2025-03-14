package com.amlzq.csle.inspection

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

@NonNls
private const val BUNDLE = "messages.CsleBundle"

internal object CsleBundle {
    private val INSTANCE = DynamicBundle(CsleBundle::class.java, BUNDLE)

    fun message(
        key: @PropertyKey(resourceBundle = BUNDLE) String, vararg params: Any
    ): String {
        return INSTANCE.getMessage(key, *params)
    }

    fun lazyMessage(
        @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any
    ): Supplier<String> {
        return INSTANCE.getLazyMessage(key, *params)
    }
}