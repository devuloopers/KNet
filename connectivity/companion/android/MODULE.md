# `:connectivity:companion:android`

## Responsibility

Adapts shared companion network and inspection contracts to Android connectivity and VPN permission lifecycle.

## Owns

- `ConnectivityManager` observation.
- The merged `ACCESS_NETWORK_STATE` manifest permission required by network observation; it is a
  normal permission and does not require a runtime consent flow.
- `VpnService.prepare` consent checks.
- A deterministic inspection controller over a replaceable Android packet/VPN backend.

## Does not own

- Shared policies/use cases/UI state, pairing persistence, desktop proxy/capture, Compose UI, or native
  tun2socks implementation. The packet backend is a separate implementation dependency so it can be qualified
  and replaced without changing application or presentation layers.

## Dependency rule

May depend only on shared companion application/core contracts and Android SDK APIs. A product composition root
supplies the concrete packet backend and effect handlers.
