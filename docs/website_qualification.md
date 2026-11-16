# KNet website qualification

Date: 2026-11-15. Scope: the minimal public website under `products/website`, optional safe product
artwork generation, static production hosting and delivery configuration. No desktop/Companion runtime
behavior was changed, and no existing app, device, capture database or user traffic was driven/read.

## Website motion and default Traffic (2026-11-16)

Status: [COMPLETED]. Traffic is now the shared default selection/non-deferred tour image, while valid
feature permalinks retain their target. The website adds 220ms native tab crossfades, 480ms one-time
opacity reveals for offscreen sections, smooth anchor scrolling and 180ms hover/underline feedback.
Timing/easing is theme-owned. No animation dependency or continuous JavaScript loop was introduced.

The overlaid grid retains its geometry. Outgoing tabs become inert and ARIA-hidden synchronously,
then release their visual layer after fading; rapid changes cancel obsolete effects. Reveals preserve
anchor geometry, unobserve completed targets and release finished animations. Above-fold/no-script
content is visible initially. Keyboard focus/explicit anchors reveal immediately, and missing browser
APIs retain usable content. Reduced motion works on startup and during active effects, clearing pending
content and cancelling animations without re-hiding sections if motion is later enabled.

Executed verification:

- All 144 Chromium/Firefox/WebKit browser cases passed without retries: 120 functional/layout cases
  plus 24 real-motion cases. Functional checks use reduced motion for deterministic snapshots; the
  dedicated motion file explicitly enables animation and verifies actual browser effects.
- The eight motion scenarios per engine cover accessible crossfades, rapid/repeated selection,
  one-time reveals/resource cleanup, offscreen keyboard focus, initial/dynamic reduced motion,
  missing animation/observer APIs, actual smooth-anchor travel and hover enter/leave cleanup.
- All 34 unit cases, strict TypeScript/static build, ESLint, scoped Prettier and whitespace checks passed.
- Fresh source/image verification passed. The strict-source Docker build and actual non-root/read-only
  Nginx container passed health, content/security/cache/compression/404 checks. Native tab animation
  executed under the real production CSP without browser errors; reduced-motion screenshots capture
  complete static content afterward. The disposable container was removed by the check.
- Compressed JavaScript/CSS is 5,759 bytes locally and 5,740 bytes in the pinned container, below the
  150KiB budget. The default WebP hero remains 58,102 bytes, below 400KiB.

Logs and production-shaped screenshots are retained at
`/Users/devuloopers/.codex/verification/knet-website-motion-2026-11-16/`.
No hosted deployment was triggered by this increment.

## Protected Traffic field spacing (2026-11-16)

Status: [COMPLETED] screenshot composition correction. The website fixture omitted the vertical gap
between “Default TLS handling” and its dropdown. It now uses `KNetTheme.spacing.xs` (4dp), matching
the existing production screen.

All eight Compose captures regenerated successfully in their optional JVM test owners; source revision,
input digest, PNG dimensions and content hashes verified afterward. The static build/strict TypeScript
passed, and all five panels activated with the rebuilt responsive images in Chromium, Firefox and
WebKit (three existing cases passed). Reviewed the corrected field from the actual local website.
Whitespace validation passed. Render/build/browser logs and the reviewed field crop are retained at
`/Users/devuloopers/.codex/verification/knet-website-protected-field-spacing-2026-11-16/`.

## Feature tabs with hero layout (2026-11-16)

Status: [COMPLETED] feature tour layout revision. The tab row now sits directly above open two-column
content using the initial hero's shared layout, title scale and spacing. The outer card and extra
visible introduction are removed; an accessible hidden heading preserves section navigation. Screenshots
retain source-derived proportions and viewport-aware dimensions. At 1099px and below the content stacks
like the hero; enhanced panels share copy/image subgrid rows to keep screenshots anchored across tabs.

At 1600x670 the API Studio screenshot is 850x510px, and the three 8:5 screenshots are 816x510px;
desktop titles use the same 56px scale as the hero. All five screenshot top offsets are identical.
Measured 375x812, 768x950 and 1280x600 layouts also have stable screenshot top offsets and zero
horizontal page overflow. Reviewed the actual [desktop](assets/knet-website-feature-hero-desktop.png)
and [phone](assets/knet-website-feature-hero-mobile.png) previews.

All 120 browser cases passed across Chromium, Firefox and WebKit without retries. These include the
existing containment, no-band, resizing, no-script, keyboard/touch, accessibility and hero checks; three
new cases verify larger feature titles/screenshots and tab placement. Repeated-panel checks additionally
assert stable screenshot positions at phone, tablet and desktop widths. Static build/strict TypeScript,
ESLint, scoped Prettier and whitespace checks passed. Compressed JavaScript/CSS is 4,489 bytes, and the
default hero remains 58,102 bytes, both within the existing budgets.

Build/browser logs, screenshots and measured geometry are retained at
`/Users/devuloopers/.codex/verification/knet-website-feature-hero-layout-2026-11-16/`.
This qualifies the local preview; no deployment was executed for this revision.

## Feature preview side spacing (2026-11-16)

Status: [COMPLETED] refinement of the containment correction below. The full-width, height-limited
frame preserved the whole capture but left broad side bands. Frames now use the corresponding
verified source ratio and scale both dimensions together. Asset preparation generates the ratios as
external CSS rather than inline styles or manually maintained per-feature values. Deferred and
no-script captures reserve their dimensions before loading; the existing CSP is unchanged.

At 1600x670 the Protected Traffic frame is now 624x390px rather than 947.2x390px, with only its 1px
border around the proportional image. The frame still ends at 610.8px, inside the viewport. Reviewed
the actual [updated preview](assets/knet-website-feature-frame-fit.png).

All 117 Chromium/Firefox/WebKit cases passed without retries. The existing 27 containment cases now
also calculate the actual fitted bitmap bounds, checking that neither side bands nor top/bottom bands
are hidden inside a correctly sized image element. They cover all five tabs at seven viewport sizes,
window resizing/direct links, stable panel geometry and no-script fallbacks. All 34 unit cases, static
build/TypeScript, ESLint, scoped Prettier and whitespace checks passed. Transfer remains within budget
at 4,444 gzip bytes of JavaScript/CSS; the 58,102-byte default hero and all source PNGs are unchanged.

Initial intrinsic sizing alone caused cold deferred images to change panel heights. Regression checks
caught that approach; generated source ratios reserve stable geometry before image loading. The final
complete suite passed after that correction. Logs, measured geometry and the reviewed screenshot are
retained at `/Users/devuloopers/.codex/verification/knet-website-feature-frame-fit-2026-11-16/`.
No live deployment was performed for this correction.

## Feature screenshot containment (2026-11-16)

Status: [COMPLETED] shared website sizing correction. At 1600x670 the original Network Conditions
bitmap extended about 11.2px above/below its clipping frame, and its bottom reached 800.3px. A width-only
image inside a fixed 5:3 frame did not constrain taller 8:5 captures. The preview also exceeded the
available browser height.

All feature-tour images now fit a box constrained in both dimensions using proportional containment.
Desktop frame height accounts for the section heading, tabs and card insets through shared viewport
tokens. Deferred images and no-script pictures use the same rule. Tab geometry remains stable and
phone/tablet sections retain normal page scrolling. Source images and Compose components are unchanged.

The corrected 1600x670 frame spans 220.8px to 610.8px, with the complete bitmap inside it. Reviewed the
actual [corrected preview](assets/knet-website-feature-preview-fit.png), including its title and bottom
graph/counters. Verification passed:

- All 117 browser cases across Chromium, Firefox and WebKit. The 27 new cases cover every feature tab
  at 1280x600, 1600x670, 1920x800, 1440x1000, 375x812, 768x950 and 1099x800; stable panel heights;
  direct links and resizing without remounting; and all five no-script images.
- Static build, strict TypeScript, ESLint, scoped Prettier and `git diff --check`.
- Artwork freshness verification for all eight existing Compose captures.
- Existing budgets: 4,326 gzip bytes of JavaScript/CSS and 58,102-byte default WebP hero.

The first focused Firefox no-script run exposed a pending `HTMLImageElement.decode()` promise in the
test helper. Polling actual resource completion/natural dimensions fixed that test harness issue; the
subsequent focused check and complete browser suite passed without retries. Before/after images,
corrected geometry and build/browser logs are retained at
`/Users/devuloopers/.codex/verification/knet-website-feature-fit-2026-11-16/`.
No live deployment was performed for this correction.

## Feature showcase and automatic Compose artwork (2026-11-16)

Status: [COMPLETED] implementation and local production verification. This increment replaces the minimal page with the
approved feature tour, conditions/protection panels, multi-device/Android Companion section and concise
utility highlights. The website displays real Compose artwork, not a runnable desktop/Wasm application.
No desktop/Companion production behavior, real app/device connection, private traffic or user database
was changed or accessed. Hosted GitHub Actions/Coolify execution remains separate from local evidence.

Implemented capture ownership is eight scenarios across six optional JVM fixture test owners. Required
labels must be visible in the actual composed viewport. Focus is cleared, scene time is fixed and the
renderer waits for stable output to avoid asynchronous syntax-highlighting churn. Every run uses a new
empty staging directory, preventing a prior baseline from filling a missing capture. All required PNGs
are validated before publication; source changes and partial failures cannot publish a fresh manifest.

The manifest records source revision/digest, toolchain and image dimensions/content hashes. Fresh release
mode binds npm and Docker to both expected source identities. Existing guarded candidate publication,
immutable promotion and Coolify request handling remain intact. Workflow path checks cover every render
input for both pushes and PRs; actionlint 1.7.7 passed.

| Gate | Executed result |
| --- | --- |
| Real UI generation | All eight Traffic/multi-device/Studio/Intercepts/conditions/protected/Companion captures rendered successfully in their test-only owners. |
| Source/failure tests | 16 Python cases passed, including changed/new/deleted inputs, wrong commit, modified/missing PNGs, dimensions, unsafe/duplicate inventory, failed producer, edit during rendering, stale baseline substitution and trigger coverage. |
| Web tests | 34 Vitest cases passed: the existing 20 metadata cases and 14 artwork inventory/hash/dimension/path/symlink/strict-release cases. |
| Browser qualification | 90 cases passed across Chromium, Firefox and WebKit, including 320/375/768/1024/1099/1280/1440/1920px layouts, no-script/touch/keyboard behavior, stable feature geometry, 200% zoom, short desktop hero fit and whole-page/selected-panel accessibility. |
| Existing deployment guards | All 30 deployment-tool cases passed. No live webhook was called. |
| Architecture | All six `verifyArchitectureFoundation` checks passed. No production Kotlin source or dependency graph was modified. |
| Repeat rendering | Two complete isolated render runs produced byte-identical PNGs for all eight scenarios. Source verification passed after publication. |
| Production packaging | Both npm and the actual Docker build accepted the exact expected source revision/digest. Non-root/read-only Nginx passed health, content types, compression, immutable hashed assets, revalidated HTML, six 404 boundaries, CSP and real browser/tab checks. |
| Stale-artwork rejection | A real Docker build supplied the correct revision and a deliberately stale digest; it failed before producing a release image. |
| Dependency/style checks | ESLint, TypeScript through the production build, Prettier, `git diff --check` and npm audit passed; audit reported zero vulnerabilities. |
| Asset budgets | 4,262 gzip bytes of JavaScript plus CSS; default 1200px WebP hero 58,102 bytes. Both remain below the existing limits. |
| Final visual review | All visible images were decoded before reviewing complete 1440x1000 desktop and 375x812 mobile captures; no page overflow or browser errors. |
| Local production mobile audit | Lighthouse 13.6.0 against the real production Nginx container: performance 99, accessibility 100, best practices 100, SEO 92; FCP 0.8s, LCP 1.5s, blocking time 100ms, speed index 0.8s and layout shift 0. These are local measurements, not field-performance guarantees. |

Findings repaired during qualification: header overflow under magnification; changing feature-card heights
when descriptions wrap; an old external-request assertion that mistook the local `api-studio` image path
for an API call; asynchronous highlighting in repeated captures; and possible reuse of a baseline PNG
when a capture is absent. Inactive tour pictures retain their geometry but defer sources until selected;
no-script visitors receive the complete native-link content and regular images.

The production audit's sole SEO failure is its own robots resource probe being rejected by
`connect-src 'none'` (`Network.loadNetworkResource: CSP violation`). Canonical/indexing checks passed;
robots and sitemap generation/HTTP availability are covered by the existing metadata and container
checks. The connection policy was retained. The score is recorded as measured, without bypassing CSP
to improve it. Earlier failed/interference-heavy audit runs are diagnostic only, not release measurements.

Reviewed actual [desktop](assets/knet-website-showcase-desktop.png) and
[mobile](assets/knet-website-showcase-mobile.png) pages, separate from the illustrative design proposal.
The [machine-readable summary](qualification/website-showcase-2026-11-16.json) records test counts,
source/artifact identity, image stability and audit metrics. Logs, the full audit and manifest are
retained at `/Users/devuloopers/.codex/verification/knet-website-showcase-2026-11-16/`.

Hosted GitHub Actions generation/publication and Coolify deployment were not executed in this increment;
they remain acceptance checks after commit/push. No physical-device test is needed for these static
artwork and website changes. No live app, device or capture data was accessed.

## Browser-title correction (2026-11-16)

Changed the homepage title to `KNet - Network Inspector & API Studio`, with the same regular ASCII
hyphen in Open Graph/Twitter metadata. The generated 404 title now reads `Page not found - KNet`.
The approved feature-showcase redesign and automatic screenshot pipeline remain planned, not implemented.

The production build using `KNET_SITE_ENV=production` and
`KNET_SITE_ORIGIN=https://knet.devuloopers.com` passed, as did all 20 existing Vitest metadata cases.
Direct assertions against `dist/index.html` verified the exact homepage, Open Graph and Twitter titles;
`dist/404.html` verified the matching separator. The existing asset budgets passed (1,992 gzip bytes
of CSS, no production JavaScript; 55,798-byte default hero image). Plan references, the saved design
artifact and `git diff --check` were verified. No browser/container suite or live deployment was run
for this text-only correction.

## Original executed checks

| Gate | Result |
| --- | --- |
| Locked installation | `npm ci` succeeded in the Node 24 Docker build, independently of Gradle/JVM. |
| TypeScript and lint | Strict build/type checks and ESLint passed. |
| Unit cases | 20 Vitest cases passed, covering safe preview defaults, production-origin requirements, invalid/private origins, unknown stages, normalization and consistent crawl/social metadata. |
| Browser cases | 39 passed across Chromium, Firefox and WebKit: 13 scenarios per engine. |
| Real artwork | Optional `WebsiteProductPreviewTest` passed and emitted the actual composed KNet interface using authored example traffic. |
| Static build | React rendered full HTML; production contains no script tags or client React runtime. Local development still uses React/Vite. |
| Metadata integration | An actual production build with a fixture HTTPS origin emitted matching canonical, social, robots and sitemap URLs. The final local output was restored to unindexable preview mode. |
| Asset budget | 1,650 gzip bytes of production CSS; no production JavaScript. Default 1200px WebP screenshot: 55,798 bytes. Both are below the recorded budgets. |
| Dependency audit | `npm audit --audit-level=moderate` reported zero vulnerabilities. A scoped minimatch override is documented in `website.md`. |
| Nginx container | Actual built image passed configuration validation, health, UID 101, read-only root, dropped capabilities, security headers, compression, content types, cache rules, six missing/private/backend-route 404 boundaries and Chromium rendering/layout under its CSP. |
| Workflow | actionlint v1.7.12 passed the dedicated workflow; its downloaded binary was checked against upstream release checksums. The new artifact-download action's pinned commit was verified. |
| Whitespace | `git diff --check` passed. |

The 39 browser cases exercise 320/375/768/1280/1920px viewports, contained download controls,
screenshot aspect ratio/loading, all GitHub destinations and an intercepted real navigation click,
no-JavaScript rendering, unavailable third parties, keyboard skip/focus, fixed dark theme under light
system settings, reduced motion, 200% zoom equivalent, automated WCAG 2 A/AA and 2.1 AA analysis,
absence of background external/API/WebSocket requests, pre-rendered output, budgets and actual 404s.
WebKit on macOS uses Option-Tab for link navigation under its default keyboard preference; the test
uses that documented platform shortcut rather than changing the website's semantic links. See
[Apple's Safari keyboard documentation](https://support.apple.com/guide/safari/keyboard-shortcuts-and-gestures-cpsh003/mac).

Container checks use the production Nginx configuration, which disables scripts and connections while
allowing local CSS/images. The image verification job saves that tested image for registry publication;
the publishing job loads it without rebuilding. No remote workflow run or registry push is claimed.

## Local performance audit

The subsequent user-supplied Releases correction uses exactly
`https://github.com/devuloopers/KNet/releases` for both download buttons and the footer Releases
link. Its rebuilt static output passed the existing navigation case in all three browser engines.
The container verification expectation and website documentation use that same unfiltered URL.

Lighthouse was run against the optimized static production output on a loopback server, with mobile
simulation and a fixture production origin used only to exercise complete indexable metadata:

| Category/metric | Observed result |
| --- | --- |
| Performance | 100 |
| Accessibility | 100 |
| Best practices | 100 |
| SEO | 100 |
| First contentful paint | 0.8 s |
| Largest contentful paint | 1.1 s |
| Total blocking time | 90 ms |
| Cumulative layout shift | 0 |
| Speed index | 0.8 s |

The first audit exposed unnecessary client React work for this static page. Removing the production
runtime/hydration retained React build-time composition and native-link behavior while reducing code
transfer from approximately 70 KiB gzip to approximately 1.6 KiB CSS. The final audit reported no run
warnings. These are local audit observations, not guarantees for an unconfigured public host or every
visitor device/network. Automated accessibility checks do not substitute for all assistive-technology
acceptance.

## Evidence and reproduction

Commands and ownership details are in [`website.md`](website.md). Logs, audit JSON and browser
artifacts from this run are retained under:

`/Users/devuloopers/.codex/verification/knet-website-2026-11-15/`

Repository previews: [desktop](assets/knet-website-desktop.png) and
[mobile](assets/knet-website-mobile.png). The bitmap product screenshot itself remains under
`products/website/artwork`, with its source fixture in `tools/website`.

## Remaining delivery configuration and acceptance

- [ ] Set the actual public HTTPS origin in GitHub repository variable `KNET_SITE_ORIGIN`.
- [ ] Configure the Coolify image resource, domain/TLS route, registry pull access if needed, and
  `website-production` environment secrets `COOLIFY_WEBHOOK` and `COOLIFY_TOKEN`.
- [ ] Execute the production workflow to publish the verified image and deploy that resource.
- [ ] Verify real HTTPS, origin metadata, crawl behavior, asset caching and GitHub navigation at the
  actual public hostname. Exercise rollback using a verified immutable image tag.

The implementation and local checks are complete. Public delivery remains pending configuration;
no deployment or public-domain test was performed in this task.

## Automatic Coolify delivery increment

The workflow now deploys qualifying `master` pushes and manual `master` runs automatically after
website verification, candidate publication and current-head/digest promotion. PRs remain
verification-only. The source is checked before publication, before promotion and before the
single-resource request. Giot's request helper was adapted for KNet with redacted diagnostics,
bounded reads, redirect rejection, one POST and matching queued-resource validation.

All **30 deployment-tool cases** passed: 19 webhook cases and 11 release-guard cases. These cover
current push/manual eligibility, wrong events/refs, superseded commits between stages, malformed
contexts/refs, GitHub timeout/error redaction, strict step outputs, valid POST options, actual
loopback authorization/user-agent/query preservation, wrong/duplicate/malformed/deep/oversized
responses, authentication/proxy/permission failures, redirects, cancellation options, no automatic
retry and configuration-only mode. The loopback endpoint uses only a synthetic token. No production
Coolify request or registry push was executed. actionlint v1.7.12 and whitespace validation passed.

The upstream `master` default branch and current Coolify POST/bearer/deploy-permission documentation
were checked during this increment. Exact public variable, environment-secret names, resource
settings and rollback behavior are in [website deployment instructions](website.md#exact-github-configuration).

## Desktop hero revision

The approved desktop proposal is implemented at 1100px and above. Copy and download sit beside the
actual product screenshot, with smaller responsive type/spacing and a compact feature row. Smaller
viewports retain their original layout. The image source-size hint now accounts for its desktop column.

Verification for this revision passed:

- Static build with strict TypeScript checks and ESLint.
- All 20 existing unit cases and 39 Chromium/Firefox/WebKit browser cases, including automated
  accessibility, keyboard focus, reduced motion, 200% zoom and no-JavaScript rendering.
- Ten rendered layouts: 375x1000, 768x1000, 1024x1000, 1099x1000, 1100x768, 1280x720,
  1366x768, 1440x900, 1600x1000 and 1920x1080. No horizontal overflow or desktop column/action
  overlap was found. Both hero copy and the complete preview fit inside the first viewport at all
  six checked desktop sizes.
- Before/after full-page PNG comparisons at 375, 768, 1024 and 1099 pixels are byte-identical.
- Build budget: 1,892 gzip bytes of CSS and zero production JavaScript; the default WebP remains
  55,798 bytes. No dependencies or hosting configuration changed.

The updated [desktop preview](assets/knet-website-desktop.png) shows the actual implementation.
Before/after images and measured geometry are retained in
`/Users/devuloopers/.codex/verification/knet-website-desktop-hero-2026-11-15/`.
These results qualify the local revision; publishing it requires its deployment workflow.

## Desktop viewport height correction

A shorter desktop browser reproduced the reported cutoff: at 1600x650 the preview ended at
714.6px, 64.6px below the visible viewport. Width-only checks at taller viewports did not expose it.
Desktop spacing now uses small viewport height units, and the image width is capped from the height
left after the header, spacing and border. Start alignment prevents taller copy from pushing the
image downward. Its full 5:3 contents remain intact and the rest of the page can still scroll.

The static build, lint, formatting and all 39 existing cross-browser cases passed again. Additional
resize/height verification passed 24 checks across Chromium, Firefox and WebKit at 1100x600,
1280x600, 1440x650, 1600x500, 1600x650, 1920x600, 1920x768 and 1600x1000. These measure first-viewport
image visibility, header separation, retained image ratio, unclipped frame height and horizontal
containment while resizing the same page. Four full-page PNGs at 375/768/1024/1099px remain
byte-identical to the pre-redesign phone/tablet baseline. The final build contains zero production
JavaScript and 1,992 gzip bytes of CSS; image assets and dependencies are unchanged.

Measured geometry and viewport screenshots are retained at
`/Users/devuloopers/.codex/verification/knet-website-viewport-fit-2026-11-15/`.
