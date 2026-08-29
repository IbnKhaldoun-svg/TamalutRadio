from pathlib import Path

p = Path("PROJECT_SPEC.md")
text = p.read_text()
old_decision = "- Google Drive OAuth/API setup: **deferred** until the Drive feature implementation phase."
new_decision = "- Google Drive music source: **implementation authorized** as the final original-plan feature, using modern Google AuthorizationClient + Drive Picker with least-privilege `drive.file`; credentials/tokens are never persisted by TamalutRadio."
if old_decision not in text:
    raise SystemExit("current-decision anchor missing")
text = text.replace(old_decision, new_decision, 1)

old_section = """## Google Drive

Deferred. Planned direction remains Drive REST API v3 + modern Google authorization, least-privilege scope compatible with the final picker UX, authenticated Media3 reads, and no paid quota/billing without explicit approval."""
new_section = """## Google Drive music source implementation contract

Google Drive is the final feature from the original TamalutRadio plan. Implement it as an additional source inside the existing `Musica` experience, alongside the already validated local SAF folder. The work is intentionally split into three independently verified sub-steps.

Architectural boundary for all three sub-steps:
- Add a minimal pure-Kotlin `:core:cloud` module containing provider-neutral cloud-music models plus a deliberately small `CloudMusicSource` contract. It must contain no Android, Google, OAuth, networking implementation, UI, Media3, credentials, or provider registry/framework.
- Add `:feature:drive` as the first and only concrete cloud provider. `GoogleDriveSource` implements the neutral contract. Future Mega/Dropbox support may implement the same small contract later, but no multi-provider selector/registry/plugin system is built now.
- Google Drive appears as a source choice within `Musica`; do not add a fifth bottom-navigation destination.
- The existing local SAF library remains fully functional and offline-capable. Drive-specific work must not broaden local storage permissions or modify local folder access semantics.

- [ ] **1/3 — Cloud abstraction + modern Google authorization foundation.** Create `:core:cloud` and `:feature:drive`. Google authorization must use Google Play services `AuthorizationClient` / `AuthorizationRequest`, never legacy `GoogleSignIn` APIs. The authorization request must use only `https://www.googleapis.com/auth/drive.file`, opt out of implicitly including previously granted scopes, use the Google Picker OAuth trigger, allow folder selection, and support explicit account selection/consent. Do not request `drive.readonly`, full `drive`, offline access, server auth codes, or refresh tokens.
- Use the Android OAuth installed-app identity for package `com.tamalut.radio` and the actual app signing certificate. Do not embed or require a Web OAuth client secret. TamalutRadio must not persist Google access tokens, refresh tokens, authorization codes, account passwords, or other Google credentials. Google Play services may manage its own authorization state/cache internally.
- Repository-publicity boundary: source code may contain non-secret protocol constants such as the `drive.file` scope and Drive REST endpoints. Android OAuth client identifiers, if ever required by an SDK/API, are public identifiers rather than secrets; nevertheless do not add a server/Web client secret or any credential that grants access by itself. Never commit the debug keystore.
- Sub-step 1 exposes testable authorization-request policy/state only; folder persistence, Drive REST scanning, Music-screen integration and Drive playback are explicitly deferred to 2/3 and 3/3.
- Verification 1/3: unit/structural tests must prove the provider-neutral contract has no Google dependency, `:feature:drive` uses `AuthorizationClient`, requests exactly `drive.file`, enables Picker folder selection, does not use legacy `GoogleSignIn`, requests no offline/server auth flow, and no OAuth secret/token literal is committed. A real GitHub Actions run must pass `:core:cloud:test`, `:feature:drive:testDebugUnitTest`, existing relevant regressions, and `:app:assembleDebug` with persistent debug signer v1 before clean promotion.

- [ ] **2/3 — Drive folder selection, persistence and audio scan.** Use the Android Google Picker flow to let the user select exactly one Drive folder. Persist only the selected Drive folder ID in `:core:preferences`; never persist a Google credential/token. `GoogleDriveSource` uses Drive REST API v3 under the runtime `drive.file` grant to enumerate the selected folder and its descendants, with pagination, deterministic ordering, audio MIME/extension filtering and recoverable handling of unreadable/removed items.
- Mirror the local UX inside `Musica`: source choice `Locale / Google Drive`, then `Collega Google Drive`/account authorization as needed, `Scegli cartella` or `Cambia cartella`, `Riscansiona`, loading/empty/error states and the discovered track list. Picker cancellation must leave the previous selection unchanged. Revoked authorization or a no-longer-accessible folder must produce a recoverable reconnect/reselect state rather than a crash.
- Network boundary 2/3: Drive requires connectivity. Detect lack of a usable network before REST scanning when practical and expose a clear offline state/message; never conflate Drive-offline with the always-available local library, and never delete the persisted folder ID merely because the network is temporarily unavailable.
- Verification 2/3: tests cover picker result parsing, persistence of folder ID only, cancellation, recursive/paginated filtering/order, revoked/inaccessible folder handling, offline scan policy and preservation of the local SAF path. A real signed CI build is required before promotion.

- [ ] **3/3 — Google Drive playback through the existing Media3 service.** Selecting a Drive track must install the selected Drive queue into the same process-shared `PlaybackController` / `TamalutPlaybackService` / `MediaLibrarySession` used by Radio and LOCAL; no second ExoPlayer, MediaBrowser, MediaSession or foreground playback service is allowed. Drive items use `MediaSourceType.DRIVE` and replace the current queue exactly as another music source would.
- Authenticated media reads must obtain authorization just in time and attach the current bearer token to Drive REST `files.get?alt=media` requests without persisting the token in DataStore, Room, MediaItem metadata, logs, source code or disk caches controlled by TamalutRadio. Prefer a narrow provider-neutral playback request/resolution seam in `:core:playback` rather than adding a Google dependency to the playback service.
- Preserve current mini-player, notification/lock-screen, Now Playing, overlay, Previous/Next and shared-state behavior. Local repeat/shuffle semantics must not regress; Drive queue repeat/shuffle behavior should match local music unless a concrete Drive limitation requires a documented exception. Radio fallback/live behavior remains isolated.
- During Drive playback, loss of connectivity or expired/revoked authorization must fail clearly and recoverably; a later retry/re-authorization may resume/reprepare the remote item, while LOCAL playback remains unaffected and available offline.
- Verification 3/3: tests cover Drive MediaItem/queue mapping, same-controller delegation, authenticated request resolution without token persistence, RADIO/LOCAL/DRIVE source transitions, offline/auth failure behavior and absence of a parallel player/session/service. Final CI must run all relevant core/radio/library/drive/app tests plus `:app:assembleDebug`, verify persistent debug signer v1 and publish the exact final `main` APK for physical Google Drive testing.

Google Cloud/OAuth setup contract:
- Use one Google Cloud project for TamalutRadio, enable **Google Drive API**, configure the **Google Auth Platform** consent/branding/audience, and create an **Android OAuth 2.0 client** for package `com.tamalut.radio` bound to each signing certificate that will legitimately run the app.
- Current persistent debug v1 certificate SHA-1 for the sideload/debug channel: `45:57:54:11:8C:EF:B9:B8:91:07:27:BF:C4:23:84:5C:5F:25:61:14` (SHA-256 remains `03225636d52d29f3886592d40747bc85c1c7ad2cafdf622a7d35d409fd928bd6`). A future production signing certificate requires its own Android OAuth client entry; do not reuse the debug key as the production key.
- Configure only `drive.file` for this feature. Do not add `drive.readonly` merely to browse the user’s whole Drive: Android Google Picker grants the app access to the folder/files the user explicitly selects, which is the intended least-privilege design.
- No paid Google Cloud quota/billing is authorized by this contract. If Google later requires billing or a materially broader/restricted scope for a necessary capability, stop and request explicit approval before changing scope/cost posture."""
if old_section not in text:
    raise SystemExit("Google Drive section anchor missing")
text = text.replace(old_section, new_section, 1)

decision_anchor = "## Decision log\n\n"
decision = """### 2026-08-29 — Google Drive music source authorized

Authorized the final original-plan feature as three verified sub-steps: (1) minimal provider-neutral `:core:cloud` contract plus Google `AuthorizationClient`/Picker foundation using only `drive.file`; (2) one selected Drive folder, persistence of folder ID only, recursive audio scan and `Locale / Google Drive` Music UX with explicit offline handling; (3) authenticated Drive playback through the existing single `TamalutPlaybackService`/MediaLibrarySession with no persisted Google credentials or parallel player. The repository is public, so no Web/server OAuth client secret, access token, refresh token, auth code or keystore may enter Git history.

"""
if decision_anchor not in text:
    raise SystemExit("decision log anchor missing")
text = text.replace(decision_anchor, decision_anchor + decision, 1)
p.write_text(text)
