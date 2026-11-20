# Widgetly

Widgetly is an Android-only video and music streaming client built with Kotlin, Jetpack Compose and AndroidX Media3.

## Current project scope

- YouTube video search and playback
- YouTube Music search and audio playback
- Channel subscriptions
- Music and video playlists
- Watch and listening history
- Background playback

Out of scope for this development phase: Movies, TV series, Shorts, Trending and custom recommendation algorithms. The interface will remain functional and minimal until the streaming system is reliable; visual redesign comes later.

## Technology

- Kotlin, Android Gradle Plugin and Jetpack Compose
- AndroidX Media3 for playback
- NewPipeExtractor for YouTube extraction
- Room/local persistence (to be implemented)
- MediaSessionService for background playback (to be implemented)

## Current implementation status

The repository currently contains a prototype UI and URL-based YouTube stream resolution. The bundled sample catalogue is placeholder data; live search, subscriptions, playlists and persistent playback services are not yet implemented. Do not treat the prototype as a completed streaming client.

## Build

Requirements: JDK 17, Android SDK 35 and Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Third-party code and compatibility

The playback adapter is derived from LibreTube/NewPipeExtractor work. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and the included GPL-3.0-or-later license. Check license obligations before distributing builds.

YouTube extraction is unofficial, can break when YouTube changes its service, and must be reviewed against applicable terms and distribution requirements.
