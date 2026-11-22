# Rush device validation

The GitHub Actions debug APK compiles and packages successfully. The following checks require a physical Android device or Android emulator with network access.

## Install

Install `app-debug.apk` from the CI artifact. The app currently uses application ID `com.widgetly.app` and supports Android API 26 and later.

## Playback

- Search for a normal video and start playback from the result.
- Confirm video and audio both play, and test pause, resume, seek and quality changes.
- Open a music result and confirm audio starts; test background playback with the screen off and the notification/media controls.
- Try a second video that offers adaptive formats. Confirm playback recovers if the initial direct stream is unavailable.

## Search and channels

- Search for a broad topic rather than an exact title.
- Search for a channel, open its details, confirm description/subscriber/upload information where available, and open a recent upload.
- Subscribe to the channel, then refresh the subscriptions feed.

## Offline downloads

- Download one video in its source-native format and one music item.
- Confirm progress, cancellation, resume and deletion.
- Try MP3 conversion for an audio item.
- Enable airplane mode and confirm completed files still play offline.

## Diagnostics

When something fails, record the exact search term or video URL, the action taken, and the on-screen error/status. With USB debugging enabled, capture relevant log messages with:

```bash
adb logcat -d -v threadtime | grep -iE "Rush|Sabr|NewPipe|ExoPlayer|MediaCodec"
```

Streaming depends on unofficial YouTube endpoints and extractor compatibility. Device testing is required before treating playback, SABR negotiation, background operation or downloads as production-verified.
