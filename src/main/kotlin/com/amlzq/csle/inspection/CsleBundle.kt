package com.amlzq.csle.inspection

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.PropertyKey

import java.util.function.Supplier

object CsleBundle {
    @NonNls
    private const val BUNDLE: String = "messages.CsleBundle"

    private val INSTANCE: DynamicBundle = DynamicBundle(CsleBundle::class.java, BUNDLE)

    @NotNull
    @Nls
    fun message(@NotNull @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: @NotNull Any?): String {
        return INSTANCE.getMessage(key, *params)
    }

    @NotNull
    fun messagePointer(
        @NotNull @PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: @NotNull Any?
    ): Supplier<String> {
        return INSTANCE.getLazyMessage(key, *params)
    }
}
