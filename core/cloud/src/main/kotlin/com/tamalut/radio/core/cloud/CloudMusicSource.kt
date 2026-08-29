package com.tamalut.radio.core.cloud

import com.tamalut.radio.core.model.MediaId

@JvmInline
value class CloudProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Cloud provider ID must not be blank" }
    }
}

@JvmInline
value class CloudFolderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Cloud folder ID must not be blank" }
    }
}

data class CloudMusicTrack(
    val mediaId: MediaId,
    val remoteId: String,
    val title: String,
    val mimeType: String? = null,
) {
    init {
        require(remoteId.isNotBlank()) { "Cloud remote ID must not be blank" }
        require(title.isNotBlank()) { "Cloud track title must not be blank" }
    }
}

/**
 * Minimal provider-neutral boundary for cloud-backed music libraries.
 *
 * Provider authorization, pickers, networking and playback authentication stay in
 * provider/features and are intentionally not modeled here.
 */
interface CloudMusicSource {
    val providerId: CloudProviderId

    suspend fun listMusic(folderId: CloudFolderId): List<CloudMusicTrack>
}
