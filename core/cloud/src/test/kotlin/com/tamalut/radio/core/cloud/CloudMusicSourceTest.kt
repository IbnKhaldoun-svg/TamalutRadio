package com.tamalut.radio.core.cloud

import kotlin.test.Test
import kotlin.test.assertTrue

class CloudMusicSourceTest {
    @Test
    fun `cloud source remains an empty provider neutral interface`() {
        assertTrue(CloudMusicSource::class.java.isInterface)
        assertTrue(CloudMusicSource::class.java.declaredMethods.isEmpty())
    }
}
