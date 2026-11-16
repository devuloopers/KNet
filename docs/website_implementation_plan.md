# KNet public website implementation plan

This document records the original minimal-page implementation. The approved 2026-11-16 expansion is
tracked separately in the [feature showcase and automatic artwork plan](website_feature_showcase_plan.md).

Status: [COMPLETED] implementation and local qualification on 2026-11-15.
Public domain configuration, registry publication and actual Coolify deployment remain [PENDING].

## Scope and approved direction

Build one public landing page for KNet Desktop using React, strict TypeScript and Vite. Follow the
local Aajubaju administrative website's frontend tooling, static-container packaging and deployment
pattern, while keeping this product independent of its Kotlin/JS runtime and authenticated services.

Use the [minimal homepage proposal](assets/knet-website-home-proposal.png) as the visual reference.
The application screenshot in that proposal is illustrative; production artwork must represent KNet's
actual interface and use non-sensitive demonstration traffic.

The initial page contains:

1. Header: existing KNet brand, Docs, GitHub and Download on GitHub.
2. Hero: "Your app's traffic. Clearly understood." and one short supporting sentence.
3. One Download on GitHub action in the hero.
4. One large, optimized desktop product screenshot.
5. Three short feature labels: Multiple devices, Network conditions and Live interception.
6. A brief local/open-source statement and documentation link.
7. Minimal footer: KNet, Releases, Docs and GitHub.

All download actions open GitHub Releases. There are no binary download URLs, platform selectors,
release API requests or separate Downloads page in this phase. Documentation remains in the existing
repository. Keep the page free of extra feature paragraphs, pricing, accounts, testimonials, analytics
widgets and simulated live charts. Capability descriptions must agree with the current README and
runtime capability catalog.

## 1. Product boundary and tooling [COMPLETED]

- Create `products/website` as an independent npm product with its own lockfile and documented Node
  version. Do not register it as a Gradle module or require JVM/Kotlin compilation to develop it.
- Use React, strict TypeScript, Vite and scoped CSS with small reusable components. Resolve compatible
  maintained dependency versions during implementation and pin the resulting installation.
- Provide development, production build, preview, lint, typecheck, focused test and browser-test
  commands. Keep formatting and build configuration documented.
- Use a small structure rather than the admin application's authentication, API and runtime layers:

```text
products/website/
  MODULE.md
  package.json
  package-lock.json
  index.html
  vite.config.ts
  src/
    app/
    components/
    sections/
    content/
    styles/
  public/
  scripts/
  tests/
  Dockerfile
  deployment/
```

- Add the website product to the product ownership documentation and ignore its generated output,
  browser reports and local environment files.

Acceptance: a clean npm installation can develop and build the website without desktop or Companion
dependencies. Existing application build and release entry points remain independent.

## 2. Branding, content and assets [COMPLETED]

- Reuse the source logo, favicon artwork and suitable social artwork from `desktopBrand`; avoid
  maintaining a redrawn logo or an independent copy of brand source assets.
- Centralize website CSS tokens mapped to KNet's existing dark colors, typography, spacing, borders
  and focus styles. The website is dark regardless of system theme. Keep browser layout rules within
  this web product rather than importing Compose UI dependencies.
- Keep external links and short page content in one typed configuration. Repository:
  `https://github.com/devuloopers/KNet`; documentation:
  `https://github.com/devuloopers/KNet/blob/master/docs/README.md`; releases:
  `https://github.com/devuloopers/KNet/releases`.
- Prepare one real product screenshot with safe demo traffic, strip private information and produce
  optimized responsive image variants. Record its source and regeneration procedure. Do not ship the
  generated proposal's invented interface as a literal release screenshot.
- Reuse a suitable licensed local font or use a system stack. Keep fonts and site imagery local.

Acceptance: consistent KNet branding, short copy, no unsupported maturity claims and no dependency on
runtime third-party image/font services. Every download action has the same GitHub Releases target.

## 3. Responsive landing page [COMPLETED]

- Implement shared container, button/link, header, screenshot, feature-highlight and footer components.
- Match the selected reference's hierarchy, restrained styling and generous spacing. Render the
  desktop preview as an image rather than rebuilding the inspector as a website widget.
- Define responsive behavior centrally in website styles. Fit header actions on small screens through
  an accessible compact navigation treatment; stack feature highlights when needed.
- Keep text naturally readable, preserve screenshot aspect ratio and prevent page-level horizontal
  overflow. Reserve image dimensions so content does not shift while loading.
- Support visible keyboard focus, semantic landmarks, accessible link names, image alternatives,
  sufficient contrast and reduced motion. Use only brief hover/navigation transitions.

Acceptance: the same concise design works at narrow mobile, tablet, normal desktop and wide desktop
widths, including 200% zoom and long system-font metrics. All links and any compact menu work with
keyboard and touch input.

## 4. Static delivery and discoverability [COMPLETED]

React renders the complete page at build time. Since the selected design uses only native links,
production ships no JavaScript or React hydration; the Vite React entry is development-only.

- Produce build-time HTML for the landing content using React's server-rendering APIs, with client
  hydration only where interaction needs it. This remains a static Vite output without a Node server.
- Add page title, description, favicon, social preview metadata, canonical URL, robots policy and
  sitemap. Derive origin-dependent metadata from a documented deployment setting; do not invent a
  production domain or advertise localhost as canonical.
- Keep preview environments out of indexing and validate the public origin before production build.
- Load the hero screenshot efficiently, use responsive image sources and avoid unnecessary runtime
  libraries. Start with budgets of 150 KiB compressed JavaScript/CSS and 400 KiB for the default hero
  image, measured in the build report and adjusted only with recorded evidence.

Acceptance: useful page content is present in delivered HTML without JavaScript, metadata is correct
for the deployment origin, and the optimized production build meets its recorded asset budgets.

## 5. Docker and Nginx hosting [COMPLETED]

- Use a multi-stage Node build and a non-root Nginx runtime, following Aajubaju's packaging pattern.
  The final image contains only public static assets and hosting configuration.
- Serve on port 8080 and expose `/healthz`. Configure compression, safe response headers, revalidation
  for HTML and immutable caching only for content-hashed assets.
- Make missing assets and unknown page routes return an actual 404. The initial site has only one
  page, so no blanket SPA fallback is needed.
- Keep the image stateless and document reverse-proxy HTTPS ownership. This website has no `/api`,
  `/ws`, desktop proxy, certificate store or local-device connection.

Acceptance: the container starts without a JVM or backend, serves the real build as a non-root user,
passes its health check, and returns correct content types, cache headers and missing-file responses.

## 6. GitHub Actions and Coolify [COMPLETED]

Delivery implementation and workflow validation are complete. Live GitHub publication and the
configured Coolify deployment have not been executed; they require the public destination settings.

- Add a dedicated `.github/workflows/website-deployment.yml`, scoped to website, consumed branding
  and workflow changes. Use KNet's current `master` branch rather than copying Aajubaju's `main`
  trigger unchanged. Leave existing desktop and Companion release workflows intact.
- Verification installs from the npm lockfile and runs lint, typecheck, tests, production build,
  browser checks and a production-container smoke test.
- Publish a separate `knet-website` image to GitHub Container Registry with an immutable commit tag.
  Separate verification, image publication and deployment jobs with minimal job permissions.
- Follow Aajubaju's Coolify webhook pattern. Put the webhook and token in GitHub environment secrets;
  keep the public origin as deployment configuration. Do not bake deployment credentials into Vite.
- Automatic delivery now follows Giot's checked-release pattern: current `master` pushes/manual
  runs publish the verified candidate, recheck before digest promotion, and request one validated
  deployment. PRs only verify. The `website-production` secrets are `COOLIFY_WEBHOOK` and
  `COOLIFY_TOKEN`; public repository variable `KNET_SITE_ORIGIN` supplies production metadata.
- Document branch/environment behavior, domain/TLS setup, first deployment and rollback to a previous
  image. Live deployment remains a separate step once its destination is configured.

Acceptance: a website change can be verified and packaged independently; deployment uses the exact
verified image and has a documented rollback path. Domain and Coolify configuration are explicit
delivery prerequisites, not assumed existing infrastructure.

## 7. Verification and handover [COMPLETED]

All 20 unit cases and 39 cross-browser cases passed. The final static container passed its checks;
the local Lighthouse audit scored 100 in all four categories. Full evidence and the remaining live
deployment checklist are in [website qualification](website_qualification.md).

Add behavior-focused tests rather than tests that simply repeat component markup:

| Area | Required verification |
| --- | --- |
| Download/navigation | Every download CTA opens the project's exact GitHub Releases page; Docs/repository links are correct; no binary endpoint or release-fetch dependency exists. |
| Responsive layout | Representative widths 320, 375, 768, 1280 and 1920 pixels; no horizontal overflow, clipped actions or unstable screenshot dimensions. |
| Accessibility | Keyboard navigation, focus visibility, compact-menu open/close if needed, accessible names, contrast, reduced motion and 200% zoom. |
| Browsers | Chromium, Firefox and WebKit production-build smoke checks, with representative mobile viewports. |
| Static behavior | Main content works with JavaScript disabled, local assets resolve, and external-service failure cannot prevent the homepage from rendering. |
| Metadata | Deployment origin validation, canonical/social URLs, preview indexing policy and sitemap consistency. |
| Performance | Compressed asset budgets, no unnecessary fonts/scripts, reserved image bounds and a recorded performance/accessibility audit. |
| Container | Health, non-root execution, content types, compression, HTML versus hashed-asset caching, and actual 404 responses. |
| Delivery | Workflow syntax/path triggers, isolated image tags, no secrets in the bundle and documented deployment/rollback procedure. |

Record executed commands, results, screenshots and remaining limitations in
`docs/website_qualification.md`. Add setup/build/deployment instructions in `docs/website.md`, update
the documentation map and product ownership docs, and update each phase here and in the main
implementation plan as work progresses. Automated coverage and actual live-domain verification must
be reported separately; do not label unexecuted checks as passed.

## Delivery sequence

Implement phases 1-4 as the reviewable website, then phases 5-6 for independently verifiable hosting
and delivery. Run phase-7 checks throughout and finish with the packaged-container verification.
Publish to the configured public destination only as the deployment step, after the site and checks
are concrete and reviewable.
