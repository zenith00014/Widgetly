# Nexus

A lightweight, ad-free Android discovery and messaging companion designed to reduce doomscrolling. Nexus uses finite content sessions, local notification history, and in-app reading/playback rather than endless feeds.

## Current implementation

- Home: compact dashboard with a limited video shelf and shortcuts.
- Discover: finite Video, News, and Movies sessions.
- Videos: educational YouTube channel RSS feeds with thumbnails; videos open in the embedded YouTube player inside Nexus. YouTube controls its own playback and may show its own ads.
- News: RSS/Atom sources with thumbnails where supplied; articles open in an in-app WebView.
- Movies: public catalog search through Internet Archive; film pages open in its embedded player. Availability and rights remain subject to each catalog item's terms.
- Inbox: opt-in Android notification previews for WhatsApp, Telegram, Instagram, and Messenger. If a notification exposes a RemoteInput reply action, Nexus offers quick reply; otherwise it opens the original app. Previews are not full synced conversations.
- Library: existing Quick Note and Retro Clock home-screen widgets remain available.

## Product principles

- No infinite scroll, autoplay, streaks, ads, subscriptions, or paid APIs.
- No scraping or unauthorized access to private messages.
- Notification rows are labelled PREVIEW; quick reply appears only when Android exposes a supported action.
- Feeds refresh when the user opens or refreshes Discover.
- UI follows the SLAB//GLASS system in DESIGN.md.

## Build locally

Requirements: JDK 17, Android SDK 35, Gradle 8.9 (or Android Studio).

    gradle :app:assembleDebug

APK output: app/build/outputs/apk/debug/app-debug.apk.

## Build on GitHub

1. Open a pull request or push to main.
2. Open the repository Actions tab.
3. Wait for Build Android APK to finish.
4. Download the widgetly-debug-apk artifact.

The workflow currently produces an unsigned debug APK for testing. Configure signing before distributing a release APK.

## Privacy and integration limits

Nexus does not claim to remove YouTube ads or bypass platform restrictions. YouTube playback uses the embedded player. RSS feeds expose only content provided by each publisher. Messaging integrations are limited to Android notification previews and supported notification quick-reply actions; there is no private-chat scraping or full message synchronization.
