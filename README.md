# Widgetly for Android

Widgetly is an Android media and discovery app built with Kotlin, AndroidX Media3 and a local HTML/CSS/JavaScript interface hosted in an Android WebView. Native Android components handle media playback, downloads, notifications, background playback, immersive video and Picture-in-Picture.

## Current product scope

- YouTube video search and playback with native playback controls, captions where available and immersive full-screen viewing
- YouTube Music search and playback, history, lyrics and taste-based suggestions derived from followed musicians and listening history
- Source follows for YouTube channels, news publishers and musicians
- A two-mode Home feed: For You and Following
- User-selected topics across learning, culture, entertainment and low-focus/viral categories
- For You recommendations limited to YouTube channels the user explicitly includes in the productive-channel pool
- News discovery and publisher-aware suggestions using topic relevance, reporting/context signals and followed publishers
- Broad search across videos, music, news and YouTube channels; recommendation scoring is not applied as a search restriction
- Public video playlists, local playlists, watch/listening history and offline downloads
- Media3 background playback, notifications, app-private downloads and Picture-in-Picture

Movies and TV are temporarily hidden from the app while a permitted, reliable movie source is evaluated. They are not part of the current build.

## Recommendation behavior

For You only considers channels that the user has selected and explicitly added to the For You pool. Channel selection is separate from following: users can follow a channel without adding it to For You. Selected topics help rank videos across different subject areas, so productive recommendations are not limited to one discipline. Low-focus topics such as memes, pranks and viral clips remain available in topic selection and unrestricted search, but receive a ranking penalty in recommendation feeds.

Following collects content from sources the user follows. Music suggestions use followed musicians and recent listening history. News suggestions use selected topics and followed publishers, with contextual/reporting signals prioritized over low-information viral terms. Search remains broader than recommendation feeds and is not filtered by the selected topics or productive-channel pool.

## Project layout

- `app/src/main/java/com/widgetly/app/`: Android application, playback, download, search and preference integration
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

A successful build verifies compilation and packaging only. It does not verify live YouTube extraction, stream playback, news-source coverage, recommendation relevance, offline download integrity or device-specific behavior; those require testing on physical Android devices.

## Compatibility and licensing

YouTube extraction uses unofficial endpoints and NewPipeExtractor, so upstream service changes may affect compatibility. News discovery currently reads public Google News RSS search feeds. Use offline downloads only for content and methods you are permitted to keep offline.

The playback implementation adapts LibreTube/NewPipeExtractor code under GPL-3.0-or-later. MP3 conversion uses a community-maintained FFmpegKit-compatible audio package under LGPL-3.0. Read `THIRD_PARTY_NOTICES.md` and the included license texts before distributing builds.
