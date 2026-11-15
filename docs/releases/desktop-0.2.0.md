
### What's new

- Separate connected devices into individual Traffic tabs, with online/offline state, per-device
  capture controls, scoped clearing and retained local history.
- Manage global and device-specific breakpoint, network-condition and protected-traffic rules.
- Simulate upload/download bandwidth, latency and other configured conditions with reusable profiles,
  destination/protocol matching and measured throughput monitoring.
- Keep protected TLS connections visible as metadata records, with automatic fallback after eligible
  client certificate rejection and controls to inspect, tunnel or block future connections.
- Inspect HTTP, GraphQL, WebSocket, GraphQL WebSocket, gRPC and SSE through the updated protocol views.
  GraphQL tooling handles URL/body request parameters and observed persisted-query documents.
- Export Traffic selections and API Studio executions, and use grouped copy presets for developer
  workflows. File import and drag-and-drop open the import review flow.
- Use smart/manual traffic scrolling, unseen-request tracking and updated shared controls.
- Improve connection cleanup, tunnel lifecycle/closure diagnostics, large-body scrolling and
  stability while switching selected traffic.

### Compatibility and scope

- Use KNet Companion 0.2.0 with this desktop release for the coordinated device-session changes.
- macOS and Windows packages remain unsigned development distributions.
- Android packet-level shaping remains experimental; desktop/iOS packet adapters are unavailable.
  Proxy-layer conditions and encrypted-tunnel metadata do not expose decrypted protected payloads.
- The public KNet website links to this repository's Releases page.
