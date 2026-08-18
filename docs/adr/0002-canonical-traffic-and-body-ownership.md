# ADR 0002: Canonical Traffic and Body Ownership

- Status: Accepted
- Date: 2026-08-18

## Context

Request/response values are needed by Traffic, API Studio, breakpoints, replay, exports, scripts, inspectors, and future remote clients. Embedding body byte arrays in every feature model creates copies, unclear memory ownership, and unbounded UI state.

## Decision

`HttpRequestSnapshot`, `HttpResponseSnapshot`, and `HttpExchangeSnapshot` in `:core:traffic` are the immutable shared records. They contain metadata and `BodyRef`, never generally owned body arrays. `BodyAccessPort` supplies bounded ranges. API Studio keeps mutable drafts and converts them at execution boundaries; breakpoints use typed patches; presentation owns only selection and derived state.

One ordered session writer will own durable exchange mutation. A body store will own atomic body files, references, retention, and reconciliation. Until Phase 10, exactly one legacy compatibility writer remains active.

## Consequences

Features share one HTTP vocabulary without sharing mutation or large allocations. Large bodies can remain disk-backed and partial/truncated capture is explicit. Schema and storage types do not leak into stable consumers.
