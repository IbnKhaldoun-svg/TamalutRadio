package com.tamalut.radio.core.cloud

/**
 * Provider-neutral marker for a possible future cloud music source.
 *
 * No cloud provider is active in TamalutRadio. Concrete provider contracts,
 * credentials, networking, pickers and playback behavior must be introduced only
 * by a future explicitly approved provider implementation.
 */
interface CloudMusicSource
