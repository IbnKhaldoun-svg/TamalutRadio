package com.tamalut.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LazyOverlayHostSlotTest {
    @Test
    fun constructionAndExistingDoNotCreateHost() {
        var createCount = 0
        val slot = LazyOverlayHostSlot {
            createCount += 1
            Any()
        }

        assertNull(slot.existing())
        assertEquals(0, createCount)
    }

    @Test
    fun firstExplicitGetCreatesOnceAndReusesHost() {
        var createCount = 0
        val expected = Any()
        val slot = LazyOverlayHostSlot {
            createCount += 1
            expected
        }

        val first = slot.getOrCreate()
        val second = slot.getOrCreate()

        assertSame(expected, first)
        assertSame(first, second)
        assertEquals(1, createCount)
    }

    @Test
    fun failedCreationRemainsRetryableWithoutCachingFailure() {
        var createCount = 0
        val expected = Any()
        val slot = LazyOverlayHostSlot {
            createCount += 1
            if (createCount == 1) null else expected
        }

        assertNull(slot.getOrCreate())
        assertSame(expected, slot.getOrCreate())
        assertEquals(2, createCount)
    }
}
