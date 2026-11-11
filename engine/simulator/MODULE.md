# `:engine:simulator`

## Completion qualification

The shaper owns one neutral transport hold while either direction has pending work. Inbound TCP shutdown events
remain behind delayed upload bytes, including live disable/replan paths, so FIN cannot overtake payload. Queue
drain, removal and cancellation release both holds and reference-counted work.
Manual raw read requests also wait behind pending upload work, preventing early read-complete callbacks from
draining an entire sender into the global queue. EOF remains ordered before any unnecessary resumed read.

`networkProtectionLoadQualification` runs separately from routine unit tests. It measures separate aggregate
100 kbps uploads and downloads over four real tunnels, each with over a minute after every first byte, plus
byte/hash/resource bounds for 500 MiB of incremental streaming.
Its 512 MiB worker records heap/direct-buffer/queue metrics under `build/reports/network-protection-load` without
allocating or capturing complete bodies. Physical-device and complete protocol/soak qualification remain separate.
Routine real-socket fixtures additionally exercise shaped half-close, IPv6 destinations, incremental SSE comments,
events/reconnect cursors and immediate live bypass without losing queued bytes.

## Responsibility

Implements deterministic network-condition simulation for proxied connections.

## Owns

- Latency, bandwidth, packet/connection behavior profiles and runtime statistics.
- Netty handlers that apply selected simulation policy.

## Does not own

- Proxy startup, UI state, persisted preferences, connectivity, or traffic storage.

## Dependency rule

Attach through a proxy extension point and remain independently testable.

## Migration direction

Expose configuration through application commands while keeping transport handlers inside this engine.
