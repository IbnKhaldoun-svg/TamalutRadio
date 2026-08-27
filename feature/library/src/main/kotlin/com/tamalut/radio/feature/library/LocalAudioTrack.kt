package com.tamalut.radio.feature.library

import com.tamalut.radio.core.model.MediaId

data class LocalAudioTrack(
    val id: MediaId,
    val title: String,
    val contentUri: String,
    val mimeType: String?,
) {
    init {
        require(title.isNotBlank()) { "Local audio title must not be blank" }
        require(contentUri.isNotBlank()) { "Local audio content URI must not be blank" }
    }
}
