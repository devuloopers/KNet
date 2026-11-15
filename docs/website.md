# KNet public website

The public website is an independent React/TypeScript/Vite product at `products/website`. It serves
the selected minimal dark landing page, a real KNet UI render with demonstration data, and links to
the existing repository. Both Download on GitHub actions and the footer Releases link use the
GitHub Releases page at `https://github.com/devuloopers/KNet/releases`. There are no direct binary
downloads or release-API calls.

## Local development and commands

Use Node 24 (`products/website/.nvmrc`) and npm. From `products/website`:

```sh
npm ci
npm run dev
```

Vite listens on `http://127.0.0.1:5174`. The asset preparation hook reads existing `desktopBrand`
sources and the checked-in product screenshot; it does not build or launch KNet. No credentials or
environment settings are needed for local development.

```sh
npm run build
npm run preview
```

The production-shaped static preview listens on `http://127.0.0.1:4174`. The build performs strict
type checking, builds the browser assets and build-only React renderer, injects the same component
tree into the HTML, emits crawl metadata and checks compressed asset budgets. The final output is
`dist`; `.prerender` is a temporary build tool, not a runtime server.

Checks, after a build:

```sh
npm run lint
npm run typecheck
npm run test
npx playwright install chromium firefox webkit
npm run test:e2e
npm run test:container
```

Browser tests start and stop their own static preview. They never open the user's desktop app or
read its captured traffic. The container check builds from the repository root, validates Nginx,
starts a disposable read-only/non-root container on a random loopback port and removes it afterward.
It requires a running Docker daemon. `KNET_CONTAINER_SKIP_BUILD=true` reuses an already built image;
`KNET_WEBSITE_IMAGE` overrides the default local `knet-website:verification` tag.

## Ownership and design

- `src/content/site.ts`: public link destinations and brief feature labels.
- `src/components` and `src/sections`: semantic shared links, branding and page sections.
- `src/styles/tokens.css`: centralized browser design tokens mapped to KNet's dark Compose palette;
  `site.css` owns responsive behavior. System light mode never changes this page's palette.
- `src/development.tsx`: serve-only Vite development mounting. Production needs no client JavaScript
  for this page's native links, so no React runtime or hydration is shipped to visitors.
- `src/entry-server.tsx`: build-only HTML rendering. The page's navigation and content also work
  without JavaScript, and there are no API, WebSocket or third-party asset requests on page load.
- `scripts/prepare-assets.mjs`: projects existing brand files into ignored `public` output and
  produces content-hashed AVIF/WebP screenshot variants. The generated manifest is ignored too.
- `scripts/site-environment.mjs`, `validate-environment.mjs`, `prerender.mjs`: public configuration
  validation and static metadata. These scripts consume no deployment credentials.
- `scripts/check-budgets.mjs`: enforces 150 KiB gzip JavaScript/CSS and a 400 KiB default WebP hero
  budget, then writes the measured `dist/asset-budget.json`.
- `scripts/serve.mjs`: local static verification server, with real missing-file/page 404 responses.
- `deployment/nginx.conf`, `Dockerfile`: final static hosting, described below.
- `scripts/verify-container.mjs`: executes the hosting acceptance checks against that built image.
- `vite.config.ts`: React tooling and the serve-only development entry; `tsconfig.json` enforces
  strict browser/build types. `eslint.config.js`, `.prettierignore`, `vitest.config.ts` and
  `playwright.config.ts` own static analysis, formatting boundaries, unit discovery and the three-engine
  browser matrix. `package.json`/lockfile and `.nvmrc` pin the installation and Node development line.

Node's system font stack and local SVG symbols avoid remote fonts and an icon-library dependency.
Header download controls collapse on narrow screens while the main download action remains visible;
Docs and GitHub remain directly accessible. Images reserve their dimensions to prevent layout shift.

At viewport widths of 1100px and above, the hero places its copy/download action on the left and the
actual KNet preview on the right. A wider 1400px content cap, responsive desktop type/spacing and compact
feature highlights match the approved desktop proposal. Smaller viewports keep the original centered,
stacked presentation. The shared image `sizes` hint reflects its narrower desktop column without
adding JavaScript or altering the image's aspect ratio.

Desktop hero spacing also responds to viewport height. The screenshot width is capped by the height
remaining after the header, hero spacing and frame borders, using the image's 5:3 ratio. The frame
aligns to the start of its grid row so taller copy cannot push the screenshot below the initial
viewport. Page content remains scrollable; there is no fixed-height page or clipped image treatment.

All direct npm dependencies are exact versions and `npm ci` uses the checked-in lockfile. The scoped
`@typescript-eslint/typescript-estree` minimatch override selects 11.0.1 to remove the vulnerable
older transitive version under Node 22+/24. Lint, type checks, tests and `npm audit` verify that graph;
review/remove the override when upstream adopts the fixed dependency.

## Product artwork provenance and regeneration

`products/website/artwork/traffic-preview.png` is rendered from actual KNet components, including
device tabs, navigation, Traffic controls, table, inspector and code viewer. It contains authored
`api.example.com` exchanges, sample JSON and synthetic device identities only. No running app,
traffic database, certificates, pairing credentials or private hostnames are read.

The optional `tools/website` fixture is added only to a one-off test compilation by its init script;
it is not part of the default application source sets. From the repository root:

```sh
./gradlew :ui:desktop:app:jvmTest --tests '*WebsiteProductPreviewTest' --init-script tools/website/render-preview.init.gradle --no-daemon
```

This regeneration step needs the existing JVM/Compose toolchain. Subsequent npm builds consume the
checked-in PNG and need no JVM. Source logo, favicon, touch icon and social artwork remain owned by
`desktopBrand`; their generated web projections are not independent editable brand sources.

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
reverse proxy. The Content Security Policy blocks scripts/connections and permits local styles and imagery.
Coolify's reverse proxy owns the public hostname and HTTPS certificate.

## GitHub Actions, registry and Coolify

`.github/workflows/website-deployment.yml` is independent of desktop/Companion delivery. Relevant
pull requests verify the site in unindexable preview mode. Website pushes to `master`, and manual
**KNet website** runs on `master`, verify and automatically deliver the production site. Triggers
cover `products/website`, `desktopBrand`, `tools/deployment`, the workflow and root Docker context
rules. The default/release branch was checked as `master` on 2026-11-15.

The production sequence is:

1. Run the deployment-tool tests, locked npm install, build, lint/type checks, unit tests, dependency
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
