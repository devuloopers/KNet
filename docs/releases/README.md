# Product release preparation

Desktop and Companion use independent product-qualified tags. For the coordinated 0.2.0 release,
`desktop-v0.2.0` and `companion-v0.2.0` identify the same verified source commit. Product versions
are independent from the Companion control-envelope protocol version.

## Draft and verification boundary

The product Actions workflows qualify their tagged source, build packages, create checksums and
attest the resulting files. Metadata resolves each tag to an immutable commit used by downstream
checkouts. Before staging packages, the workflow checks that the remote tag still identifies that
commit. Release creation always produces a draft; retries may update only existing drafts.
Published releases are never overwritten by these workflows.

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
Public release publication requires the maintainer's authorization; the coordinated 0.2.0 release
cycle was explicitly authorized in the active task.
