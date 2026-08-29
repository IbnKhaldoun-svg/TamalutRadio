package com.tamalut.radio

internal class LazyOverlayHostSlot<T>(
    private val factory: () -> T?,
) {
    private var value: T? = null

    fun existing(): T? = value

    fun getOrCreate(): T? {
        value?.let { return it }
        return factory()?.also { value = it }
    }
}
