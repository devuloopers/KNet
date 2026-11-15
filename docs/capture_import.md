# Local capture-file import

Import opens the [continuous file/action overlay](capture_drop_actions.md) over the current workspace.
Dragging a local capture into KNet shows that same surface before release and updates it in place during
validation. Files can prepare API Studio, Intercept, Network Conditions or Protected Traffic drafts.
They do not open an imported Traffic screen or add records to captured device traffic.

## Supported formats and limits

The existing bounded repository content-detects KNet Capture archives, HAR 1.2 documents from Charles,
HTTP Toolkit and other HAR exporters, and supported Charles XML interchange exports. A filename extension
alone does not select a decoder. Proprietary Charles session files, unsupported archive schemas and
malformed documents produce a clear explanation rather than partial live activation.

Parsing streams records and retained payloads into a temporary private spool. The overlay keeps at most
128 body-free summaries plus the selected full record. Draft preparation reads at most 1 MiB of request
body; missing, partial, omitted, redacted, binary or still-compressed request bodies cannot silently become
complete API Studio drafts. Bodies are decoded only when their recorded representation permits it.
Ordered/repeated headers, URL parameters and available protocol evidence are retained for draft preparation.
The destination-only Protected Traffic action does not load body bytes.

Capture files preserve original availability/evidence metadata for validation, but recorded device IDs,
policy IDs and decisions never authenticate a device or restore active policy. Current app selection
supplies the draft workspace. Rule editors remain unsaved and API Studio remains unsent until explicit
user actions. Imported responses do not enable response export as if a request had just executed.

## Lifecycle

The overlay owns its spool until dismissal, replacement or successful editor routing, then retires it.
A failed replacement or cancelled native picker preserves a previous valid candidate. Cancellation and
single-use native reservations prevent stale work from reopening or overwriting a newer import. Original
files, captured Traffic, registrations and other devices' settings remain unchanged.

The removed imported-history viewer is not an alternate Traffic source. Format repositories, body stores
and parser tests remain because the four editor actions depend on them. See the linked workflow for
surface continuity, input containment, ownership tests and the remaining native acceptance checks.
