# Rush

A mobile-first entertainment app inside the Widgetly Android project. Rush is designed around finite, curated shelves rather than an endless feed.

## Experience
- Four destinations: Home, Discover, News, and Library.
- Curated music, movie, and video shelves with search and type filters.
- Full-screen player interfaces and a persistent mini-player.
- Expandable editorial stories with a clear end to the news shelf.
- Save-for-later and recently played collections, persisted locally.
- Aubergine dark theme with magenta/amber accents and phosphor-mint details; optional lilac light theme.
- Original gradient artwork generated in Compose; no remote image assets required.

## Build locally
Requirements: JDK 17, Android SDK 35, and Gradle 8.9 (or Android Studio).

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Build on GitHub
Push to `main` or open the **Actions** tab and run **Build Android APK**. Download the `widgetly-debug-apk` artifact from the completed workflow.

## Notes
The current catalog and news are sample content. The player screens are interactive UI previews; streaming providers and licensed playback sources have not been connected yet.

The project retains its Android Quick Note and Retro Clock home-screen widget implementation.
