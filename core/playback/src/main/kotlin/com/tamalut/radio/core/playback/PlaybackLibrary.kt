package com.tamalut.radio.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

internal object PlaybackLibrary {
    const val ROOT_ID = "tamalut_root"

    val rootItem: MediaItem
        get() = MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("TamalutRadio")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()
}
