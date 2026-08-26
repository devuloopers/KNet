# ADR 0004: Connectivity and Companion Ingress Isolation

- Status: Accepted
- Date: 2026-08-18

## Context

PAC, manual proxy, Apple profiles, ADB reverse, VPN, companion tunnels, and relays have different setup and lifecycle behavior. Coupling any of them to Netty handlers or traffic persistence would force future migration of proven proxy behavior.

## Decision

Connectivity mechanisms consume versioned proxy endpoint snapshots and publish setup descriptors/status. They may establish reachability to an authorized proxy ingress, but cannot own parsing, capture, storage, body access, or inspectors. A future companion or relay terminates pairing/authentication/encryption in a gateway adapter and forwards authenticated byte streams or tunnel sessions to the same ingress contract.

Remote traffic/query/control APIs depend on `:application:desktop`, never on Netty or Room. VPN packet capture may require a separate transport adapter, but its decoded HTTP events enter the same capture/session contracts.

## Consequences

PAC, manual configuration, ADB, profiles, VPN, companion, and relay can be delivered independently. Existing proxy and traffic modules do not migrate when a new reachability mechanism is added.

## Implemented boundary

Manual, PAC, Apple profile, ADB reverse, the dedicated setup listeners, desktop network monitor, portable
pairing, Room-backed registered/trusted-device storage, and the authenticated loopback standard-proxy gateway
now implement this decision. The stock-phone Wi-Fi adapter is a separate automatically managed exact-interface
gateway with intentionally open local-client admission and bounded per-source quotas; its stable setup page
serves Android and Apple certificate formats without entering the proxy pipeline. Mobile apps, VPN, direct
tunnels, and relay remain unavailable product increments.
