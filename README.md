# NOVA PILL — Live Island for Android

A native Kotlin + Jetpack Compose Android app with a real `TYPE_APPLICATION_OVERLAY` island, Android media-session discovery, notification identity peeks, local LRC import/sync, and quick device utilities.

## Project setup

- Package: `app.novapill.android`
- Minimum SDK: Android 8.0 / API 26
- Compile / target SDK: API 36
- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- JDK: 17
- UI: Jetpack Compose 1.11.4 + Material 3 1.3.2
- Settings: Preferences DataStore

Dependency note: Compose 1.12.0+ requires compileSdk 37 and AGP 9.2+. This repository intentionally pins Compose 1.11.4 with AGP 8.13.2 and API 36 so the toolchain remains internally compatible. The versions are pinned explicitly instead of mixing an unverified newest BOM with an older Android Gradle Plugin.

## Build with GitHub Actions (phone-friendly)

1. Create a GitHub repository named `NOVA-PILL` (or use an empty Android repository).
2. Upload the **contents** of this project folder to the repository root. Do not put the project one folder deeper.
3. Commit the files to `main` (or `master`).
4. Open **Actions → NOVA PILL · Android CI**.
5. Open the successful run and download **NOVA-PILL-debug-APK** under Artifacts.

No Firebase, API key, paid hosting, signing key, or secret is required for a debug build.

### Gradle Wrapper note

The generation environment did not have network access to retrieve the official Gradle Wrapper JAR. The included `gradlew` is a pinned Gradle 8.13 bootstrap that uses an installed Gradle when available, or downloads the exact 8.13 distribution when network access is available. The GitHub workflow uses the managed Gradle 8.13 installation to generate and verify the standard `gradle-wrapper.jar` before running the build. The wrapper JAR is therefore generated on the GitHub runner, not shipped inside this source ZIP.

## First launch

1. Allow **Display over other apps** so NOVA PILL can place the island above other apps.
2. Enable **Notification access** to discover compatible Android media sessions and notification app names.
3. Allow app notifications when desired; this controls the visible ongoing session notification.
4. Shizuku and battery-optimization exemption are optional. The core app does not depend on Shizuku.
5. Return to NOVA PILL and refresh permission status, then press **Continue**.
6. Press **Start island** on the dashboard. Stop it from the dashboard or its ongoing notification.

## Implemented in this initial build

- First-run permission wizard with live permission checks.
- Native Compose dashboard, Studio and Settings screens.
- Original adaptive app icon.
- Black/crimson overlay pill built with `WindowManager.TYPE_APPLICATION_OVERLAY`.
- Collapsed/expanded morph with a restrained spring-like settle; uses status-bar and display-cutout safe insets with responsive width.
- Expanded controls are capability-gated; seek gestures are sent only when the active media session advertises seeking. Closing the panel collapses it without stopping playback or the overlay service.
- Android `MediaSessionManager` discovery through an enabled `NotificationListenerService`.
- Media metadata, player package, artwork where supplied, playback status, and capability-gated play/pause/previous/next controls.
- Mini visualizer only while playback is active; it is explicitly a playback-state visual, not audio analysis.
- Notification peek shows the source app name only and respects privacy/peek-duration settings.
- Local `.LRC` import through Storage Access Framework, multi-timestamp parsing, active-line selection and local playback-position interpolation.
- Timer and stopwatch based on monotonic elapsed time; the timer continues while the activity is hidden as long as Android keeps the app process alive.
- Flashlight through `CameraManager`, media volume through `AudioManager`, battery percentage from `BatteryManager`.
- Manual recording marker and Android 15+ screen-recording visibility callback for NOVA PILL's own activity only.
- DataStore-backed settings and unit tests for LRC parsing/synchronization.
- GitHub Actions unit-test, lint, debug-APK build, and artifact upload workflow.

## Current limitations — do not assume these are implemented

- There is **no licensed online lyrics provider** connected. Import an LRC file you have permission to use. The app does not scrape lyrics or manufacture timed lyrics.
- Android screen-recording visibility is scoped to the registering app's own activities. It is not a universal detector for recordings of other apps.
- Weather has a saved manual city field but no weather data provider connected; no made-up weather is shown.
- Shizuku is deliberately optional and is not integrated into this initial build.
- The current overlay does not yet include long-press repositioning, per-app allowlists, hide-in-fullscreen detection, keyboard-aware hiding, or independent landscape layout tuning. Its Canvas-based overlay is a single accessibility node rather than separate TalkBack nodes for each drawn control.
- Multiple media sessions follow Android's active-session ordering; a dedicated player-priority picker is not included yet. Notification peeks are single-item rather than a queued multi-notification stack.
- The manual recording marker is a user-controlled status dot; a separate elapsed HH:MM:SS manual recording timer and pause/reset controls are not included yet.
- The countdown continues while the process is alive; it is not yet backed by a dedicated timer foreground service or reboot restoration.
- Battery/OS/OEM background policies can still stop a service. Force-stop cannot be worked around.
- `specialUse` foreground-service use is declared for the user-started overlay session. Store distribution still requires review against current Play policy and device behavior.
- Only source-level/unit test assets are included here. No Android device or emulator was available in the generation environment, so no device compatibility claim is made.

## Manual test checklist

- Install debug APK on Android 8–16.
- Verify the system overlay permission page and notification-listener page open correctly.
- Start/stop the island, then test collapsed/expanded interaction over a different app.
- Test Spotify and another player that exposes a standard Android media session.
- Verify missing metadata/artwork fall back cleanly and unavailable transport actions are disabled.
- Pause/resume/skip tracks and verify the mini visualizer stops when paused.
- Import an LRC file, then test seek, pause, resume, and track switching.
- Test torch behavior on a device with flash hardware and verify an unsupported error on devices without flash.
- Test volume slider, timer, stopwatch, notification peek, manual recording marker, rotation and reduced-motion setting.
- Check Android battery saver and OEM background restrictions.

## Local build (computer with Android SDK)

```bash
chmod +x ./gradlew
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

A signed release build is not configured. Never commit a keystore or signing password to this repository.
