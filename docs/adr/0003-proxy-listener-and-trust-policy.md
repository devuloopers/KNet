# ADR 0003: Proxy Listener and Trust Policy

- Status: Accepted
- Date: 2026-08-18

## Context

A wildcard proxy listener without authentication exposes a powerful interception service. Permissive upstream TLS silently weakens origin authenticity. Portal routes on arbitrary proxy authorities can return local setup material for unrelated requests.

## Decision

Fresh desktop startup binds only to explicit loopback and verifies upstream TLS. LAN, wildcard, internal-gateway, companion, and relay exposure require a separate policy-bearing application use case with authenticated access; unsupported exposure is rejected before bind. CONNECT and Host authority values use strict parsing. Setup portal routes require a recognized local authority and run on a separate loopback delivery listener owned by desktop connectivity.

## Consequences

Manual/PAC loopback use is safe by default. Adding mobile or LAN reachability cannot weaken the default listener implicitly. Users may later opt into narrowly scoped insecure upstream testing through explicit policy and visible state, but it is never a fallback.
