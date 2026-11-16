# Website product artwork

This optional headless JVM Compose fixture generates eight real-UI screenshots with authored sample data.
It uses actual feature components in their owning modules' test boundaries and the production Companion
root. It never bootstraps the desktop/mobile products or reads a user's traffic, certificates or devices.

From the repository root:

```sh
python3 -m unittest discover -s tools/website -p 'test_*.py'
python3 tools/website/render_artwork.py
python3 tools/website/render_artwork.py --verify
```

`scenarios.json` declares required outputs and owners. `render-preview.init.gradle` includes `common`, the
existing app fixture in `src`, and each feature's `scenarios` sources only in optional JVM test compilations.
The normal source graph is unchanged. `common/ArtworkRenderer.kt` owns fixed geometry/time/theme and visible
semantic-label checks; each scenario owns its safe state and composition of actual product components.

The runner uses a new empty temporary output directory for every run, so a missing capture can never
be filled by an older baseline PNG. It requires stable source throughout rendering and writes the image manifest only after every
scenario succeeds. Verification compares the Git revision, complete input digest, render configuration,
required inventory, PNG geometry and content hashes. CI rerenders before verifying/packaging, checks freshness
again before the container build and supplies matching source identities to npm/Docker. Failure blocks
publication; no stale-image fallback or automatic commit is performed. Normal npm development uses the safe
baseline images and needs no JVM. Add a scenario once for each new feature; changes inside reused components
then regenerate without manual screenshots.

Ownership, local commands, image optimization, source provenance, CI and static hosting are documented in
[website documentation](../../docs/website.md). Executed evidence belongs in
[website qualification](../../docs/website_qualification.md).
