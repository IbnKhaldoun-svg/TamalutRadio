package com.tamalut.radio.feature.drive

import com.tamalut.radio.core.cloud.CloudFolderId
import com.tamalut.radio.core.cloud.CloudMusicSource
import com.tamalut.radio.core.cloud.CloudMusicTrack
import com.tamalut.radio.core.cloud.CloudProviderId

fun interface GoogleDriveFolderReader {
    suspend fun listMusic(folderId: CloudFolderId): List<CloudMusicTrack>
}

/**
 * First concrete CloudMusicSource. The Drive REST reader is intentionally injected so
 * sub-step 1 establishes the provider boundary without implementing folder scanning yet.
 */
class GoogleDriveSource(
    private val folderReader: GoogleDriveFolderReader,
) : CloudMusicSource {
    override val providerId: CloudProviderId =
        CloudProviderId(GoogleDriveAuthorizationPolicy.PROVIDER_ID)

    override suspend fun listMusic(folderId: CloudFolderId): List<CloudMusicTrack> =
        folderReader.listMusic(folderId)
}
