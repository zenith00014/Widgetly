# Rush

Rush is a mobile-first entertainment app built with Kotlin, Jetpack Compose and AndroidX Media3. It brings curated music, videos, a concise news briefing and a personal library into one calm interface. Movie listings and playback are intentionally excluded until a suitable free source is selected.

## Current experience

- **Home:** a featured pick, music shelf, recent playback and a finite set of recommendations.
- **Discover:** local catalogue filtering by title and creator, with Music and Videos categories.
- **News:** expandable fictional briefing cards with publisher, source type, read time and selection rationale.
- **Library:** locally saved tracks/videos and playback history.
- **Players:** separate audio-first and video playback modes.
- Dark and lilac light themes.

All catalogue artwork and editorial content are fictional placeholders. Discover currently filters the bundled sample catalogue; it does not yet perform live YouTube catalogue search.

## YouTube playback

Rush uses a LibreTube-derived NewPipeExtractor integration to resolve a YouTube URL or 11-character video ID. AndroidX Media3 plays the resolved audio or video stream. This is URL-driven playback, not a complete YouTube search/browse integration. Stream availability depends on YouTube and extractor compatibility and can change without notice.

The playback integration adapts the extraction approach used by [LibreTube](https://github.com/Libre-tube/LibreTube), licensed under GPL-3.0-or-later. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and `LICENSES/LibreTube-GPL-3.0-or-later.txt`. Redistributed builds must comply with the GPL, including providing corresponding source.

## Build locally

Requirements: JDK 17, Android SDK 35 and Gradle 8.9 (or Android Studio).

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Build on GitHub

Push to `main` or open the **Actions** tab and run **Build Android APK**. Download the `widgetly-debug-apk` artifact from the completed workflow.
