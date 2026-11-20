# Nexus — personal Android app

Nexus is a private, single-owner Android app. It is not a public service or commercial product. Priorities are low storage and battery use, local-first personal data, and useful in-app experiences without unnecessary infrastructure.

## Product architecture

- **DM:** An in-app Matrix client supports homeserver sign-in, joined-room listing, direct-room creation, recent message history, and sending text messages. Matrix homeservers with mautrix or other bridges can expose third-party networks. Beeper's private bridge infrastructure is not treated as a public API; access to bridged networks depends on the account and homeserver configuration.
- **YouTube + Music:** An in-app Piped-compatible client searches YouTube videos and music, displays thumbnails and metadata, resolves streams, and plays them with Android Media3. The API endpoint is configurable because public instances can fail or rate-limit. This is an independent LibreTube-style client, not a copied LibreTube app module.
- **Movies:** Provider FTP server at `192.168.100.100:21`, anonymous login, passive mode, root-level individual video files. Nexus caches the catalogue locally, supports search and manual refresh, and uses Media3 with a custom FTP DataSource for playback. Seeking depends on FTP REST support. Only available on the provider network.
- **Home / Discover / Library / Settings:** retain the personal, finite-session and local-first principles below.

## Main areas

- **Home:** concise personal dashboard.
- **Discover:** finite Videos, News, and Movies shelves with curated sources, configurable session size and daily item/time budgets. No endless feed or autoplay.
- **DM / Inbox:** native Matrix messaging UI. Notification previews remain a separate optional Android capability.
- **YouTube / Music:** in-app search, thumbnails, stream resolution, video playback and audio playback through a configurable Piped-compatible API.
- **Movies:** anonymous FTP catalogue, local cached index, search and in-app Media3 playback.
- **News:** RSS sources with thumbnails where available, in-app reader and text-only offline copies.
- **Library:** saved media, notes and selected contact shortcuts.
- **Settings:** source selection, session limits, notification access, backup/restore and clear-data controls.

## Build and test

Requirements: JDK 17, Android SDK 35, Gradle 8.9.

    gradle :app:assembleDebug

APK output: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions builds a debug APK. A successful CI build is not a substitute for testing the FTP server, Matrix account and media API on the owner's phone.

## Integration boundaries

- Matrix is an open protocol. A Matrix account can message other Matrix users; WhatsApp, Telegram and other networks require working bridges configured for that account. Nexus does not have a private Beeper API integration.
- Nexus's YouTube/music implementation uses a Piped-compatible public or self-hosted API. Search and extraction availability depend on the selected instance and upstream YouTube changes. Do not promise permanent availability or ad-free playback.
- This client is an independent implementation inspired by LibreTube's experience; it does not embed LibreTube source code. If LibreTube source is incorporated later, comply with its GPL-3.0-or-later license.
- FTP playback depends on server support for passive data connections and REST byte ranges. Verify on the provider Wi-Fi network.
- Android notification access exposes notification previews and sometimes quick-reply actions; it does not grant unrestricted private-chat access.
