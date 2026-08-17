# KNet Implementation Plan

## Phase 1: Packaged Desktop App Runtime Module Fix [COMPLETED]
* Configured `apps/desktop/build.gradle.kts` with essential JPMS runtime modules (`jdk.unsupported`, `jdk.crypto.ec`, `jdk.crypto.cryptoki`, `java.sql`, `java.naming`, `java.management`, `java.scripting`, `java.compiler`, `java.instrument`, `java.security.jgss`) to resolve Netty bootstrap (`sun.misc.Unsafe`), SQLite JDBC, and SSL provider dependencies in native `jlink` packaged distributions.

## Phase 2: Engine Startup Diagnostics & Error Presentation [COMPLETED]
* Implemented `TrafficErrorBanner` in `:ui:desktop:traffic` and wired `TrafficIntent.DismissEngineError` and `TrafficState.engineErrorMessage` to present clear, dismissible visual error feedback whenever port binding fails or proxy engine errors occur.

## Phase 3: Distribution Packaging & Verification [COMPLETED]
* Executed full multi-module `./gradlew test check` test suite (232 tasks passed).
* Executed `./gradlew :apps:desktop:createDistributable` to verify native `jlink` runtime image generation and distribution assembly.

## Phase 4: Full Multi-Platform Brand & Icon Suite (macOS, Windows, Linux, Web) [COMPLETED]
* Designed mathematically centered master SVG logos, symbols, and wordmarks with balanced 70.92px horizontal and 81.60px vertical margins in `brand/svg/`.
* Generated brand kit via LogoLoom MCP (`export_brand_kit`, `optimize_svg`) with 25 web and social assets in `brand/kit/`.
* Compiled native platform desktop icons: macOS `.icns` (315 KB), Windows 7-layer `.ico` (27 KB), and Linux FreeDesktop `.png` suite in `brand/`.
* Documented brand standards in `docs/brand_guidelines.md`.

## Phase 5: Official Brand & Application Icon Adoption [COMPLETED]
* Replaced legacy application resources in `apps/desktop/src/jvmMain/resources/icons/` with the official centered icons (`KNet.icns`, `KNet.ico`, `KNet.png`, `KNet.svg`).
* Verified Compose Desktop window icon (`DesktopBootstrap.kt`) and sidebar branding badge (`NavigationOverlay.kt`).
* Verified native packaging builds with `./gradlew :apps:desktop:createDistributable` and `./gradlew :apps:desktop:packageDmg` (`apps/desktop/build/compose/binaries/main/dmg/KNet-1.0.0.dmg`).

## Phase 6: Multi-Platform Installer Branding & ARM64 Linux Support [COMPLETED]
* Configured `TargetFormat.Exe` alongside `TargetFormat.Msi` in `apps/desktop/build.gradle.kts` with consistent `upgradeUuid`, `perUserInstall`, and `shortcut` options so Windows setup executables embed `KNet.ico` directly into the PE header.
* Designed high-DPI Retina installer window background (`brand/dmg/knet-dmg-background.png`, `brand/dmg/knet-dmg-background@2x.png`) with KNet dark theme, logo, and drag-to-Applications layout.
* Configured `.github/workflows/release.yml` with `create-dmg` post-processing for macOS branded DMG packaging (`KNet-${VERSION}-mac.dmg`).
* Added `ubuntu-24.04-arm` native GitHub runner matrix target in `.github/workflows/release.yml` to build Linux ARM64 packages (`.deb`, `.rpm`, and `knet-${VERSION}-linux-arm64.tar.gz`).
* Verified with `./gradlew check :apps:desktop:createDistributable` (236 tasks passed).
