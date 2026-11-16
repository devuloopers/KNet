# KNet website feature showcase and automatic artwork plan

Planning: [COMPLETED] on 2026-11-16. The user approved the professional design direction.
Redesign, artwork automation and local qualification: [COMPLETED]. Browser-title correction is also
complete. Hosted GitHub Actions rendering and Coolify deployment remain unexecuted until commit/push;
the workflow and local packaged-hosting checks are implemented and qualified.

## Approved design and scope

Use the [approved full-page proposal](assets/knet-website-feature-showcase-proposal.png) for hierarchy,
spacing and visual direction. Its generated application previews are illustrative: replace them with
renders of actual KNet Compose components. Current capabilities, application labels and platform
availability take precedence over invented details inside the proposal.

The website remains an independent React/TypeScript/Vite product. Keep the existing dark KNet palette,
blue accents, shared brand sources, short copy, local assets and GitHub-only downloads. Exact homepage
browser title, including a regular ASCII hyphen:

```text
KNet - Network Inspector & API Studio
```

Use the same title for Open Graph and Twitter metadata. Escape the ampersand when writing HTML.

### Page sequence

| Section | Approved presentation |
| --- | --- |
| Header | KNet brand; Features, Companion, Docs and GitHub links; Download on GitHub action. |
| Hero | Concise headline beside a complete, uncropped Traffic screenshot. Preserve the existing desktop viewport-height fit and stack cleanly on phones. |
| Protocol strip | Compact HTTP/HTTPS, GraphQL, WebSocket, GraphQL WS and gRPC labels. Link to the documented capability matrix and make experimental status discoverable without implying equal maturity. Include SSE in the relevant feature descriptions where accurate. |
| Feature tour | Traffic, API Studio, Intercepts, Network Conditions and Protected Traffic tabs; one focused description and one actual screenshot per panel. |
| Conditions and protection | Two equal feature panels showing the real throughput/rule UI and TLS-handling UI with one short explanatory sentence each. |
| Devices and Companion | Device-specific workspaces and online/offline tabs, plus actual Android Companion pairing/connection artwork. No released iOS packet-tunnel claim. |
| Utility highlights | Import/export, structured copying and Kotlin scripting, using existing capabilities and no unsupported format claims. |
| Closing action and footer | One short download prompt, documentation and repository links. All download actions use https://github.com/devuloopers/KNet/releases. |

Keep the feature tour as website navigation between static product images. No desktop proxy, runnable
Compose/Wasm app, Kotlin runtime, live API execution, device connection or animated simulated graph is
loaded by visitors. Do not add pricing, accounts, testimonials, fabricated usage statistics or analytics.

## 1. Content and reusable website structure [COMPLETED]

- Audit public descriptions against the current README, runtime capability catalog and focused feature
  documentation. Confirm advanced-protocol and Companion maturity before finalizing copy.
- Extend `products/website/src/content` with typed feature IDs, short copy, screenshot references, alt
  text and destinations. Reuse one definition for each feature across the tour and related highlights.
- Reuse/extend shared section containers, links, buttons, brand elements and screenshot frames. Own
  colors, spacing, breakpoints and motion centrally in the website styles.
- Keep original application feature behavior unchanged. Extract a presentational Compose component
  only where a real screen cannot otherwise be rendered in isolation; preserve architecture boundaries
  and qualify existing callers when doing so.

Acceptance: every public claim maps to an implemented capability; page sections use consistent styling
and accurate screenshots rather than independent website recreations of KNet controls.

## 2. Actual Compose screenshot scenarios [COMPLETED]

Extend the existing `tools/website` headless JVM Compose fixture. Use the real composables with authored
UI state, bypassing live application services. Do not add a Wasm target solely to produce images.

| Scenario | Required visual state |
| --- | --- |
| Traffic | Safe sample exchanges, a selected response with formatted JSON, existing navigation and device tabs; used for the hero. |
| API Studio | Actual collection/request/response layout, successful sample response and current copy/export controls. |
| Intercepts | Matching breakpoint and paused request or response with existing edit/forward controls. |
| Network Conditions | Existing profile selection, recorded demo throughput samples, and actual Overview/Rules/Profiles presentation. |
| Protected Traffic | Current default TLS handling and saved destination rules, reflecting actual labels and actions. |
| Multi-device | Online, offline-with-history and selected-device states using the real compact tab component. |
| Companion pairing | Actual Android Companion QR/setup presentation rendered with sample state. |
| Companion connected | Actual Android Companion connected-desktop presentation with a synthetic desktop identity. |

- Use stable scenario identifiers and a manifest of required outputs, dimensions and descriptions.
- Fix timestamps, locale, time zone, theme, fonts, density, viewport, identities, selection and data order.
  Freeze animations and explicitly settle composition/layout before capture.
- Keep screenshot state authored and deterministic. Do not read the running app, user database, body
  store, certificates, credentials, clipboard, attached devices or external endpoints.
- Add the Companion presentation fixture to an appropriate JVM-capable test boundary instead of
  introducing mobile-product dependencies into the desktop production graph.
- Detect missing/empty renders and incomplete content. Verify representative long labels, clipping and
  layout bounds. Review real outputs at the final website display sizes for readability.
- Document one local regeneration command. Adding a new screen requires an initial scenario; later UI
  changes regenerate its image without manual screenshots.

Acceptance: every required scenario renders on the CI host from the checked-out UI code, using the real
components and synthetic state. Repeat renders are stable under the pinned render environment.

## 3. Image generation and freshness contract [COMPLETED]

- Generalize `scripts/prepare-assets.mjs` from one Traffic PNG to the required scenario manifest.
- Produce responsive AVIF/WebP variants, content-hashed filenames and a typed image manifest. Preserve
  source aspect ratios and dimensions; avoid enlarging small detail images unnecessarily.
- Record source commit, scenario IDs, render configuration/toolchain and source-image hashes in a
  generation manifest. Verify that optimized outputs correspond to the current required inputs.
- Make release verification fail if a required image is missing, rendering fails, or provenance does
  not match the checked-out source. Never silently substitute an old screenshot during delivery.
- Keep npm development usable with documented local preview artwork. CI must perform regeneration;
  developers do not need a JVM merely to adjust website copy/styles using those previews.
- Keep generated release images as build artifacts, with no bot commit loop. Ensure both the verified
  npm output and Docker build consume the same freshly rendered source artwork and manifest.

Acceptance: changing a rendered Compose component and running the pipeline updates the corresponding
website imagery. An incomplete/stale render cannot reach image promotion or Coolify deployment.

## 4. Responsive page and accessible feature tour [COMPLETED]

- Implement the approved section order with complete hero artwork and restrained copy. Use accurate
  captures even when their labels differ from illustrative details in the design proposal.
- Preserve the full hero image on normal and short desktop viewports, with proportional scaling and
  natural page scrolling. Stack sections on smaller screens and keep actions and headings readable.
- Reserve image dimensions. Load the hero promptly; defer below-the-fold and inactive-tour imagery
  rather than fetching all full-resolution screenshots on initial navigation.
- Render all meaningful page content at build time. Start from native feature anchor links and usable
  content without JavaScript; progressively enhance the feature tour with a small local TypeScript
  module for accessible tabs, keyboard navigation, selected state and panel changes.
- Keep tab-panel bounds stable and transitions brief. Support touch, visible keyboard focus, reduced
  motion and narrow-screen tab navigation. Do not introduce a carousel or runtime React hydration for
  the whole page.
- Update the current script-blocking CSP narrowly for the local enhancement module; retain the
  third-party connection/script restrictions. Update static/container verification accordingly.
- Retain preview noindex behavior, canonical origin validation, metadata, cache policy and real 404s.

Acceptance: the complete site works in Chromium, Firefox and WebKit, with and without JavaScript. Tab
interaction is accessible and stable; images do not crop unexpectedly or cause layout shifts.

## 5. CI and verified deployment integration [COMPLETED]

- Add a screenshot-generation stage to `.github/workflows/website-deployment.yml` before website
  verification/packaging, using pinned JDK/Gradle settings, fixed fonts and the Linux display/headless
  configuration required by the existing renderer. Confirm it works on the hosted runner.
- Reuse the generated artwork artifact throughout verification and container packaging. Retain the
  scenario manifest and rendered previews with qualification evidence.
- Extend PR/push path coverage to `tools/website`, consumed desktop/Companion/shared UI, render inputs
  and relevant build/dependency configuration. Include indirect theme/component dependencies so UI
  changes trigger a refreshed website even when no React files changed.
- Adjust job timeouts and cache inputs based on measured rendering/build time. Cache dependencies,
  without treating an old artwork cache as evidence for the current source.
- Keep current-commit guards, verified-image publication, digest promotion, environment secrets and
  Coolify delivery intact. PRs verify without publishing or requesting production deployment.

Acceptance: a relevant app UI change automatically produces fresh screenshots and a verified website
candidate from the same source revision; only the existing guarded production path can deploy it.

## 6. Qualification, documentation and handover [COMPLETED]

| Area | Required cases |
| --- | --- |
| Render scenarios | All eight required states; deterministic rerender; correct theme/font/scale; long labels; viewport bounds; no production data or service initialization. |
| Freshness | Changed component updates artwork; missing/stale manifest or image fails; wrong source commit fails; renderer error blocks downstream publication; no checked-in-image fallback in release mode. |
| Image processing | Valid dimensions and aspect ratios; every manifest entry resolves; responsive sources and MIME types; hashed cache identity changes when image bytes change; missing/corrupt input fails clearly. |
| Feature tour | Every panel, pointer/touch activation, keyboard behavior, focus/selected state, repeated/rapid selection, reduced motion, narrow width and JavaScript-disabled fallback. |
| Responsive layout | 320/375/768/1024/1280/1440/1920px widths, both sides of chosen breakpoints, short desktop heights, resize/orientation changes and 200% zoom; no clipped controls or page overflow. |
| Content and links | Exact ASCII-hyphen browser/social title; capability/maturity accuracy; every download reaches GitHub Releases; feature/documentation links have real destinations. |
| Performance | Initial requests and transferred bytes, inactive-image deferral, image decoding/layout stability, bounded script/CSS payload and a recorded Lighthouse audit. Retain existing budgets unless measured evidence justifies a documented change. |
| Static hosting | Existing unit checks, build, lint/typecheck, Chromium/Firefox/WebKit suite, accessibility audit and non-root/read-only container checks, including CSP, caching, health and 404 behavior. |
| Delivery | Relevant path triggers, current-source artifact consumption, PR isolation, failure propagation and existing publication/deployment guards. Report hosted deployment/public readiness separately from local checks. |

Run appropriate existing Compose/architecture checks if shared UI extraction is required. Physical devices
are not needed for deterministic screenshot creation. Record actual results and reviewed screenshots in
`docs/website_qualification.md`; update `docs/website.md`, `products/website/MODULE.md`, the artwork README
and implementation statuses as each phase progresses. Do not mark unexecuted checks as passed.

## Execution order and completion criteria

1. Finalize capability-backed copy and the screenshot scenario manifest.
2. Render/review all actual Compose scenarios and implement image freshness/optimization.
3. Build the approved responsive page and accessible tour using those images.
4. Wire regeneration into the existing verified delivery workflow.
5. Complete automated/browser/container checks and visual review, then document evidence.

Complete means the approved page is implemented, required real-UI artwork regenerates automatically,
the delivery pipeline rejects stale/missing output, and qualification evidence is recorded. Planning
alone does not satisfy any pending implementation phase.

Completed locally on 2026-11-16. See [qualification](website_qualification.md#feature-showcase-and-automatic-compose-artwork-2026-11-16)
and the [machine-readable summary](qualification/website-showcase-2026-11-16.json). All eight captures
were byte-identical across two full isolated renders. The fresh-artwork production build/container,
stale-artwork rejection, 34 web unit cases, 16 freshness cases, 30 deployment-guard cases, six rendered
fixture cases/eight scenes, 90 browser cases and six architecture checks passed. Hosted delivery is a
remaining external execution boundary, not a claimed local result.
