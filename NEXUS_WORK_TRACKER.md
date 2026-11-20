# Nexus — implementation tracker

Nexus is a private, single-owner Android app. Keep it native, local-first, lightweight, free, and free of ads, analytics and public-user infrastructure.

## Implemented in source (build/device verification pending where noted)

- [x] Native SLAB//GLASS app shell, Home, Discover, Library and Settings.
- [x] Local saved media, notes, contacts picker, JSON backup/restore and clear-data controls.
- [x] Finite Discover item and daily time limits; autoplay remains off.
- [x] Anonymous FTP catalogue for `192.168.100.100:21`, local catalogue cache, search, refresh and custom Media3 FTP DataSource.
- [x] Replace Beeper app launcher with an in-app Matrix client: homeserver login, joined rooms, direct-room creation, recent message history and text sending.
- [x] Replace LibreTube app launcher with an in-app Piped-compatible YouTube/music client: search, thumbnails, stream resolution and Media3 playback.
- [x] Remove the external Beeper/LibreTube launcher implementation and package queries.

## Remaining verification and work

- [ ] Build the current branch and fix all compile errors.
- [ ] Test Matrix login, room listing, sending and receiving on a real homeserver.
- [ ] Configure and test bridges if third-party messaging networks are required. Beeper's private bridge backend is not assumed accessible.
- [ ] Test the selected Piped-compatible endpoint; verify search, video streams, music streams, seek and playback on device.
- [ ] Add robust account-token encryption, incremental Matrix sync, message notifications, pagination and attachments.
- [ ] Add YouTube subscriptions, playlists, watch history, queue and background audio controls.
- [ ] Test FTP catalogue, streaming, seeking and reconnect behaviour on provider Wi-Fi.
- [ ] Add movie artwork/metadata where server files or an authorized metadata source provide it; add favorites and resume history.
- [ ] Verify Saved Library refresh/removal, accessibility, reduced motion, startup, memory, app size and Android 8+ compatibility.
- [ ] Produce and test a downloadable debug APK after the current branch build succeeds.

## Technical boundaries

- Matrix direct messages work with Matrix-compatible users. Third-party platforms require working bridges on the signed-in homeserver.
- Public Piped-compatible instances may be unavailable, rate-limited or broken by upstream changes; the endpoint is configurable.
- FTP media playback requires passive FTP data connections. Seeking requires server REST support.
- Android notification previews are not full private-chat synchronization.
- Keep all personal content and settings on-device unless a specific connected service requires remote storage.

## Status discipline

A feature is not complete for release until CI builds and the relevant behavior has been verified on the Android device. Source implementation and runtime verification must be reported separately.
