package com.tamalut.radio.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.tamalut.radio.core.model.RadioStation

internal data class PlaybackLibraryNode(
    val mediaId: String,
    val title: String,
    val browsable: Boolean,
    val playable: Boolean,
    val station: RadioStation? = null,
)

internal object PlaybackLibrary {
    const val ROOT_ID = "tamalut_root"
    const val TEST_RADIO_ID = "tamalut_test_radio"

    val rootNode = PlaybackLibraryNode(
        mediaId = ROOT_ID,
        title = "TamalutRadio",
        browsable = true,
        playable = false,
    )

    val testRadioNode = PlaybackLibraryNode(
        mediaId = TEST_RADIO_ID,
        title = "Radio di test",
        browsable = true,
        playable = false,
    )

    val stationNodes: List<PlaybackLibraryNode> = PlaybackTestCatalog.stations.map { station ->
        PlaybackLibraryNode(
            mediaId = PlaybackTestCatalog.mediaIdFor(station),
            title = station.name,
            browsable = false,
            playable = true,
            station = station,
        )
    }

    val rootItem: MediaItem
        get() = toMediaItem(rootNode)

    fun nodeById(mediaId: String): PlaybackLibraryNode? = when (mediaId) {
        ROOT_ID -> rootNode
        TEST_RADIO_ID -> testRadioNode
        else -> stationNodes.firstOrNull { it.mediaId == mediaId }
    }

    fun children(parentId: String, page: Int, pageSize: Int): List<PlaybackLibraryNode> {
        if (page < 0 || pageSize <= 0) return emptyList()
        val allChildren = when (parentId) {
            ROOT_ID -> listOf(testRadioNode)
            TEST_RADIO_ID -> stationNodes
            else -> emptyList()
        }
        val fromIndex = page * pageSize
        if (fromIndex >= allChildren.size) return emptyList()
        return allChildren.drop(fromIndex).take(pageSize)
    }

    fun toMediaItem(node: PlaybackLibraryNode): MediaItem = MediaItem.Builder()
        .setMediaId(node.mediaId)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(node.title)
                .setIsBrowsable(node.browsable)
                .setIsPlayable(node.playable)
                .build(),
        )
        .build()
}
