# Nexus

A lightweight, ad-free Android companion for intentional discovery and communication.

## Included in this iteration
- Native Jetpack Compose interface: Home, Discover, Inbox and Saved.
- Curated public RSS sources for educational YouTube channels, learning/productivity news and movie editorial content.
- Lazy-loaded image thumbnails with Coil.
- Category filters, refresh, saved links and external source playback/reading.
- Unified notification previews from supported messaging apps.
- Notification quick replies when the originating app exposes an Android RemoteInput action.
- Phone contacts with SMS, WhatsApp and call hand-off.
- Existing Quick Note and Retro Clock home-screen widgets retained.

## Messaging boundaries
Nexus does not bypass third-party app privacy or APIs. It cannot read full private-chat histories. The inbox stores notification previews only. Quick reply is available only when a message notification exposes a reply action. SMS, WhatsApp and calls are handed off to their respective apps.

## Feed boundaries
Feeds are fetched from public RSS endpoints. Feed availability and thumbnail metadata depend on publishers. YouTube links open the official destination; Nexus does not scrape or re-host videos. Movie discovery currently uses editorial/movie-news feeds and trailers, not a licensed streaming catalog.

## Build
Requires JDK 17, Android SDK 35 and Gradle 8.9.

\`\`\`bash
gradle :app:assembleDebug
\`\`\`

GitHub Actions builds a debug APK on push or manual workflow dispatch.
