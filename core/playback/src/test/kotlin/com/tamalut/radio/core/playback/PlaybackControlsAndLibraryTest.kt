package com.tamalut.radio.core.playback

import com.tamalut.radio.core.data.InitialRadioCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackControlsAndLibraryTest {
    @Test
    fun mediaControlsArePlayPauseNextThenStopExit() {
        assertEquals(
            listOf(
                PlaybackControlAction.PLAY_PAUSE,
                PlaybackControlAction.NEXT,
                PlaybackControlAction.STOP_EXIT,
            ),
            PlaybackControls.definitions.map { it.action },
        )
    }

    @Test
    fun stopExitExposureRequiresSystemAutoOrTrustedController() {
        assertFalse(PlaybackControls.shouldExposeStopExit(false, false, false, false))
        assertTrue(PlaybackControls.shouldExposeStopExit(true, false, false, false))
        assertTrue(PlaybackControls.shouldExposeStopExit(false, true, false, false))
        assertTrue(PlaybackControls.shouldExposeStopExit(false, false, true, false))
        assertTrue(PlaybackControls.shouldExposeStopExit(false, false, false, true))
    }

    @Test
    fun libraryIsBrowsableRootThenCategoryThenPlayableStations() {
        assertTrue(PlaybackLibrary.rootNode.browsable)
        assertFalse(PlaybackLibrary.rootNode.playable)

        val rootChildren = PlaybackLibrary.children(PlaybackLibrary.ROOT_ID, page = 0, pageSize = 20)
        assertEquals(listOf(PlaybackLibrary.testRadioNode), rootChildren)
        assertTrue(rootChildren.single().browsable)
        assertFalse(rootChildren.single().playable)

        val stations = PlaybackLibrary.children(PlaybackLibrary.TEST_RADIO_ID, page = 0, pageSize = 20)
        assertEquals(3, stations.size)
        assertTrue(stations.all { !it.browsable && it.playable && it.station != null })
        assertEquals(PlaybackTestCatalog.stations.map(PlaybackTestCatalog::mediaIdFor), stations.map { it.mediaId })
    }

    @Test
    fun directStationLookupAndPagingAreDeterministic() {
        val hitRadioId = "radio:hit-radio-maroc"
        assertEquals("HIT RADIO Maroc", PlaybackLibrary.nodeById(hitRadioId)?.title)
        assertNotNull(PlaybackLibrary.nodeById(PlaybackLibrary.ROOT_ID))
        assertNull(PlaybackLibrary.nodeById("radio:missing"))

        val secondPage = PlaybackLibrary.children(PlaybackLibrary.TEST_RADIO_ID, page = 1, pageSize = 2)
        assertEquals(listOf("radio:radio-mars"), secondPage.map { it.mediaId })
    }

    @Test
    fun selectingHitRadioBuildsOrderedPlaylistAndNextTargetsRadioMars() {
        val resolved = PlaybackTestCatalog.resolve("radio:hit-radio-maroc")
        assertNotNull(resolved)
        resolved!!
        assertEquals(1, resolved.startIndex)
        assertEquals(
            listOf("radio-azawan", "hit-radio-maroc", "radio-mars"),
            resolved.stations.map { it.id.value },
        )
        assertEquals("radio-mars", resolved.stations[resolved.startIndex + 1].id.value)
        assertNull(PlaybackTestCatalog.resolve("radio:unknown"))
    }

    @Test
    fun temporaryPlaybackCatalogMatchesCoreDataSeedExactly() {
        val seedById = InitialRadioCatalog.stations.associateBy { it.id }
        val matchedSeedStations = PlaybackTestCatalog.stations.map { testStation ->
            seedById.getValue(testStation.id)
        }
        assertEquals(PlaybackTestCatalog.stations, matchedSeedStations)
        assertEquals("radio:radio-azawan", PlaybackTestCatalog.RADIO_AZAWAN_MEDIA_ID)
    }
}
