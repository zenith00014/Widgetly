# Rush

Rush is an Android-only video and music streaming client built with Kotlin, Jetpack Compose and AndroidX Media3.

## Product scope

- YouTube video search and playback
- YouTube Music search and audio playback
- Channel search, subscriptions and latest uploads
- Local video/music playlists and queue playback
- Watch and listening history
- Background audio/video playback through a Media3 media session
- Playback quality, speed and available captions
- App-private offline downloads, offline playback, progress, cancel, resume and storage management

Out of scope for this phase: Movies, TV series, Shorts, Trending and custom recommendation algorithms. The interface remains functional and minimal until the streaming system is reliable; visual redesign comes later.

## Current implementation

The development branch includes live YouTube and YouTube Music search adapters, channel subscriptions and latest-upload feeds, local playlists, ordered playback history, Media3 background playback, playback controls, caption-track extraction, and a WorkManager-based private download library.

Downloads use the source's available stream format and extension (for example, M4A or WebM where supplied). Rush does not currently transcode downloaded audio into MP3. Video/audio formats and selectable qualities depend on what the source exposes. Interrupted transfers resume from partial files where the server supports HTTP range requests; a manual resume re-resolves the source stream because signed URLs may expire.

Data is currently stored locally using SharedPreferences and app-private files. It is not synced to a YouTube account. Search/extraction relies on unofficial YouTube web endpoints and NewPipeExtractor, so service changes can break compatibility. A successful CI build proves compilation/package creation only; real-device playback, background operation, download reliability and source compatibility still need device testing.

## Build

Requirements: JDK 17, Android SDK 35 and Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Third-party code and compatibility

The playback adapter is derived from LibreTube/NewPipeExtractor work. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the included GPL-3.0-or-later license. Check license obligations before distributing builds.

Offline downloads should only be used for content and methods you are permitted to keep offline. Private app storage prevents ordinary sharing by the app; it does not grant download rights or bypass platform terms.
