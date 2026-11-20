# Nexus — Remaining Work Tracker

Nexus is a private, single-owner Android app. Keep it native, local-first, lightweight, free, ad-free by design, and widget-free. Do not add public accounts, multi-user infrastructure, monetization, analytics, or growth mechanics.

## Completed in the current implementation
- [x] Remove home-screen widgets, widget receivers, resource XML, and Glance dependencies.
- [x] Add local Saved Library for videos, news, and movies with media filters.
- [x] Add personal note inside the app.
- [x] Add a configurable finite Discover item limit (5–30); autoplay remains off.
- [x] Keep notification-preview Inbox and supported Android RemoteInput reply hand-off.

## Remaining work — implementation order

### P0 — Build and core reliability
- [ ] Get a clean Android debug APK build on the latest branch head.
- [ ] Fix any compile/runtime errors found by build and device testing.
- [ ] Verify Discover feeds, thumbnails, embedded video/article/movie viewing, and back navigation.
- [ ] Improve Inbox: search, app filters, clear individual/all history, timestamps, and expired-reply fallback.
- [ ] Make Saved Library refresh and item removal reliable; handle missing/broken saved URLs.

### P1 — Personal media and communication
- [ ] Music: local audio picker, saved track list, playback, pause/resume, seek, next/previous, and background playback/notification controls if feasible without excessive size.
- [ ] Contacts: permission-based Android contacts picker; create call/message shortcuts using system intents; never scrape contact data or promise third-party message sending.
- [ ] Settings: source selection, theme, session-size defaults, notification access help, and privacy controls.

### P2 — Anti-doomscrolling and data ownership
- [ ] Add a daily Discover time budget and item budget with a clear stop screen.
- [ ] Add optional break reminders; no streaks, rewards, autoplay, or engagement tracking.
- [ ] Add offline article saving/reading and bounded image/content cache.
- [ ] Add local export/import for personal notes, saved items, settings, and Inbox history; avoid exporting transient notification action tokens.
- [ ] Add reset/clear-data controls and explain exactly what is removed.

### P3 — Quality and release readiness
- [ ] Add accessibility labels, adequate touch targets, and reduced-motion behavior.
- [ ] Verify app size, startup, memory, and background battery use.
- [ ] Check Android 8+ compatibility (minSdk 26), Android 13+ permissions, and Android 15/16 behavior.
- [ ] Produce and verify a downloadable debug APK artifact.
- [ ] Update README and this tracker with only verified completion status.

## Constraints and integration notes
- Notification previews are not full chat synchronization. Quick reply works only while a compatible Android RemoteInput action is available; otherwise open the original app.
- YouTube embeds may show YouTube ads. Nexus must not claim to remove provider ads.
- Only list films with source/rights information; Internet Archive availability alone does not prove public-domain status.
- Local music uses files the owner selects. Spotify remains an official-app/deep-link integration unless authorized APIs and account rules permit more.
- Prefer Android system pickers/intents and local storage over broad permissions or backend services.

## Status discipline
A checkbox is complete only after code is committed and the relevant build/test has passed. Features that need real-device verification must remain marked as needing device testing until verified.
