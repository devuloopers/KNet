# KNet public website

KNet's public website is the independent React/TypeScript/Vite product at `products/website`. The
professional dark landing page includes the complete Traffic hero, five-feature tour, network/protected
traffic highlights, multi-device workspaces, Android Companion and import/export/copy/scripting links.
All downloads go to https://github.com/devuloopers/KNet/releases. There are no direct binary downloads,
release API requests, accounts or live desktop/device connections.

The homepage, Open Graph and Twitter title is exactly `KNet - Network Inspector & API Studio`, using a
regular ASCII hyphen. Capability descriptions link to the current README matrix and retain experimental
status for advanced protocols and Android Companion. iOS packet-tunnel release availability is not implied.

## Local commands

Use Node 24 (`products/website/.nvmrc`) and npm. From `products/website`:

```sh
npm ci
npm run dev
npm run build
npm run preview
npm run lint
npm run typecheck
npm test
npm run test:e2e
npm run test:container
```

Vite development uses http://127.0.0.1:5174; the static preview and browser-suite server use
http://127.0.0.1:4174. The browser suite starts/stops its own preview, so close an existing server on that
port first. `PORT` can select a different local preview port. Browser setup uses
`npx playwright install chromium firefox webkit`. Container checks require a running Docker daemon and
remove their disposable non-root/read-only container afterward. `KNET_CONTAINER_SKIP_BUILD=true` reuses a
previous image; `KNET_WEBSITE_IMAGE` selects its local tag.

Normal npm work uses the checked-in safe artwork and needs no JVM. It validates the entire image manifest
before building; missing/corrupt image bytes fail rather than leaving broken screenshots. Generated
`public`, `.generated`, `.prerender` and `dist` files remain ignored.

## Ownership and visitor runtime

- `src/content/site.ts`: typed feature identities, short copy, alt text and public destinations.
- `src/components`: shared brand, download links, SVG symbols and responsive `ProductImage` frames.
- `src/sections`: Header, Hero, feature tour/highlights/devices/utilities/closing action and Footer.
- `src/styles/tokens.css` and `site.css`: KNet dark colors, shared dimensions, responsive layout, focus
  and reduced-motion policy. System light mode never changes the palette.
- `src/entry-server.tsx` and `scripts/prerender.mjs`: render all meaningful React content to static HTML.
  `src/development.tsx` mounts React only in Vite development.
- `src/showcase.ts`: the production browser entry. It enhances native feature links into manual
  activation tabs with arrow/Home/End navigation, Enter/Space selection, selected-panel permalinks and
  touch support, and owns the enhancement lifetime/cleanup. There is no React hydration, Compose/Wasm,
  Kotlin engine or backend runtime in the site.
- `src/motion.ts`: native Web Animations/IntersectionObserver ownership, interrupted tab transitions,
  scroll reveals, keyboard/hash visibility and reduced-motion changes. It joins the same small bundle;
  no animation library or persistent frame/timer loop is loaded.
- `scripts/artwork.mjs`: source-image manifest/path/dimension/hash and release-identity validation.
- `scripts/prepare-assets.mjs`: shared brand projection, AVIF/WebP optimization, content-hashed image
  names, generated TypeScript image catalogue/source-ratio CSS and public source-provenance manifest.
- `scripts/site-environment.mjs`, `validate-environment.mjs`: public-only origin/indexing/metadata policy.
- `scripts/check-budgets.mjs`: enforces 150 KiB gzip JavaScript/CSS and 400 KiB default WebP hero budgets.
- `scripts/serve.mjs`, `deployment/nginx.conf`, `Dockerfile`, `scripts/verify-container.mjs`: local preview
  and production-shaped static hosting, security/cache/health checks and browser verification under CSP.

Without JavaScript, every feature remains a native anchor destination with its description and real
capture. With JavaScript, inactive panels share a grid cell and retain intrinsic layout contributions,
so different descriptions cannot shift the section when selecting another feature. Hidden panels are
not focusable or exposed as active content. Deferred image sources are activated only for selected tour
panels; `<noscript>` supplies their normal images to no-script visitors. The hero loads promptly, while
other screenshots are lazy-loaded. Width/height attributes reserve image geometry before loading.
Traffic is the default tab and initial non-deferred feature image. A valid `#feature-...` permalink
retains its requested selection; ordinary visits and unknown hashes start with Traffic.

Tab content crossfades for 220ms in the existing shared grid. The outgoing layer becomes inert and
ARIA-hidden synchronously, remains visual only while exiting, and is removed from that visual state
afterward. Rapid changes cancel obsolete effects, and clicking the selected tab does not restart one.
Offscreen sections fade in once over 480ms; above-fold and no-script content starts visible. Smooth
anchor navigation and 180ms hover/underline feedback use the same theme duration/easing tokens.
Reveals change opacity, preserving section geometry and native anchor positions.

Observers unobserve completed sections; finished effects release their animation resources. There is
no permanent `will-change` or continuous animation. Keyboard focus and explicit anchor targets reveal
immediately. Missing observer/animation APIs preserve usable content. Reduced-motion preferences are
honored on startup and when changed: pending content becomes visible, running effects cancel and smooth
scrolling stops. Re-enabling motion never hides already visible content.

Every feature-tour frame uses the corresponding verified source's aspect ratio. Asset preparation emits
external CSS keyed by the frame's `data-artwork` identity, so dimensions are reserved even before a
deferred image loads and stay compatible with the existing CSP. At desktop widths a height-derived
width cap scales both dimensions proportionally, leaving room for the heading, tabs and card insets.
The frame follows the bitmap rather than adding empty bands; shared viewport dimensions live in
`tokens.css`. All panels retain the same geometry while switching tabs, including deferred images and
no-script fallbacks. Phone/tablet sections retain natural page scrolling.

The complete hero screenshot preserves its aspect ratio and fits available desktop viewport height.
The feature tour reuses the hero's `showcase-layout`, title scale, spacing and responsive stacking:
tabs sit above open two-column content, without an outer card or a second visible introduction. Panel
copy begins with its main title; the feature name appears in the tab row without a repeated label.
The feature section retains an accessible hidden heading. In stacked enhanced panels, shared subgrid rows
keep screenshot positions stable across different copy lengths; no-script panels remain normal flowing
sections. Removing the extra heading/card makes room for larger complete captures in short windows.
Narrow screens stack content. Header actions wrap under magnification; tabs scroll within their own
row using touch, trackpad or keyboard. That row hides its native scrollbar and clips vertical overflow;
the shared page scrollbar is unaffected. All screenshots are static examples, including the network
graph. No external font, analytics,
image CDN, application API or control WebSocket is requested by the page.

## Actual Compose artwork and automatic regeneration

From the repository root, with the existing JDK 21/Gradle toolchain:

```sh
python3 -m unittest discover -s tools/website -p 'test_*.py'
python3 tools/website/render_artwork.py
python3 tools/website/render_artwork.py --verify
```

`tools/website/scenarios.json` inventories eight captures: Traffic, multi-device, API Studio, Intercepts,
Network Conditions, Protected Traffic, Companion pairing and Companion connected. The optional init
script adds isolated fixture sources only to the respective modules' JVM tests; normal product builds
never include them. Shared rendering utilities live in `tools/website/common`.

Desktop fixtures compose the actual feature components (navigation/device tabs/table/inspector,
collections/editors/result panels, live interception drawer, condition workspace, protected manager
shell/table). Companion renders its production root with authored presentation state. Fixture composition
and sample state are owned by `tools/website`; components and their internal layouts remain owned by
the product modules. A new screen/feature or a change to the surrounding fixture composition needs a
scenario update; changes within the reused components regenerate automatically.
Fixture form layouts follow production spacing: the Protected Traffic header uses the same
`KNetTheme.spacing.xs` label-to-dropdown gap as the real manager.

No application bootstrap, user database, private traffic, body store, certificates, pairing credentials,
clipboard, camera, VPN or attached device is used. All endpoints and identities are authored examples.
Scene dimensions, locale, timezone, density and frame timestamps are fixed. Focus is cleared and the
renderer waits for half a second of identical output after asynchronous syntax preparation; unsettled
scenes fail on a bounded deadline. Required semantic labels
must be visible inside the viewport; every expected PNG must have its declared dimensions and non-empty
content. The output manifest records Git source commit, a digest of render inputs (including new files
and deletions), rendering configuration, toolchain/platform and each image's dimensions/SHA-256.

Every render starts in a new empty temporary output directory; existing baseline PNGs cannot fill a
missing scenario. The runner publishes `products/website/artwork/manifest.json` atomically only after all fixture tasks
succeed and source inputs remain unchanged throughout rendering. `--verify` rejects old commits, changed
components/theme/build inputs, missing or altered images and changed rendering configuration. A failed
render leaves the prior manifest intact; it cannot validate against changed/partial output.

Local npm work can consume the safe baseline artwork. In delivery, fresh rendering is mandatory:
`KNET_ARTWORK_REQUIRE_FRESH=true` requires both `KNET_ARTWORK_SOURCE_SHA` and
`KNET_ARTWORK_SOURCE_DIGEST` to match the freshly verified manifest. These public provenance values also
pass into the Docker build as build arguments. They contain no secrets. CI performs source verification
again immediately before container packaging, so the verified npm and container builds consume the
same source PNGs/manifest. Render failures or stale/missing artwork block publication and deployment.

Ordinary UI changes need no manual screenshots or image upload. CI regenerates all scenarios using
pinned source dependencies, JDK 21, Ubuntu 24.04 and installed DejaVu/fontconfig support. Generated images
are retained as qualification artifacts; CI never commits them back or creates a regeneration loop.
Checked-in safe PNGs keep local npm development independent of Gradle. Shared logo/favicon/social-brand
sources remain in `desktopBrand`, with no independently redrawn website brand source.

## Preview and production metadata

The default build is `KNET_SITE_ENV=preview`. It emits `noindex, nofollow`, disallows crawlers and
omits canonical/social-origin URLs and a sitemap. A preview domain must also be access-restricted
if confidentiality is required; robots directives do not provide authentication.

For an indexable production build, set:

```sh
KNET_SITE_ENV=production KNET_SITE_ORIGIN=https://YOUR_PUBLIC_HOST npm run build
```

`YOUR_PUBLIC_HOST` must be replaced with the actual public DNS hostname. The origin must be HTTPS,
without credentials, a port, path, query or fragment. Invalid/missing production origins fail before
building. The build produces matching canonical, Open Graph, Twitter, sitemap and robots URLs;
there is no guessed production domain. Public settings can be placed in an ignored `.env.local`
using `.env.example` as the guide. Deployment tokens must never be put in `VITE_*` variables.

## Production container

The root `.dockerignore` allowlists only website and consumed public-brand sources. The Dockerfile
uses digest-pinned Node 24 and unprivileged Nginx stages. It removes the stock Nginx pages and copies
only `dist` and the site configuration into the runtime image; Node, npm, Gradle, source files and
development dependencies are not runtime requirements.

The runtime listens on 8080 as UID/GID 101 and supports a read-only root filesystem with writable
`/tmp`. `/healthz` returns 200. Nginx owns compression, MIME types, security headers and caching:
HTML/metadata/brand paths revalidate; only content-hashed code and screenshot files are immutable.
Unknown pages/assets return 404 rather than serving the homepage. There is no `/api` or `/ws`
reverse proxy. The Content Security Policy permits only the local tabs/motion bundle, styles and imagery; it blocks external scripts, connections, frames and form submission.
Coolify's reverse proxy owns the public hostname and HTTPS certificate.

## GitHub Actions, registry and Coolify

`.github/workflows/website-deployment.yml` is independent of desktop/Companion delivery. Relevant
pull requests verify the site in unindexable preview mode. Website pushes to `master`, and manual
**KNet website** runs on `master`, verify and automatically deliver the production site. Triggers
cover the website, `tools/website`, consumed UI/core/application/engine/data and brand sources,
Gradle/build configuration, deployment tools, the workflow and root Docker context rules. The default/release branch was checked as `master` on 2026-11-15.

The production sequence is:

1. Run renderer/freshness tests, render all real Compose captures, verify current source identity, then
   run the deployment-tool tests, locked npm install, build, lint/type checks, unit tests, dependency
   audit, Chromium/Firefox/WebKit checks and actual container tests. A production build requires the
   repository's public `KNET_SITE_ORIGIN` variable before any image can be promoted.
2. Save the verified image as a run-scoped artifact. The publication job loads that image without
   rebuilding it and uses its exact checked-out commit's release helper.
3. Confirm the source is still the current `master` head, then publish the unique candidate
   `ghcr.io/devuloopers/knet-website:sha-COMMIT-RUN-ATTEMPT` with the job-scoped `GITHUB_TOKEN`.
4. Check the head again. Resolve the candidate's immutable registry digest and promote that verified
   image to `ghcr.io/devuloopers/knet-website:production`. The workflow summary records source,
   candidate, digest and tracking tag.
5. In the `website-production` environment, check the head once more and send one authenticated
   Coolify POST. Require exactly one queued deployment identifying the intended resource and a valid
   deployment UUID. A successful response is a queued request, not public-readiness confirmation.

PRs, other branches/events, failed verification, unverifiable GitHub refs and superseded commits
cannot advance through those release gates. Production runs are serialized without cancelling an
in-progress publication/request; PR verification uses separate concurrency groups. A new commit can
arrive after the last head check, so a request already sent cannot be revoked by that check. The next
successful current release supersedes it. Coolify resolves `production` when pulling, so a queued
request can receive a newer promoted verified image. Pin an immutable tag/digest when a deployment
must stay version-locked.

### Exact GitHub configuration

Create repository variable under **Settings > Secrets and variables > Actions > Variables**:

| Variable | Value |
| --- | --- |
| `KNET_SITE_ORIGIN` | The public website HTTPS origin, for example `https://knet.your-domain.com`, without a path/query/port. This is the website hostname, not the Coolify dashboard URL. |

Create GitHub environment **website-production** under **Settings > Environments**, then add:

| Environment secret | Value |
| --- | --- |
| `COOLIFY_WEBHOOK` | The website resource's authenticated HTTPS deployment webhook, for example `https://coolify.your-domain.com/api/v1/deploy?uuid=WEBSITE_RESOURCE_UUID&force=false`. Target exactly one UUID. |
| `COOLIFY_TOKEN` | The complete Coolify API token for the correct team/resource, with `deploy` permission. |

The workflow uses these two names, matching Giot's helper. The earlier manual-only
`COOLIFY_WEBSITE_DEPLOY_WEBHOOK` name is superseded. `GITHUB_TOKEN` is supplied automatically by
GitHub Actions; do not create a separate publishing PAT secret. Environment approval/branch rules
remain operator settings; the workflow introduces no mandatory reviewer.

The helper adapts Giot's deployment client with a KNet integration user agent. It validates HTTPS,
path, one resource UUID, optional force/cancellation values and secret syntax before requesting.
It rejects redirects, bounds reads/timeouts, never logs webhook/token/provider bodies and never
retries an ambiguous request automatically. The fixed rejection diagnostics distinguish recognized
authentication, API access, permission and proxy failures. A `--check` mode validates configuration
without sending a deployment request. The current API accepts authenticated
[POST deployment requests](https://coolify.io/docs/api/endpoints/deployments/deploy-by-tag-or-uuid);
[deploy-only token permissions](https://coolify.io/docs/api/permissions) are sufficient for this helper.

### Coolify resource

Configure a **Docker Image** website resource with:

| Setting | Value |
| --- | --- |
| Image | `ghcr.io/devuloopers/knet-website` |
| Tag | `production` |
| Internal port | `8080` |
| Health path | `/healthz` |
| Domain | The same public origin configured as `KNET_SITE_ORIGIN` |

GitHub builds the image; Coolify pulls it after the webhook. Configure registry pull access on the
Coolify server if the GHCR package is private. That pull credential is separate from the short-lived
publishing `GITHUB_TOKEN`. Self-hosted Coolify API access must be enabled and allow this integration.

Set the repository variable, resource and environment secrets before pushing the workflow. Once
pushed, qualifying `master` changes deploy automatically. To retry/start delivery manually, run
**KNet website** from Actions on `master`; there are no deployment-checkbox or environment inputs.
Check the queued UUID in the workflow summary, Coolify Deployment Logs and actual public domain.

### Tests and rollback

`tools/deployment/release_guard.py` implements the production-event/ref/current-head boundary. It
fails closed when GitHub cannot confirm the ref. `tools/deployment/coolify.py` owns the bounded,
redacted single-resource request. Both use the Python standard library. Their 30 tests use synthetic
credentials, fake GitHub responses and one real loopback POST-only endpoint:

```sh
python3 -m unittest discover -s tools/deployment -p 'test_*.py'
actionlint .github/workflows/website-deployment.yml
```

For rollback, pin the Coolify resource to the previously verified `sha-COMMIT-RUN-ATTEMPT` tag or
registry digest and deploy it. Pause automatic delivery while a rollback must remain pinned, then
restore the `production` tracking configuration when resuming. No live webhook is called by the
local tests. Actual registry publication, server pulls and public HTTPS readiness remain hosted
acceptance checks. See [website qualification](website_qualification.md) for executed evidence.
