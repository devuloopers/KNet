# KNet Companion Brand

This directory contains the standalone identity for KNet Companion.

The companion mark is intentionally different from the framed desktop application icon:

- the connected **K** identifies the KNet family;
- the linked ring represents the paired companion device;
- the mark has no baked-in square, rounded rectangle, background, shadow, phone, monitor, QR code, shield, or VPN glyph;
- platform launchers supply their own background and mask around the transparent mark.

## Source of truth

- `source/knet-companion-symbol.svg` — editable master
- `source/knet-companion-logo-light.svg` — editable light wordmark master
- `source/knet-companion-logo-dark.svg` — editable dark wordmark master
- `svg/knet-companion-symbol.svg` — LogoLoom-optimized production symbol
- `svg/knet-companion-logo-light.svg` — outlined wordmark for light backgrounds
- `svg/knet-companion-logo-dark.svg` — outlined wordmark for dark backgrounds
- `svg/knet-companion-symbol-mono-black.svg` — single-color dark mark
- `svg/knet-companion-symbol-mono-white.svg` — single-color light mark
- `kit/` — LogoLoom raster, social, favicon, and supporting exports
- `preview.html` — light, dark, and small-size visual checks

## Brand tokens

- Gradient start: `#4F9CFE`
- Primary: `#3081FC`
- Gradient end: `#2563EB`
- Supporting indigo: `#6366F1`
- Dark surface: `#0D1117`
- Light surface: `#FFFFFF`

## Platform rule

Do not embed a rounded-square container in this mark. Android adaptive icons and iOS app icons must compose the transparent mark inside their platform-specific safe zones and backgrounds.
