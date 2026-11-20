# Rush

A mobile-first entertainment app inside the Widgetly Android project. Rush is designed around finite, curated shelves rather than an endless feed.

## Experience
- Four destinations: Home, Discover, News, and Library.
- Curated music, movie, and video shelves with search and type filters.
- YouTube audio and video playback: paste a YouTube URL or 11-character video ID in the player, choose AUDIO or VIDEO, and load the stream.
- Playback streams are resolved with NewPipeExtractor using the extraction approach adapted from LibreTube, then played with AndroidX Media3.
- Save-for-later and recently played collections, persisted locally.
- Aubergine dark theme with magenta/amber accents and phosphor-mint details; optional lilac light theme.
- Original gradient artwork generated in Compose; no remote image assets required.

## Build locally
Requirements: JDK 17, Android SDK 35, and Gradle 8.9 (or Android Studio).

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Build on GitHub
Push to `main` or open the **Actions** tab and run **Build Android APK**. Download the `widgetly-debug-apk` artifact from the completed workflow.

## Licensing and playback notes
This playback integration incorporates/adapts GPL-3.0-or-later material from [LibreTube](https://github.com/Libre-tube/LibreTube). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [the included GPL license](LICENSES/LibreTube-GPL-3.0-or-later.txt). Redistributed builds must comply with the GPL, including providing corresponding source.

LibreTube's extraction path targets YouTube. This player does not provide Spotify or general multi-platform music streaming. Stream availability depends on YouTube and extractor compatibility; YouTube may change its delivery protocol.

The home-screen Quick Note and Retro Clock widgets from Widgetly remain in the project.
