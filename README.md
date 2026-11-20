# Nexus — personal Android app

Nexus is a private, single-owner Android app for one person's daily use. It is not a public service, commercial product, or multi-user platform. Build decisions should favor usefulness on the owner's phone, low storage and battery use, simple maintenance, and local-first personal data.

## Personal app principles

- No public registration, profiles, subscriptions, ads, monetization, public discovery, or multi-user backend.
- No analytics or telemetry unless explicitly requested by the owner.
- Keep personal settings, saved items, notes, and contact preferences on-device by default.
- Ask Android permissions only when the owner enables the corresponding feature.
- Avoid adding abstractions, infrastructure, or settings that do not solve the owner's actual use cases.
- Prefer free sources and integrations; never imply a platform provides access it does not expose.

## Main areas

- **Home:** a concise personal dashboard with a small, finite selection of useful content.
- **Discover:** finite Videos, News, and Movies shelves. No endless feed, autoplay, or engagement loops.
- **Videos:** curated YouTube channel feeds with thumbnails and in-app embedded playback. YouTube controls its player and may show ads.
- **News:** RSS/Atom sources with thumbnails where available; open articles in an in-app reader and save a text-only copy for offline reading when the publisher exposes readable paragraphs.
- **Movies:** public catalog discovery and in-app playback when an item is legally available and embeddable.
- **Inbox:** search and filter notification previews by app, remove individual previews or clear history, use Android quick-reply actions while available, and otherwise hand off to the original app. This is not full private-chat synchronization.
- **Contacts:** choose individual phone contacts through Android's system picker; call and SMS shortcuts hand off to the phone's own apps.
- **Music:** choose local audio files with Android's document picker and play them in Nexus. The current player is local-file focused; background media notification controls are not yet implemented.
- **Library:** personal saved videos, articles and films with filters, plus a local personal note. Nexus does not include home-screen widgets.
- **Settings:** local session and daily item limits, privacy explanations, and JSON backup/restore for personal app data.

## Build and test

Requirements: JDK 17, Android SDK 35, Gradle 8.9 (or Android Studio).

    gradle :app:assembleDebug

APK output: app/build/outputs/apk/debug/app-debug.apk.

GitHub Actions builds a debug APK for the owner's testing. A public release pipeline, store listing, signing/release automation, and distribution infrastructure are out of scope unless requested.

## Platform boundaries

Android notification access can expose notification content and sometimes a reply action. It does not grant unrestricted access to private chats, message history, or sending APIs. Nexus must clearly distinguish a notification preview from a full conversation. Do not scrape private app databases or bypass platform protections.

YouTube playback remains subject to YouTube's player and ad policies. News embeds depend on publisher restrictions. Movie playback depends on the catalog item's availability and rights.
