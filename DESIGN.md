# Nexus — Personal SLAB//GLASS UI

## Product context
Designed for one owner, on one Android phone. The UI should optimize for frequent personal tasks, not public onboarding, retention, growth, monetization, or broad device support.

## Visual direction
A terminal slab: hard-edged blocks establish hierarchy, with glass reserved for transient overlays. Calm, compact, tactile, and readable.

## Tokens
| Token | Dark |
| --- | --- |
| Background | #0E0F0C |
| Surface | #1A1B17 |
| Text | #ECE9DF |
| Muted | #8B8D82 |
| Divider | #3A3C34 |
| Accent | #C8F23A |

## UI rules
- Zero-radius slabs and rectangular controls.
- Keep primary actions reachable on a phone.
- Use readable sans-serif body text and compact technical labels.
- Load thumbnails lazily; keep layouts stable while images load.
- Use short transitions and respect reduced-motion settings.
- Avoid excessive settings and decorative UI.
- Show clear loading, empty, and offline states.

## Behavior
- Discover categories are finite sessions with a visible end.
- Autoplay is disabled; playback starts only after a tap.
- Save, dismiss, and mark-read actions should be quick.
- Inbox distinguishes notification PREVIEW from a real LIVE conversation.
- Quick reply appears only when Android exposes a compatible action.
- Contact shortcuts should open the owner's existing communication app when no official integration exists.
- Store personal preferences locally unless the owner explicitly requests sync.

## Navigation
HOME, DISCOVER, INBOX, LIBRARY. The content viewer has an obvious BACK action. Add a dedicated Contacts view only when it is useful for the owner's actual workflow; do not create public profiles or directories.
