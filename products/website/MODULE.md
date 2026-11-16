# KNet public website

Owns KNet's public React/TypeScript/Vite feature-showcase page: browser layout, typed short copy, shared
branding projection, responsive real-UI images, static hosting and website delivery checks.

This independent npm product imports no Kotlin/JS/Wasm runtime, desktop application, proxy, credential
store or device transport. React renders meaningful content at build time. A small local TypeScript
bundle progressively enhances feature links into accessible tabs, native crossfades and one-time scroll
reveals. Traffic is the initial selection, with explicit feature links taking precedence. Reduced-motion
preferences, focus visibility and unavailable browser APIs retain usable content. All feature content and downloads
remain usable without JavaScript. All downloads link to GitHub Releases.

`src/content` owns public claims and destinations; `src/styles` centrally owns browser tokens, responsive
layout and motion. Components/sections compose only presentation. `scripts/artwork.mjs` validates every
image against its source manifest before `prepare-assets.mjs` generates hashed AVIF/WebP variants and
the typed catalogue. Width/height and shared panel geometry prevent layout jumps; inactive-tour images
are deferred. No third-party assets or background API/WebSocket connections are used.
`src/motion.ts` owns native effects/observers and their cleanup; theme tokens own timing/easing. Functional
browser checks use reduced motion for deterministic geometry, while dedicated motion cases run actual
effects in all three engines. Production-container checks exercise native transitions under the real CSP.

`tools/website` owns authored screenshots of actual Compose components, isolated in the respective JVM
test modules. It records source/dimension/content provenance and verifies freshness before release
packaging. CI regenerates automatically for consumed UI/theme/build changes. Normal npm development
uses safe baseline artwork and remains independent of Gradle/JVM. Application production dependencies
and runtime behavior are unchanged. Shared brand source remains owned by `desktopBrand`.

Commands, metadata, provenance, test/delivery contracts and hosting are documented in
[`docs/website.md`](../../docs/website.md). The approved plan is
[`docs/website_feature_showcase_plan.md`](../../docs/website_feature_showcase_plan.md), and executed
evidence is in [`docs/website_qualification.md`](../../docs/website_qualification.md).
