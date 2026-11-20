# Nexus Design System — SLAB//GLASS

## Direction
A terminal slab: hard-edged brutalist blocks establish hierarchy, with glass reserved for transient overlays. The app should feel deliberate and calm, not like a social feed.

## Tokens
| Token | Dark |
| --- | --- |
| Background | #0E0F0C |
| Surface | #1A1B17 |
| Text | #ECE9DF |
| Muted | #8B8D82 |
| Divider | #3A3C34 |
| Accent | #C8F23A |
| Warning | #FFB020 |

## Components
- Zero-radius slabs and rectangular buttons.
- 2px structural borders; 1px dividers.
- Hard offset shadows only on raised interactive blocks.
- Monospace for labels, status and compact data; readable sans-serif for body copy.
- Minimum 48dp touch targets and clear TalkBack descriptions.
- Glass only for overlays; never use translucent glass as a content-card background.
- Use short, low-motion transitions; respect Android reduced-motion settings.

## Content behavior
- Every Discover category is a finite session with a visible end.
- Autoplay is disabled; media starts only after a user tap.
- Images load lazily and have text fallbacks.
- Cards identify their source.
- PREVIEW and LIVE messaging states must never be conflated.
- Empty, loading, offline and error states must be explicit.

## Navigation
Four primary tabs: HOME, DISCOVER, INBOX, LIBRARY. A media or article viewer temporarily replaces the tab body and has a clear BACK action that returns to the previous list.
