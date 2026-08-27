# TamalutRadio — PROJECT_SPEC.md

Single source of truth for TamalutRadio. Update this file before code changes.

## Current decisions

- UI: Atlas Night base + Material 3 ergonomics + restrained Sahara Pulse accents.
- Theme: Follow system by default, with Light / Dark / Follow system override.
- Logo/Icon: **approved — Atlas Signal**.
- Icon proportions: generous safe-area margins based on refinement #3; verified for circle, squircle and rounded-square adaptive masks.
- Android: Kotlin + Jetpack Compose; minSdk 26; compileSdk 37; targetSdk 37.
- Playback: Media3 1.11.0 with MediaLibraryService / MediaLibrarySession and ExoPlayer-native audio focus.
- Persistence: Room + DataStore.
- Home widget: Jetpack Glance.
- Google Drive setup: deferred until the Drive feature is implemented.
- Radio Tachlit Sous Massa: placeholder remains until a reliable identifying reference is provided.
- Approved extra features: Recently played; automatic fallback stream URLs.

## Approved logo assets

- TamalutRadio_icon_master_1024.png
- TamalutRadio_adaptive_foreground_1024.png
- TamalutRadio_adaptive_background_1024.png
- TamalutRadio_monochrome_1024.png
- TamalutRadio_adaptive_mask_preview.png

## Initial radio catalog

- Radio Azawan
- Radio Plus Agadir
- HIT RADIO Maroc
- Radio Mars
- Aswat FM
- MFM Radio
- Medina FM Amazigh
- Radio Italia Solo Musica Italiana
- Radio Sportiva

## Next implementation step

Create the Gradle / Jetpack Compose project foundation and repository module structure before feature code.