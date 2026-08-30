# ADR 0004: Connectivity and Companion Ingress Isolation

- Status: Accepted
- Date: 2026-08-18

## Context

PAC, manual proxy, Apple profiles, the isolated ADB reverse adapter, mobile VPN/packet tunnels, and relays have
different setup and lifecycle behavior. Coupling any of them to Netty handlers or traffic persistence would force
future migration of proven proxy behavior.

## Decision

Connectivity mechanisms consume versioned proxy endpoint snapshots and publish setup descriptors/status. They may
establish reachability to an authorized proxy ingress, but cannot own parsing, capture, storage, body access, or
inspectors. Companion products terminate pairing/authentication/encryption at dedicated gateway adapters and
forward authenticated proxy streams from their local VPN/packet-tunnel paths to the same ingress contract. A future
relay must use the same boundary.

Remote traffic/query/control APIs depend on `:application:desktop`, never on Netty or Room. VPN packet capture may require a separate transport adapter, but its decoded HTTP events enter the same capture/session contracts.

## Consequences

PAC, manual configuration, the ADB adapter, profiles, VPN/packet tunnels, companion, and relay can evolve
independently. Existing proxy and traffic modules do not migrate when a new reachability mechanism is added.

## Implemented boundary

Manual, PAC, Apple profile, the isolated ADB reverse adapter, dedicated setup listeners, desktop network monitor,
portable pairing, Room-backed registered/trusted-device storage, and the authenticated companion proxy gateway
implement this decision. The stock-phone Wi-Fi adapter is a separate automatically managed exact-interface gateway
with intentionally open local-client admission and bounded per-source quotas; its stable setup page serves Android
and Apple certificate formats without entering the proxy pipeline.

Android and iOS companion products now implement platform VPN/packet-tunnel adapters that forward authenticated
local proxy streams into the companion gateway. Android Companion and its VPN inspection path have passed a manual
physical-device end-to-end smoke test and are cataloged as experimental pending a broader version/OEM,
interruption-recovery, and soak matrix. The iOS/iPadOS app is experimental, while physical iOS packet-tunnel
inspection remains unavailable until an entitlement-signed build is device-qualified. The relay remains
unavailable. Although ADB reverse implementation code remains isolated under desktop connectivity, KNet does not
document ADB traffic as a supported public capture path.
