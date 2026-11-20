# Rush

Rush is an Android-only video and music streaming client built with Kotlin, Jetpack Compose and AndroidX Media3.

## Product scope

- Live YouTube video search and playback
- YouTube Music search and audio playback
- Channel subscriptions and latest uploads
- Video and music playlists
- Watch and listening history
- Background video/audio playback
- Playback quality, speed and captions
- Offline downloads and offline playback for video and music, with format/quality selection, progress, cancellation, resume and storage management

Offline availability depends on the source and applicable permissions. Keep downloaded media in app-private storage; do not bypass access controls or treat private storage as a grant of download rights.

Out of scope for this phase: Movies, TV series, Shorts, Trending and custom recommendation algorithms. The interface remains functional and minimal until the streaming system is reliable; visual redesign comes later.

## Technology

- Kotlin, Android Gradle Plugin and Jetpack Compose
- AndroidX Media3 for playback
- NewPipeExtractor for supported YouTube stream resolution
- Local persistence, download workers and MediaSessionService are planned implementation components

## Implementation status

Implemented:
- Android application shell and app label Rush
- URL/video-ID stream resolution through the NewPipeExtractor adapter
- Live YouTube search in Discover
- Search result selection and player URL prefill

Not implemented yet:
- Dedicated YouTube Music search integration and full song/artist/album metadata
- Persistent subscriptions, playlists and history
- Latest uploads feed
- MediaSessionService background playback and notification controls
- Download queue, quality selection, pause/resume, cancellation, storage management and offline library
- Playback quality/speed/captions controls

The project is under active development. Do not treat the current branch as a complete streaming client.

## Build

Requirements: JDK 17, Android SDK 35 and Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Third-party code and compatibility

The playback adapter is derived from LibreTube/NewPipeExtractor work. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the included GPL-3.0-or-later license. Check license obligations before distributing builds.

YouTube extraction is unofficial, can break when YouTube changes its service, and must be reviewed against applicable terms and distribution requirements.
