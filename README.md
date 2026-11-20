# Nexus

Nexus is a free, ad-free-by-design, local-first Android app for intentional communication and selected-topic content discovery. It has four destinations: Home, Social, Inbox and Library.

## Current implementation

- Native Android app using Kotlin and Jetpack Compose.
- Home dashboard with unread message previews, latest selected-topic news, followed YouTube channel uploads, a Spotify embed shortcut and public-domain film discovery.
- Social timeline combining selected-topic RSS news with chronological uploads from user-followed YouTube channels. The list is finite and manually refreshed.
- Inbox with optional notification previews from WhatsApp, Telegram, Instagram and Facebook Messenger, including unread markers and profile icons when the source notification provides them.
- Library with saved items, Watch Later, Likes, search, notes and local JSON export.
- Local SQLite storage and SharedPreferences. No account, mandatory backend, advertising SDK, paid API or subscription dependency.
- Dark, light and system themes.
- News feeds, YouTube channel feeds and public-domain movie metadata are fetched directly from public endpoints. No API keys are required for those feed methods.

## Honest platform limits

- The Inbox is based on Android notification previews. It is not a full messaging API and cannot guarantee complete chat history, read receipts, message delivery or replies. Android requires the user to enable notification access manually.
- YouTube channel uploads use public channel Atom feeds. Playback uses the official YouTube embedded player; YouTube controls its own playback availability and advertising.
- Spotify uses the official Spotify embed. Spotify controls playback, account requirements, and any advertising shown by its service. Nexus cannot remove those restrictions.
- Public-domain films are searched through Internet Archive metadata. A catalog entry is not a guarantee that a particular video file is playable in every country or at every time.
- Publisher links open in the browser. Nexus does not strip publisher ads, paywalls or login requirements.

## Configure your sources

Open Library → Settings:
1. Select the news topics you want.
2. Add YouTube channel IDs (one per line). A channel label may follow a vertical bar, e.g. `UC... | Khan Academy`.
3. Paste a Spotify playlist, album or track URL.
4. Add optional RSS / Atom source URLs.

The app starts with World, Bangladesh, Technology, Science, Education and Football topics selected.

## Build

Requirements: JDK 17, Android SDK 35 and Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub Actions

Push to `main`, `master` or `kato-phase-1`, open a pull request, or run the **Build Nexus APK** workflow manually. The workflow uploads an unsigned debug APK named `nexus-debug-apk`.

A signed release APK requires a signing key. The debug APK is for testing, not production distribution.

## Privacy

Nexus does not require account creation or a server. Saved links, notes, preferences and captured notification previews are stored in the app's private local storage. Notification capture is optional and starts only after the user enables Android notification access. Clearing local content removes the local library and captured previews.
