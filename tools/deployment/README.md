# Website delivery helpers

Standard-library Python helpers adapt Giot's single-resource authenticated Coolify request and
protect website publication/promotion/deployment with current-commit checks. Their tests use only
synthetic credentials, fake GitHub responses and a disposable loopback endpoint.

Run `python3 -m unittest discover -s tools/deployment -p 'test_*.py'` from the repository root.
Generated Python test caches are ignored within this tool directory.
Current configuration, boundaries, test evidence and rollback are documented in
[`docs/website.md`](../../docs/website.md#github-actions-registry-and-coolify).
