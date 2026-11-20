# Rush

Rush is the entertainment experience inside the Widgetly Android project. It combines a social-style home timeline with dedicated YouTube video, audio-first music, news and library sections. The feed is designed to feel familiar without becoming an endless scroll.

## Experience
- **Home:** For You and Following timelines, creator-style posts, media attachments, likes, saves and a local post composer.
- **Videos:** A dedicated video shelf with search and a YouTube playback entry point.
- **Music:** An audio-first shelf with a Spotify-inspired listening screen and an AUDIO ONLY playback mode for YouTube streams.
- **News:** A finite set of expandable story summaries.
- **Library:** Saved tracks/videos and recently played items, stored locally.
- Dark and light themes.
- Existing Widgetly Quick Note and Retro Clock widgets remain available.

## Movie content
Movie sources and movie listings are intentionally not included yet. They can be added after a source is selected.

## Playback
Paste a YouTube URL or 11-character video ID in the player, then choose AUDIO ONLY or VIDEO. NewPipeExtractor resolves the stream and AndroidX Media3 plays it. Audio-only mode hides the video surface and presents the track artwork.

Playback is currently link-driven: the Home, Videos and Music screens are the Rush UI layer, not a live YouTube catalogue or Spotify catalogue. Search filters the current in-app picks; it does not yet search YouTube remotely. Stream availability depends on YouTube and extractor compatibility.

This playback integration incorporates/adapts GPL-3.0-or-later material from [LibreTube](https://github.com/Libre-tube/LibreTube). See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [the included GPL license](LICENSES/LibreTube-GPL-3.0-or-later.txt). Redistributed builds must comply with the GPL, including providing corresponding source.

## Build locally
Requirements: JDK 17, Android SDK 35, and Gradle 8.9 (or Android Studio).

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Build on GitHub
Push to `main` or open the **Actions** tab and run **Build Android APK**. Download the `widgetly-debug-apk` artifact from the completed workflow.
