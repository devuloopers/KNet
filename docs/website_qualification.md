# KNet website qualification

Date: 2026-11-15. Scope: the minimal public website under `products/website`, optional safe product
artwork generation, static production hosting and delivery configuration. No desktop/Companion runtime
behavior was changed, and no existing app, device, capture database or user traffic was driven/read.

## Executed checks

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
