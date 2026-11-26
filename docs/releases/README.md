# Product release preparation

[Desktop 0.2.2](https://github.com/devuloopers/KNet/releases/tag/desktop-v0.2.2) is Latest. See the
[release notes](desktop-0.2.2.md) and the [validation record](../implementation_plan.md#desktop-022-release-completed).
Companion remains compatible at 0.2.0.

Desktop 0.2.1 publication is recorded in [`qualification-0.2.1.md`](qualification-0.2.1.md), with changes in
[`desktop-0.2.1.md`](desktop-0.2.1.md). Companion remains at 0.2.0 for this Desktop patch.

Desktop and Companion use independent product-qualified tags. For the coordinated 0.2.0 release,
`desktop-v0.2.0` and `companion-v0.2.0` identify the same verified source commit. Product versions
are independent from the Companion control-envelope protocol version.

## Draft and verification boundary

The product Actions workflows qualify their tagged source, build packages, create checksums and
attest the resulting files. Metadata resolves each tag to an immutable commit used by downstream
checkouts. Before staging packages, the workflow checks that the remote tag still identifies that
commit. Release creation always produces a draft; retries may update only existing drafts.
Published releases are never overwritten by these workflows.

Checksum generation excludes `SHA256SUMS` itself and immediately checks every package entry before
uploading the manifest. A successful draft build therefore cannot retain an invalid self-checksum.

Expected payloads, excluding artifact attestations managed separately by GitHub:

- Desktop: 13 packages and `SHA256SUMS` covering all 13 files. These are macOS arm64/x64 DMG and
  ZIP, Windows x64 MSI/EXE/ZIP, and Linux x64/arm64 DEB/RPM/TAR.GZ.
- Companion: one signed Android APK and `SHA256SUMS`. iOS qualification is required but does not
  distribute an IPA.

Version-specific changes are kept in `desktop-VERSION.md` and `companion-VERSION.md` in this folder;
the workflows append them to generated download/signing instructions when present.

## Preparation and retries

1. Update `gradle.properties` product versions and Android build number, relevant Gradle fallback
   versions, and the Xcode app/extension marketing/build versions. The 0.2.0 mobile build number is
   2000, using the release workflow's numeric version mapping.
2. Run CI against `master`; keep architecture, protocol, feature, packaging and mobile gates enabled.
3. Push annotated product tags to start the product workflows. Keep both releases unpublished while
   inspecting logs and validating their assets.
4. If a code fix is needed, commit and push it to `master`. Cancel active runs for the obsolete
   release revision before advancing both new, unpublished tags with explicit expected remote
   object IDs. Start fresh workflow runs; rerunning an old revision cannot include new code.
5. Confirm both latest release runs and CI pass on the intended commit. Check package names, sizes,
   SHA-256 values, product version metadata and Android signing continuity before publication.

Only the newly prepared, unpublished release tags may move during this process. Existing published
release tags remain immutable. Infrastructure-only failures can rerun the unchanged source.

## Publication

After verification, publish each existing draft with `gh release edit TAG --draft=false --verify-tag`.
Set the Companion release to `--latest=false`, then publish Desktop with `--latest` so the repository's
single Latest marker identifies the desktop download. Verify both public release pages and assets.
Public release publication requires the maintainer's authorization; the coordinated 0.2.0 release and
Desktop 0.2.1 patch were explicitly authorized in their active tasks.

## Native qualification diagnostics

JVM test failures retain full assertion/cause text in hosted logs. Protocol and desktop CI jobs upload
JUnit reports plus JVM native-crash logs even after a failed gate. Release qualification retains the same
evidence. These diagnostics do not bypass qualification or include signing credentials.

SQLite is pinned to 2.6.2 with Room 2.8.4: the 2.7.0 and 2.7.1 JVM bundles omit the Intel macOS native
library. `BundledSqliteDistributionTest` checks all five shipped desktop native resources and executes a
statement using the current host driver; database migration tests exercise schema compatibility.

The proxy capacity fixture caps its Java endpoint socket buffers at 64 KiB. This keeps 100 concurrent
10 MiB origin responses within macOS kernel mbuf capacity while retaining the full transport workload,
existing heap/direct-memory limits and exact-byte assertions. Origin-side failures are reported with
client truncation evidence rather than being mistaken for a proxy timeout.

Failed upstream registration is a terminal candidate result even if the socket never acquired an event
loop. Happy Eyeballs cleanup uses Netty's direct transport close for that unregistered state, and ordinary
channel close once registered. A deterministic incompatible-event-loop regression verifies the race
completes exceptionally without throwing from cleanup or retaining its connection deadline.

## Published 0.2.1 evidence

[Desktop 0.2.1](https://github.com/devuloopers/KNet/releases/tag/desktop-v0.2.1) was published with 13 native
packages and `SHA256SUMS`. Full CI, native packaging, provenance, package metadata and anonymous downloads
passed. The [qualification report](qualification-0.2.1.md) records the exact source, workflows and manifest.
Companion remains compatible at 0.2.0.

## Published 0.2.0 evidence

The coordinated release is complete. [Desktop 0.2.0](https://github.com/devuloopers/KNet/releases/tag/desktop-v0.2.0)
remains available; [Companion 0.2.0](https://github.com/devuloopers/KNet/releases/tag/companion-v0.2.0) provides the
signed Android APK. The [qualification report](qualification-0.2.0.md) records the verified source commit,
workflow runs, repairs, payload/provenance checks and publication results.
