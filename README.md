# Widgetly for Android

Widgetly is an Android entertainment app built with Kotlin, Jetpack Compose, AndroidX Media3 and a local HTML/CSS/JavaScript interface hosted in an Android WebView. Native Android components handle media playback, downloads, notifications, background playback and Picture-in-Picture.

## Current product scope

- YouTube video and YouTube Music search and playback
- Channel subscriptions and latest-upload feeds
- Public video playlists and music album browsing
- Local playlists, playback queue and watch/listening history
- Media3 background playback and Picture-in-Picture
- Adaptive streaming where supported by the resolved source
- App-private offline downloads with progress, resume and storage management
- Optional on-device MP3 conversion for audio downloads

Movies and TV series remain out of scope until a suitable, permitted source is selected. News integration is not yet present in this GitHub branch; do not confuse any earlier local prototype with code committed here.

## Project layout

- `app/src/main/java/com/widgetly/app/`: Android application, playback, download, search and persistence integration
- `app/src/main/java/com/github/libretube/`: LibreTube-derived adaptive playback implementation
- `app/src/main/assets/`: local WebView application and proof-of-origin helper assets
- `app/src/main/proto/`: protocol schemas used by the adaptive playback implementation
- `app/src/main/res/`: Android manifest resources, app label and themes
- `.github/workflows/`: CI build and APK artifact workflow
- `LICENSES/` and `THIRD_PARTY_NOTICES.md`: third-party license texts and attribution

The WebView asset is part of the current application UI and is not safe to delete as an unused prototype. Likewise, the LibreTube-derived player and protobuf files are runtime dependencies, not disposable legacy files.

## Build locally

Requirements: JDK 17, Android SDK 35 and Gradle 8.9.

```bash
gradle --no-daemon :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Build on GitHub

The **Build Widgetly Android APK** workflow runs on pushes to the supported branches and can also be started manually from GitHub Actions. Download the `widgetly-debug-apk` artifact from a successful run.

A successful build verifies compilation and packaging only. It does not verify live YouTube extraction, stream playback, offline download integrity or device-specific behavior; those require testing on physical Android devices.

## Compatibility and licensing

YouTube extraction uses unofficial endpoints and NewPipeExtractor, so upstream service changes may affect compatibility. Use offline downloads only for content and methods you are permitted to keep offline.

The playback implementation adapts LibreTube/NewPipeExtractor code under GPL-3.0-or-later. MP3 conversion uses a community-maintained FFmpegKit-compatible audio package under LGPL-3.0. Read `THIRD_PARTY_NOTICES.md` and the included license texts before distributing builds.
