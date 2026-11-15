# KNet public website

Owns the public, static KNet Desktop landing page: React components, browser layout, build-time HTML,
local branding assets, responsive product artwork, static hosting and website delivery checks.

This is an independent npm product, not a Gradle module. It imports no Kotlin/JS runtime, desktop
application, proxy, data store, credentials or device transport. All download actions link to GitHub
Releases. Documentation links to the repository's current documentation map.

`src/content` owns public copy and destinations; `src/styles` owns browser tokens and responsive
rules; components and sections compose only these presentation concerns. Build scripts validate
public origin metadata, project shared brand sources, render the homepage and enforce asset budgets.
Source artwork comes from the optional headless Compose fixture in `tools/website`, never from a
user's private traffic. Only artwork regeneration uses Gradle; npm development/build remains independent.

Development, environment policy, packaging, testing and deployment are documented in
[`docs/website.md`](../../docs/website.md). Current evidence is in
[`docs/website_qualification.md`](../../docs/website_qualification.md).
