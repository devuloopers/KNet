# `:engine:session`

## Responsibility

Implements the desktop canonical body-object store.

## Owns

- The atomic opaque `BodyRef`-backed `FileBodyStore`.
- Owner-only filesystem permissions, bounded writes and reads, finalized-object inventory, integrity checks, and recovery of abandoned temporary objects.

## Does not own

- Capture-session lifecycle, metadata persistence, UI, proxy transport, semantic inspection, or export formats.

## Dependency rule

Implement application body-store ports and depend on canonical traffic identifiers only. Filesystem details stay internal to this module.

## Current state

The body store supports bounded chunk writes, fsync plus atomic finalize, digest/truncation metadata, range reads, deletion, abandoned-temp cleanup, bounded opaque finalized-object inventory, and bounded size/SHA-256 integrity verification.
