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
- [x] Run a clean Android debug APK build after source-selection and cleanup changes (GitHub Actions run 44711799043 succeeded on commit b8f2c44).
- [ ] Fix any compile/runtime errors found by build and device testing.
- [ ] Verify Discover feeds, thumbnails, embedded video/article/movie viewing, and back navigation.
- [x] Improve Inbox: search, app filters, clear individual/all history, timestamps, and expired-reply fallback.
- [ ] Make Saved Library refresh and item removal reliable; handle missing/broken saved URLs.\n- [ ] Verify in-app Matrix client build and homeserver/device behavior; implemented sign-in, joined rooms, direct-room creation, history and text sending for sign-in, joined rooms, direct-room creation, message history and text sending (homeserver/bridge availability still requires setup).\n- [ ] Verify in-app Piped-compatible YouTube/music search and Media3 playback against a working API instance and on-device (public API instance and real-device playback need verification).

### P1 — Personal media and communication
- [x] Music: local audio picker, saved track list, playback, pause/resume, seek, next/previous. Background notification controls remain a separate enhancement.
- [x] Contacts: Android system phone-contact picker with local shortcuts for Call and SMS app hand-off; no broad contact-book permission.
- [x] Settings: source selection for curated feeds, notification-access shortcut/help, local session/daily item/time limits, privacy explanations, JSON backup/restore, and clear-all-data confirmation. Theme remains the fixed SLAB//GLASS dark appearance.

### P2 — Anti-doomscrolling and data ownership
- [x] Add a daily Discover item budget with a clear stop screen. Daily time budget has now been coded with lifecycle-aware tracking; latest build verification is pending.
- [ ] Add optional break reminders; no streaks, rewards, autoplay, or engagement tracking.
- [ ] Add offline article saving/reading and bounded image/content cache. (Text-only article save/read with a 50-article cap is coded and passed an earlier build; general image/content cache remains.)
- [x] Add local JSON export/import for personal notes, saved items, settings, selected contacts and Inbox history; transient notification action tokens are not exported.
- [x] Add reset/clear-data controls and explain exactly what is removed.

### P3 — Quality and release readiness
- [ ] Add accessibility labels, adequate touch targets, and reduced-motion behavior.
- [ ] Verify app size, startup, memory, and background battery use.
- [ ] Check Android 8+ compatibility (minSdk 26), Android 13+ permissions, and Android 15/16 behavior.
- [x] Produce a downloadable debug APK artifact (GitHub Actions artifact attached to run 44711799043; expires 2026-12-04).
- [ ] Update README and this tracker after the new implementation build and device verification.

## Constraints and integration notes
- The Matrix DM client is not a Beeper API clone. Third-party network access requires a homeserver and working bridges; notification previews remain separate from full chat synchronization.
- Nexus's in-app YouTube/music client uses a Piped-compatible API endpoint. Availability depends on the selected instance and upstream extraction support; do not claim permanent ad-free availability.
- Only list films with source/rights information; Internet Archive availability alone does not prove public-domain status.
- Local music uses files the owner selects. Spotify remains an official-app/deep-link integration unless authorized APIs and account rules permit more.
- Prefer Android system pickers/intents and local storage over broad permissions or backend services.

## Status discipline
A checkbox is complete only after code is committed and the relevant build/test has passed. Features that need real-device verification must remain marked as needing device testing until verified.
