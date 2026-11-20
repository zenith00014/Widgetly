# Kato

Kato is a lightweight, local-first Android app for intentional communication, discovery and entertainment. It is designed around finite lists, user-selected sources and natural stopping points rather than endless feeds.

## Current foundation build

- Native Android app using Kotlin and Jetpack Compose.
- Four primary destinations: Home, Social, Discover and Library.
- Dark, light and system themes.
- Local SQLite library for saved links, categories, completion state and personal notes.
- Local JSON library export through Android's share sheet.
- Optional Android notification access for limited WhatsApp, Telegram, Instagram and Messenger notification previews.
- No account, mandatory backend, advertising SDK, paid API or subscription dependency.
- External links open through Android's normal link handler; Kato does not bypass ads, logins, paywalls or platform restrictions.

## Important limitations

The Social inbox is a **notification preview inbox**, not a unified messaging API. It does not provide full message history, guaranteed delivery, read receipts or replies. Notification access is optional and is only enabled by the user in Android Settings.

Discover is currently a working navigation and interface foundation. RSS news, YouTube channel subscriptions/playback, podcast RSS playback, local audio playback, article reader mode and movie-source verification are not yet implemented. No sample content is presented as real news or available media.

## Build

Requirements: JDK 17, Android SDK 35 and Gradle 8.9 (or Android Studio).

\`\`\`bash
gradle :app:assembleDebug
\`\`\`

APK output: \`app/build/outputs/apk/debug/app-debug.apk\`.

## GitHub Actions

Push to \`main\`, \`master\` or \`kato-phase-1\`, open a pull request, or run the **Build Kato APK** workflow manually. The workflow uploads an unsigned debug APK named \`kato-debug-apk\`.

A signed release APK requires a signing key. Do not distribute the debug APK as a production release.

## Privacy

The app does not require account creation or a server. Saved library items and preferences live in the app's private local storage. Notification previews are stored locally only after the user enables Android notification access. Clearing local content removes the library and captured previews.
