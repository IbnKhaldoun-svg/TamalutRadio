# TamalutRadio

TamalutRadio is a native Android app for internet radio and local music, built around a single AndroidX Media3 playback service/session, reliable background playback, favorites/search, Android media controls, and an Atlas-inspired UI.

## Current canonical test build

Use this baseline for every new install and physical/regression test unless `PROJECT_SPEC.md` explicitly replaces it:

- Runtime: `017229b25041a6aa7603a766163693d75145bb4b` (`feat: show current title in floating overlay`)
- Prerelease: `debug-20260910-131003-017229b`
- APK: `TamalutRadio-debug-017229b.apk`
- Size: `23844741` bytes
- APK SHA-256: `52b00c2e1cc79be316b28231eca24f1736afe77f910d192d0dbb6b11ba59ef99`
- Debug signer SHA-256: `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`
- Release: https://github.com/IbnKhaldoun-svg/TamalutRadio/releases/tag/debug-20260910-131003-017229b
- Direct APK: https://github.com/IbnKhaldoun-svg/TamalutRadio/releases/download/debug-20260910-131003-017229b/TamalutRadio-debug-017229b.apk

Older debug prereleases are retained as historical evidence and are marked **SUPERSEDED FOR NEW PHYSICAL TESTING**.

## Current product scope

TamalutRadio currently includes the internet-radio catalog and categories (including Sport), favorites and search, user-created radios with assignable categories, local Music through Android Storage Access Framework, shared Media3 playback/background/notification controls, persistent mini-player and Now Playing, floating playback overlay with current Radio/Music title, Stop/exit policy, Sleep Timer, Backup/Restore, and the Android Auto media-discovery/browse foundation.

Google Drive is retired from current product scope. `PROJECT_SPEC.md` is the authoritative source for architecture, accepted behavior, validation evidence, physical gates, and roadmap state.

## Build locally

Requirements: JDK 17 and Android SDK Platform 37.

```bash
./gradlew :app:assembleDebug
```

Local output: `app/build/outputs/apk/debug/app-debug.apk`.

## Distribution

Development test APKs are published as permanent GitHub prerelease assets rather than temporary Actions artifacts. The publisher builds an exact supplied runtime SHA and records APK SHA-256 plus debug-signer identity in the Release notes.
