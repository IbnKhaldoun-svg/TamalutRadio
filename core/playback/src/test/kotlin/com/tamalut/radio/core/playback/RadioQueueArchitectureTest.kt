package com.tamalut.radio.core.playback

import java.nio.file.Path
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioQueueArchitectureTest {
    @Test
    fun controllerInstallsRealRadioPlaylistWithStartIndexAndWrapPolicy() {
        val source = Path.of("src/main/kotlin/com/tamalut/radio/core/playback/SharedPlaybackController.kt").readText()
        val queue = source.substringAfter("override fun playRadioQueue(").substringBefore("override fun playLocal(")
        assertTrue(queue.contains("connectedBrowser.setMediaItems("))
        assertTrue(queue.contains("stations.map(RadioMediaItemFactory::create)"))
        assertTrue(queue.contains("startIndex"))
        assertTrue(queue.contains("RadioQueuePolicy.repeatModeForQueueSize(stations.size)"))
        assertTrue(queue.contains("connectedBrowser.shuffleModeEnabled = false"))
    }

    @Test
    fun fallbackRetryReplacesOnlyCurrentQueueItem() {
        val service = Path.of("src/main/kotlin/com/tamalut/radio/core/playback/TamalutPlaybackService.kt").readText()
        val retry = service.substringAfter("is RadioFallbackDecision.Retry -> {").substringBefore("is RadioFallbackDecision.Exhausted -> {")
        assertTrue(retry.contains("currentMediaItemIndex"))
        assertTrue(retry.contains("replaceMediaItem("))
        assertTrue(retry.contains("seekToDefaultPosition(currentIndex)"))
        assertFalse(retry.contains("setMediaItem("))
        assertFalse(retry.contains("clearMediaItems("))
    }

    @Test
    fun liveReconnectPreservesPlaylistAndCurrentIndex() {
        val service = Path.of("src/main/kotlin/com/tamalut/radio/core/playback/TamalutPlaybackService.kt").readText()
        val reconnect = service.substringAfter("private fun reconnectCurrentRadioAtLiveEdge(").substringBefore("override fun onGetSession")
        assertTrue(reconnect.contains("currentMediaItemIndex"))
        assertTrue(reconnect.contains("seekToDefaultPosition(currentIndex)"))
        assertFalse(reconnect.contains("setMediaItem("))
        assertFalse(reconnect.contains("clearMediaItems("))
    }
}
