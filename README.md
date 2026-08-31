# TamalutRadio

TamalutRadio is a native Android application for listening to internet radio and local music selected through Android Storage Access Framework. The project is designed around reliable background playback, favorites-first radio browsing, Android media controls, Android Auto compatibility, and a lightweight Atlas-inspired visual identity.

The application is distributed as an APK for direct sideloading. Google Play distribution is not required.

## Current development status

The project is currently in the foundation phase.

Completed:

- Gradle 9.5.0 wrapper
- root Gradle Kotlin DSL configuration
- Android application module `:app`
- Kotlin + Jetpack Compose bootstrap
- `minSdk 26`, `compileSdk 37`, `targetSdk 37`
- AGP 9.3.0 / JDK 17 baseline
- application ID `com.tamalut.radio`
- minimal launchable `MainActivity`
- Atlas Signal launcher icon, including adaptive and Android 13+ monochrome/themed resources
- successful real `./gradlew :app:assembleDebug` build on GitHub Actions

Next foundation work:

- `:core:designsystem` with Atlas Night light/dark Material 3 design tokens
- remaining `core/*` modules
- feature modules
- Media3 1.11.0 playback service and media session
- radio catalog, favorites and fallback streams
- local music via Android Storage Access Framework
- release signing and automatic GitHub Releases publishing

See [`PROJECT_SPEC.md`](PROJECT_SPEC.md) for the complete approved architecture, scope and implementation decisions.

## Visual identity

The approved application identity is **Atlas Signal**, variant 3, with generous adaptive-icon safe-area margins.

The approved branding source files are stored in:

`assets/branding/`

Android launcher resources are generated from those approved sources without changing their proportions.

## Building locally

Requirements:

- JDK 17
- Android SDK Platform 37

Build a debug APK with:

```bash
./gradlew :app:assembleDebug
```

The generated APK is placed under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Installing an APK from GitHub Releases

Official project APKs will be published on the repository's **Releases** page once the release-signing pipeline is enabled.

On an Android phone:

1. Open this repository on GitHub.
2. Open **Releases**.
3. Select the desired TamalutRadio release.
4. Download the `.apk` file attached to the release.
5. Open the downloaded APK from your browser or file manager.
6. If Android blocks the installation, open the prompted **Install unknown apps** setting and allow installation for the browser or file manager you are using.
7. Return to the APK and choose **Install**.
8. After installation, you may disable the **Install unknown apps** permission again if desired.

Future release APKs will use the same dedicated release signing key so Android can install newer TamalutRadio versions as updates over previous release builds.

## Development debug APKs

During development, temporary debug APKs may be exposed as GitHub Actions artifacts for device testing. These are development builds rather than official releases and may expire automatically after their configured artifact retention period.

## Planned technical architecture

The approved architecture is modular and will include:

- `:app`
- `:core:model`
- `:core:data`
- `:core:database`
- `:core:preferences`
- `:core:playback`
- `:core:designsystem`
- `:core:cloud` (empty provider-neutral future seam)
- `:feature:radio`
- `:feature:library`
- `:feature:nowplaying`
- `:feature:settings`
- `:feature:widget`

Playback is planned around AndroidX Media3 / ExoPlayer with `MediaLibraryService` and `MediaLibrarySession`, with Room and DataStore handling their separate persistence responsibilities.

## Distribution principles

TamalutRadio is intended to remain usable without mandatory paid infrastructure, paid subscriptions, or Play Store distribution. APK sideloading from GitHub Releases is the primary distribution path.
