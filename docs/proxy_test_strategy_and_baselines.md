# Proxy Test Strategy and Baselines

- Status: Active; Phase 18 standard release gate and supported capacity envelope present
- Date: 2026-08-18

## Purpose

This document records reproducible evidence for proxy correctness and scalability. It separates historical Phase 9 measurements, current streaming/canonical limits, the standard release gate, and extended-duration release soak.

## Current deterministic coverage

Run the focused containment suite with:

```shell
./gradlew :engine:proxy:test :engine:interceptor:test :engine:certificate:test :engine:session:test :engine:protocol:test :storage:jvmTest :data:desktop:jvmTest :application:test :connectivity:desktop:jvmTest :ui:desktop:traffic:jvmTest :ui:desktop:apistudio:jvmTest
```

The suite covers loopback binding, failed-bind rollback/retry, strict authority rejection, setup-listener isolation, HTTP/1 ordering/streaming semantics, breakpoint terminal paths, trust and file security, canonical writer non-regression, saturation gaps, disk-exhaustion degradation, corrupt-body marking, active-session clear rotation, deletion convergence, retention/recovery, finalized-orphan inventory, schema-v13 persistence, semantic inspector isolation, connectivity transitions, pairing, gateway admission/revocation/attribution, and application lifecycle shutdown.

Run the repository and packaged-runtime gate with:

```shell
./gradlew phase18ReleaseGate
```

The completed 2026-08-18 gate passed all 254 actionable tasks and produced the desktop distributable under `products/desktop/build/compose/binaries/main/app`.

## Measured Phase 9 baselines

Run `./gradlew :engine:proxy:test --tests '*ProxyCapacityBaselineTest'` for concurrent loopback clients, the temporary aggregated large-body ceiling, slow upstream timeout, abrupt disconnect, lifecycle repetition, heap/direct-memory, throughput, and file-descriptor recovery gates. Run `./gradlew :data:desktop:jvmTest --tests '*CanonicalTrafficScaleBaselineTest'` for the 100,000-row indexed-query and metadata-storage gate.

The first retained measurement on 2026-08-18 used a Macmini9,1 (Apple M1, 8 GiB), macOS Darwin 24.6.0, and OpenJDK 21.0.8:

| Scenario | Measurement | Enforced threshold |
|---|---:|---:|
| 24 clients × 256 KiB response | 31 ms; 202,950,193 B/s | ≤ 30 s |
| Concurrent peak heap delta | 10,336,088 B | ≤ 256 MiB |
| Concurrent pooled-direct delta | 0 B | ≤ 128 MiB |
| Concurrent peak descriptor delta | 118 | ≤ 256; post-stop within initial + 32 |
| Single 8 MiB response | 29 ms | ≤ 30 s |
| 8 MiB peak heap/direct delta | 8,725,912 B / 16,777,216 B | ≤ 192 MiB / 64 MiB |
| Six slow peers + 24 disconnects | 1,583 ms | ≤ 20 s; descriptors recover |
| Ten start/stop repetitions | 2,145 ms | ≤ 20 s; descriptors recover |
| 100,000-row filtered 100-item page | 2 ms | ≤ 5 s |
| 100,000-row SQLite/WAL/sidecars | 46,242,224 B | ≤ 256 MiB |
| 1,000 connection churn (20 × 50) | 760 ms; descriptors recovered to 142 | ≤ 120 s; post-stop within initial + 32 |

These are regression ceilings, not product throughput claims. CI measurements should be collected over time before tightening them. Phase 11 replaces the aggregated large-body ceiling with streaming 500 MiB and 100 × 10 MiB gates.

## Supported capacity envelope

The envelope below is an enforced regression contract on the reference machine/JDK family, not a guarantee for arbitrary hardware or networks:

| Dimension | Supported/enforced gate |
|---|---|
| HTTP transport | HTTP/1.0/1.1 plus experimental H2C/TLS-ALPN HTTP/2 with bounded multiplexing; HTTP/3 and WebSocket transport are unavailable |
| Large response | 500 MiB streamed without body-sized test allocation; capture limited to 10 MiB |
| Large upload | 128 MiB streamed; capture limited to 10 MiB |
| Concurrent bodies | 100 clients × 10 MiB responses with bounded heap/direct-memory thresholds |
| Ordinary gateway/proxy concurrency | 128 authenticated gateway streams by default; explicit admission rejection above the limit |
| Connection churn | 20 workers × 50 cycles = 1,000 fresh connections in the standard soak gate |
| Traffic metadata | 100,000 canonical exchanges, database-filtered 100-item keyset pages |
| Traffic UI | At most 1,000 retained row projections plus bounded selected-body previews |
| Body durability | Per-body/session/global retention, atomic finalize, deletion outbox, startup recovery, size/digest integrity scrub |
| Semantic inspection | Four concurrent inspections by default, 1 MiB global body budget, two-second deadline |
| Breakpoints | Explicit rule/pending-byte/pending-connection/deadline limits; only matched response bodies aggregate under a bound |
| Connectivity | Manual, PAC, Apple profile, ADB reverse, isolated setup listener, paired standard-proxy gateway |

Experimental HTTP/2 is counted only against its dedicated local real-socket gates; it is not a `SUPPORTED`
cross-platform product claim until the qualification matrix in `docs/http2_target_and_implementation_plan.md`
passes. No unavailable protocol/connectivity feature is counted in this envelope.

## Extended release soak

The same deterministic churn test scales through a system property. Choose a cycle count and CI/job timeout appropriate to the intended duration, and retain the printed descriptor/resource result with release evidence:

```shell
./gradlew :engine:proxy:test --tests '*ProxySoakRegressionTest' -Dknet.proxy.soak.cycles=100000
```

The standard gate proves bounded behavior and packaging during normal CI. A multi-hour soak is operational release qualification and is not claimed unless that command was actually run for the release candidate.
