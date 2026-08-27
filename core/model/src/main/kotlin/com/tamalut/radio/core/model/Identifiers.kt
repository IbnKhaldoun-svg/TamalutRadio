package com.tamalut.radio.core.model

@JvmInline
value class StationId(val value: String) {
    init {
        require(value.isNotBlank()) { "StationId must not be blank" }
    }
}

@JvmInline
value class MediaId(val value: String) {
    init {
        require(value.isNotBlank()) { "MediaId must not be blank" }
    }
}
