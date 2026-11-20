# Nexus — personal Android app

Nexus is a private, single-owner Android app for one person's daily use. It is not a public service, commercial product, or multi-user platform. Build decisions favor usefulness on the owner's phone, low storage and battery use, simple maintenance, and local-first personal data.

## Product architecture

- **DM:** Nexus now includes an in-app Matrix client: sign in to a Matrix-compatible homeserver, list joined rooms, create direct rooms, read recent messages, and send text messages. Homeservers with mautrix or other bridges can expose supported third-party networks. Beeper's private bridge infrastructure is not assumed to be a public API; using a Beeper account requires a supported Matrix login and server-side bridge access.\n- **YouTube + Music:** Nexus now includes an in-app Piped-compatible client with YouTube video/music search, thumbnail results, stream resolution, and Media3 playback. It does not launch LibreTube. The public API endpoint is configurable because public instances can fail or rate-limit. This is a LibreTube-style independent client implementation, not a copied LibreTube app module.\n- **Movies:** Provider FTP server at `192.168.100.100:21`, anonymous login, passive mode, root-level individual video files. The screen caches the catalogue locally, supports search and manual refresh, and uses Media3 with a custom FTP DataSource for in-app playback. Seeking depends on server REST support. This feature is available only on the provider network; artwork, favorites and resume history are not yet implemented.
- **Home / Discover / Library / Settings:** retain the personal, finite-session and local-first principles below.

## Personal app principles

- No public registration, profiles, subscriptions, ads, monetization, public discovery, or multi-user backend.
- No analytics or telemetry unless explicitly requested by the owner.
- Keep personal settings, saved items, notes, and contact preferences on-device by default.
- Ask Android permissions only when the owner enables the corresponding feature.
- Avoid adding abstractions, infrastructure, or settings that do not solve the owner's actual use cases.
- Prefer free sources and integrations; never imply a platform provides access it does not expose.

## Main areas

- **Home:** concise personal dashboard with a finite selection of useful content.
- **Discover:** finite Videos, News, and Movies shelves with selectable curated sources, configurable session size, daily item/time budgets, and a stopping screen. No endless feed, autoplay, or engagement loops.
- **Videos / Music:** LibreTube-backed online YouTube and music experience; local audio remains a separate fallback.
- **News:** selectable RSS sources with thumbnails where available; in-app reader and bounded text-only offline copies when readable paragraphs are exposed.
- **Movies:** anonymous FTP catalogue, local cached index, search, favorites, recent playback and in-app Media3 playback.
- **DM / Inbox:** Beeper handoff for full messaging. Android notification previews and quick-reply actions remain a separate optional inbox capability, not a replacement for Beeper conversations.
- **Contacts:** choose individual phone contacts through Android's system picker; call and SMS shortcuts hand off to the phone's own apps.
- **Library:** personal saved media and notes with filters.
- **Settings:** source selection, session and daily item/time limits, notification-access help, JSON backup/restore, and confirmed clear-all-data action.

## Build and test

Requirements: JDK 17, Android SDK 35, Gradle 8.9 (or Android Studio).

    gradle :app:assembleDebug

APK output: app/build/outputs/apk/debug/app-debug.apk. GitHub Actions builds a debug APK for the owner's testing. Build success must be verified before distributing an APK.

## Platform boundaries

Android notification access can expose notification content and sometimes a reply action. It does not grant unrestricted access to private chats, message history, or sending APIs. Nexus must clearly distinguish a notification preview from a full conversation.

LibreTube is an Android application, not a general-purpose playback SDK. A genuine embedded LibreTube integration requires a maintained fork/module and must comply with its GPL-3.0-or-later license. Beeper is currently integrated by app handoff; no private-chat synchronization API is assumed. The FTP server's support for REST byte ranges and seek operations must be tested on the owner's Wi-Fi network.
