# NEXUS — SLAB//GLASS Design System

## Product principles

NEXUS is a free, ad-free-by-design, local-first Android app. Its four primary destinations are Home, Social, Inbox and Library. Every feed is finite. Nothing autoplays. There is no required account, backend, paid API or paid AI.

Non-negotiables:
- No advertising SDK, subscriptions, paid features or paid APIs.
- No infinite scrolling, autoplay, streaks or engagement rewards.
- Local-first storage; export is user initiated.
- Authorized integrations only; no scraping or piracy.
- Notification rows must be labelled PREVIEW unless an authorized live messaging API is integrated.
- Do not record watch time, click-through or dwell signals for recommendations.

## Visual language

The interface is a terminal slab: rigid blocks establish hierarchy; glass is reserved for temporary overlays; ASCII communicates real information.

### Geometry
- Zero radius on every component, image, chip and avatar.
- 2dp borders for primary slabs; 1dp hairlines for inner dividers.
- Raised blocks use a hard 4dp offset shadow, never a blurred glow.
- 8dp spacing grid. Main content is inset 12dp.
- Pressed blocks lose the offset shadow.

### Glass
Only overlays may use glass: status strip, bottom navigation, sheets, session stopping screen and recommendation explanation panels.
Use 55–70% charcoal, 14dp blur where supported, and a 1dp off-white border at 24% opacity. Use a solid fallback on older or low-RAM devices.
Content cards are opaque slabs, never glass.

### ASCII information design
- Progress and limits: `[#####-----] 5/10`
- Section frames: `+-- INBOX ---------+`
- Status: `[*]` unread, `[ ]` read, `[>]` selected, `[x]` blocked.
- Background texture is static and low contrast.
- ASCII must always have equivalent readable text for TalkBack.

## Color tokens

| Token | Dark | Light |
|---|---|---|
| bg | #0E0F0C | #ECE9DF |
| surface | #1A1B17 | #F8F6EE |
| ink | #ECE9DF | #11120F |
| muted | #8B8D82 | #5C5E54 |
| line | #3A3C34 | #11120F |
| accent / lime | #C8F23A | #3F5A00 |
| warn / amber | #FFB020 | #8A5A00 |

## Typography and accessibility

- Monospace for data, labels and ASCII; bold condensed grotesque for display headings.
- Type sizes: 12 / 14 / 16sp body; 22 / 32sp display.
- Uppercase labels use modest tracking.
- Touch targets are at least 48dp.
- Motion is limited to 120ms linear snaps and cursor blink; honor Reduce Motion.
- Color never communicates state by itself; pair it with text or a glyph.
- Maintain 4.5:1 text contrast and TalkBack labels for all meaningful visuals.

## Navigation and finite content

Four tabs only: Home, Social, Inbox and Library. Reader and media player open as child screens with a clear back path. Settings opens from the status strip.

Home: inbox strip, up to five news items, up to three videos, up to three films, and a finite continue shelf.
Social: one finite curated session, default 20 items; every item has a WHY explanation; no more than two consecutive items from one source or topic; stop screen at the end.
Inbox: notification previews, search and source/unread filters. Never claim complete message synchronization.
Library: saved, Watch Later, Liked, reading, music, movies, collections and recently viewed views.

## Integration limits

- News: selected RSS/Atom sources; show summaries when full text is unavailable; internal reader with WebView fallback.
- YouTube: public channel Atom feeds and official IFrame player; user tap required; third-party player ads remain under YouTube control.
- Music: local files may be played through an authorized media stack; Spotify uses official embed or app link, within Spotify account rules.
- Movies: legally free public-domain catalogs only; show source and license.
- Inbox: opt-in Android notification listener; previews only, deep links to the source app.
- Internal WebView filtering targets common third-party advertising and tracker hosts. It cannot guarantee removal of first-party ads or ads inside third-party media players.

## Data and quality

- Shared content records must have stable IDs and remain consistent across Home, Social and Library.
- Cache images and article content where licensing and source policy allow.
- Network requests are limited to enabled sources; cleartext traffic is blocked.
- No analytics SDKs or hidden tracking.
- Default feed refresh interval target: six hours; no foreground service.
- Release APK target: under 15MB for arm64 split.
- Validate on Android 8+ and low-RAM devices.
