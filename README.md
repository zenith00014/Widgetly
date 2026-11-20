# NEXUS

Nexus is a free, local-first Android app that brings selected-topic news, followed YouTube uploads, music, public-domain movie discovery, message previews and a personal library into one finite dashboard.

The four primary destinations are **Home, Social, Inbox and Library**. The internal WebView is a supporting reader/player technology, not a browser tab or separate browser product.

## Product and interaction rules

- No advertising SDK, paid subscription, paid API or paid AI.
- No infinite scrolling, autoplay, streaks or engagement rewards.
- Finite Social sessions (10, 20, 30 or 40 items) with recommendation explanations.
- No watch-time, click-through or dwell-time signals in recommendation ranking.
- Local-first saved content and preferences; no required account or backend.
- Integrations use public feeds or official embedded players / links.
- Notification rows are labelled **PREVIEW** and are never represented as fully synchronized conversations.

## Current implementation

- Kotlin and Jetpack Compose Android app.
- SLAB//GLASS visual system: sharp rectangular slabs, ink/off-white surfaces, lime accent, ASCII section labels, hard borders and offset shadows. See DESIGN.md.
- Home with unread message strip, selected-topic news with thumbnails, followed YouTube uploads with thumbnails, Spotify entry point and public-domain movie posters.
- Social feed capped to the selected session size. Items show a local WHY explanation using topic match, followed source, similarity to saved titles, freshness and a diversity bonus. No more than two consecutive items from a source or topic when alternatives exist.
- RSS / Atom image extraction supports media thumbnails, image content/enclosures and HTML image references. Remote images are resized for decoding and cached in app-private cache storage.
- Inbox with notification previews from WhatsApp, Telegram, Instagram and Facebook Messenger, search, unread/source filters and notification-access controls.
- Library with Saved, Watch Later, Liked, category filters, search, notes and JSON export.
- Article and media links open in an in-app WebView child screen; this is a support feature, not a top-level browser section.
- For ordinary article WebViews, a small on-device host blocklist filters common third-party advertising and tracking requests.

## Source configuration

Open **Library → Settings**:
1. Select news topics.
2. Add YouTube channel IDs (one per line); optional label after a pipe, e.g. UC... | Khan Academy.
3. Add a Spotify track, album or playlist URL.
4. Add optional RSS / Atom feed URLs.
5. Select the Social session size.

The default news topics are World, Bangladesh, Technology, Science, Education and Football.

## Honest integration limits

- The Inbox uses Android notification previews. It is not a messaging API: it cannot guarantee complete history, read receipts, reliable in-app replies or message delivery. The user must opt in through Android Notification Access.
- YouTube uploads are discovered through public channel Atom feeds. Playback uses the official YouTube embedded player and requires a tap. YouTube controls player advertising and playback restrictions.
- Spotify uses the official embed. Spotify controls playback, account rules and any ads served inside its player.
- The WebView filter blocks common third-party host requests on ordinary article pages. It is not a VPN or full browser filter and cannot guarantee removal of first-party ads, sponsored content baked into pages, or ads within YouTube / Spotify.
- Movies are discovered from Internet Archive public-domain metadata. A listing does not guarantee that a stream is available in every country or at every time.
- Some RSS feeds provide summaries only or no image. Nexus displays the available feed image or a neutral placeholder rather than inventing an image.
- Remote image cache is app-private cache storage and may be cleared by Android to reclaim space.

## Privacy and storage

Nexus has no analytics SDK or required remote account. Saved links and notes use local SQLite storage; settings and message previews use app-private preferences. Notification previews are stored locally after notification access is enabled. The image cache is app-private.

## Build

Requirements: JDK 17, Android SDK 35 and Gradle 8.9.

Run: gradle :app:assembleDebug

APK output: app/build/outputs/apk/debug/app-debug.apk.

GitHub Actions builds the debug APK on pushes to main, master or kato-phase-1, and uploads an artifact named nexus-debug-apk.

The debug APK is a development build. A signed release APK requires a separate protected signing key and release configuration. Build success does not mean the app has been smoke-tested on every physical Android device.
