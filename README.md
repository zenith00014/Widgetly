# Nexus — personal Android app

Nexus is a private, single-owner Android app for one person's daily use. It is not a public service, commercial product, or multi-user platform. Build decisions favor usefulness on the owner's phone, low storage and battery use, simple maintenance, and local-first personal data.

## Product architecture

- **DM:** The current Inbox opens the installed Beeper app. Android notification previews and quick-reply actions remain a separate optional feature, not a replacement for Beeper conversations.
- **YouTube:** The current Nexus integration hands video URLs to the installed LibreTube Android app. This is an external app handoff, not embedded LibreTube playback.
- **Music:** The current Music section launches LibreTube and can hand a search query to it. It is an external app handoff, not an embedded player.
- **Movies:** Provider FTP server at `192.168.100.100:21`, anonymous login, passive mode, root-level individual video files. The current screen caches the catalogue locally, supports search and manual refresh, and uses Media3 with a custom FTP DataSource for playback. Seeking depends on server REST support. This feature is available only on the provider network; artwork, favorites and resume history are not yet implemented.
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
