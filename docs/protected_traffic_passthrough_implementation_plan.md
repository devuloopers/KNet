# Protected Traffic Passthrough and Opaque Flow Capture

Status: **IN PROGRESS**

Target phase: Phase 135

Last updated: 2026-10-01

## Implementation progress (2026-10-01)

Phase 135 remains **in progress**. The repository now contains automated evidence for these slices:

- 135.1: persisted policy model, deterministic selectors/precedence, Google Play and Apple StoreKit compatibility
  groups, global/group/rule controls, and the dedicated **Protected Traffic** sidebar workspace;
- 135.2: bounded ClientHello SNI/ALPN/TLS-version classification before certificate creation, explicit
  inspect/tunnel/block decisions, a backpressure-aware raw TLS relay, and origin-certificate preservation tests;
- 135.3: canonical opaque-flow persistence, mixed HTTP/opaque chronological keyset paging, metadata-only Traffic
  rows/details, store-side semantic type filters, byte counters, exact action/rule/group evidence, and one-click
  application/destination policy actions;
- protected raw tunnels now retain applied Network Conditions evidence, share the aggregate Streaming 100 kbps
  budget, preserve queued bytes across half-close, and have a real-socket timing/correctness regression;
- rejected downstream interception handshakes now create typed metadata-only `TLS Rejected` rows with likely-pinning
  guidance and an explicit next-connection tunnel action instead of disappearing before an HTTP exchange exists;
- 135.4 boundary work: a strict versioned companion flow-metadata codec, authenticated gateway stripping and
  attribution, Android 10+ original-tuple owner resolution, and fail-closed shared-UID behavior;
- Darwin carrier work: the iOS packet-tunnel proxy can carry the same authenticated flow metadata when a provider
  has verified it, while destination-only operation remains the default.
- hardening audit: additive selector precedence, action-specific opaque Traffic guidance, unified live HTTP/opaque
  chronology, schema-v31 evidence persistence, non-destructive retained-schema migrations, and migration
  preservation tests.

The current HEV TUN-to-SOCKS integrations on Android and iOS expose translated loopback sockets rather than the
original application five-tuple. They therefore do **not** yet attach a package/bundle identity; Android reports the
translator capability as `TRANSLATED_SOCKET_ONLY`. Finishing 135.4/135.5 requires a native flow-open tuple callback
before SOCKS translation. Physical Google Play Billing, StoreKit, signed Network Extension, WebRTC, QUIC/HTTP/3,
custom UDP, and physical 100 kbps qualifications also remain open. The signed macOS transparent Network Extension
and entitlement lifecycle remain a separately gated adapter; the JVM explicit-proxy path does not imply them.

## Objective

Keep certificate-pinned, system-owned, mutually authenticated, and otherwise non-decryptable connections working
while making them visible in KNet Traffic as first-class opaque flows. Protected traffic must not silently disappear,
and KNet must not require weakening Google Play, banking, DRM, identity, or other security-sensitive applications.

The feature applies to every protected connection, not only Google Play Billing. Google Play is the first Android
physical qualification workload because `BillingClient` communicates with the Play Store service rather than
issuing the catalog request directly from the application process. StoreKit/App Store is the corresponding Darwin
qualification workload. KNet will show protected network flows that the operating system actually routes through
its provider, not invent network requests for local Binder, XPC, or StoreKit calls.

## Product decisions

1. **Tunnel is not bypass.** The default protected-traffic action keeps the connection inside KNet, preserves
   end-to-end encryption, records bounded metadata, and applies eligible Network Conditions. A complete VPN bypass
   remains a separate last-resort action and cannot claim Traffic visibility or throttling.
2. **No silent security downgrade.** KNet never changes an `Inspect` rule to `Tunnel` merely because a client rejects
   the generated certificate. It records a typed downstream TLS failure and offers an explicit rule action for the
   next connection. Built-in protected-service rules may select `Tunnel` before the first handshake.
3. **Opaque flows are not fake HTTP exchanges.** Persistence and Traffic use a dedicated transport-flow model rather
   than fabricating an HTTP method, status, headers, or body that KNet never observed.
4. **Application identity is the strongest mobile selector.** Android package/UID rules take precedence over broad
   Google-domain rules so an application's own debuggable Google API traffic can remain inspectable.
5. **Metadata is honest and bounded.** Tunnel rows may contain the source application, destination, SNI when visible,
   offered ALPN, transport, timestamps, byte counters, termination reason, and applied Network Conditions. They never
   contain encrypted payload bytes, inferred subscription products, session keys, or fabricated response details.
6. **Network Conditions operate on raw transport bytes.** Bandwidth, latency, offline, reset, and other safe
   stream-level conditions apply before raw tunnel delivery. Packet-only UDP conditions use the Phase 134 data plane.
7. **Platform claims remain independent.** Android application attribution, iOS packet-tunnel behavior, desktop
   explicit-proxy behavior, TCP tunnelling, and UDP/QUIC flow reporting each require their own evidence.

## User-visible behavior

Traffic will contain both decrypted exchanges and opaque flows in one chronological list:

| Traffic type | Example label | Payload | Expected behavior |
|---|---|---|---|
| Decrypted HTTP | `GET /subscriptions` | Headers/body available | Existing KNet inspection |
| Protected TLS tunnel | `TLS play.googleapis.com:443` | Unavailable by design | End-to-end TLS, metadata and counters visible |
| Opaque TCP | `TCP host:port` | Unavailable | Raw ordered tunnel with bounded counters |
| Opaque UDP/QUIC | `QUIC/UDP host:443` | Unavailable | Packet-flow metadata where the platform adapter supports it |
| Failed TLS attempt | `TLS handshake rejected` | Unavailable | Typed cause and an explicit `Always tunnel...` action |

The details drawer for an opaque flow contains:

- application label, package identifier, and attribution availability;
- destination hostname/SNI, resolved address, port, IP family, and transport;
- interception decision and the rule that selected it;
- TLS ClientHello metadata that is safe to expose, including visible SNI, offered ALPN, and supported TLS versions;
- start, connected, first-byte, end, and duration timing where measurable;
- uploaded/downloaded bytes, current state, and termination reason;
- applied Network Conditions profile/rule and conditioned byte counts;
- a prominent explanation that headers and bodies are unavailable because KNet preserved end-to-end encryption.

Traffic filters add `Decrypted`, `TLS tunnel`, `Opaque TCP`, `UDP/QUIC`, `Protected`, and `Failed`. Context actions add
`Always tunnel this application`, `Always tunnel this destination`, `Attempt inspection`, `Block`, `Add to Network
Conditions...`, and `Copy destination`. Actions that cannot affect an already-open flow state that they apply to the
next connection.

## Interception policy

### Actions

- `INSPECT`: terminate downstream TLS using the KNet CA and capture supported application protocols.
- `TUNNEL`: relay bytes unchanged while capturing transport metadata and counters.
- `BLOCK`: reject the connection with a typed policy outcome.
- `BYPASS`: allow platform-owned direct routing outside KNet. This is explicit, visibly reduces capability, and is
  used only when a KNet-carried tunnel is not viable.

### Selectors and precedence

Rules support application package/UID, exact host, wildcard host, IP/CIDR, port, IP family, and TCP/UDP. Matching is
normalized, deterministic, and explainable. Highest precedence wins in this order:

1. exact application plus exact destination/port;
2. exact application rule;
3. exact host/IP plus port;
4. exact host/IP;
5. wildcard domain or CIDR plus optional port;
6. built-in protected-service group;
7. global default.

An explicit `BLOCK` does not receive hidden priority over a more specific rule; specificity is resolved first, then
the selected rule's action is applied. Duplicate effective selectors are rejected. Live rule changes apply to new
flows; existing tunnels retain their opening decision so a TLS stream is never reclassified mid-connection.

### Built-in compatibility groups

The first built-in group is **Google Play Billing**:

- tunnel `com.android.vending` traffic when Android can prove the source UID/package;
- optionally include `com.google.android.gms` and `com.google.android.gsf` through a separately visible broader
  Google system-services switch rather than silently tunnelling them;
- fall back to a maintained destination/SNI rule set only when source attribution is unavailable;
- never apply a blanket `*.google.com` exception that would hide unrelated application traffic;
- keep the group visible, versioned, and user-disableable.

Future groups may cover platform stores, push-notification services, DRM/license services, and user-selected pinned
applications. Built-in rules carry stable IDs so updates do not overwrite user overrides.

The first Darwin group is **Apple App Store and StoreKit**:

- prefer a verified source signing identifier when a flow-oriented Network Extension provides one;
- otherwise use a maintained destination/SNI/IP fallback only for flows routed through the KNet provider;
- keep APNs, iCloud, Private Relay, DRM, and broader Apple services in separately visible groups rather than hiding
  every `*.apple.com` connection;
- never claim that an opaque Apple service flow is the exact network consequence of one StoreKit API call unless the
  platform supplies a trustworthy correlation identifier;
- retain an explicit unavailable state when Apple excludes a system service from the selected provider mode.

## Architecture and ownership

### Domain and application

`:core:traffic` owns transport-flow identity, lifecycle, protocol/mode, safe TLS metadata, byte counters, attribution,
and termination semantics. `:core:domain` owns normalized passthrough selectors, rule precedence, built-in group IDs,
and pure policy evaluation. Neither module depends on Netty, Android, storage, Compose, or companion transport types.

`:application:desktop` owns rule use cases and the policy snapshot consumed by runtime adapters. The application
contract returns one explainable decision containing the matched rule/group ID. `:application:companion` owns the
versioned, bounded policy/attribution envelope shared with paired companions.

### Desktop proxy engine

`:engine:proxy` gains an injected `TlsInterceptionPolicy` port. CONNECT handling performs bounded ClientHello
inspection before certificate creation:

1. validate CONNECT authority and retain the exact socket route;
2. buffer only the configured maximum ClientHello bytes with the existing handshake deadline;
3. derive visible SNI/ALPN/TLS metadata without persisting raw ClientHello bytes;
4. resolve `INSPECT`, `TUNNEL`, or `BLOCK`;
5. for `INSPECT`, continue through the existing SNI TLS context and HTTP/ALPN pipeline;
6. for `TUNNEL`, use the existing bounded upstream dialer, install a backpressure-aware raw duplex relay, and send the
   original ClientHello bytes exactly once;
7. publish lifecycle, counters, timing, policy evidence, and typed failures through capture ports.

The raw tunnel supports half-close, cancellation, upstream connect errors, connection limits, IPv4/IPv6, TLS 1.2,
TLS 1.3, mTLS, HTTP/1.1 inside TLS, HTTP/2 inside TLS, and arbitrary TCP payloads without attempting to parse them.
Encrypted ClientHello or absent SNI falls back to application, CONNECT authority, IP/CIDR, and port selectors. KNet
does not claim a hidden hostname when no reliable identity exists.

### Android Companion

The Android VPN path adds a platform-owned flow attribution boundary. On Android 10/API 29 and later the active VPN
may use `ConnectivityManager.getConnectionOwnerUid()` with the original TCP/UDP five-tuple, then resolve the UID to
an installed package. Because the current TUN-to-SOCKS adapter loses the original application identity before its
loopback SOCKS connection reaches Kotlin, the adapter must expose a bounded flow-open metadata callback or an
equivalent tuple correlation channel before package-aware policy can be considered implemented.

The companion sends authenticated, versioned source metadata with each desktop tunnel request. Desktop never trusts
an arbitrary package string from an unpaired client. Unknown/older-platform attribution is explicit and falls back to
destination rules. Package labels are presentation metadata; stable package IDs drive policy.

Google Play Store remains inside the VPN and uses `TUNNEL`, rather than being added to
`VpnService.Builder.addDisallowedApplication`, so its protected connections can appear on the desktop. KNet's own
control, pairing, certificate, and tunnel sockets remain protected from VPN recursion and excluded from capture.

### Darwin: iOS and iPadOS

The existing `:products:companion:iosPacketTunnel` Kotlin/Native runtime and the Swift
`NEPacketTunnelProvider` entry shim remain the iOS/iPadOS full-device packet boundary. Phase 135 extends that runtime
instead of creating a parallel VPN product:

- classify TCP ClientHello and opaque UDP flow metadata before the loopback SOCKS translation loses the original
  tuple;
- send authenticated, versioned flow-open/counter/close metadata to the desktop without sending payload copies over
  the control channel;
- route protected TCP through the desktop raw `TUNNEL` path so it remains visible and conditionable;
- retain end-to-end TLS and client-certificate behavior exactly as supplied by the source process;
- use domain/SNI/IP/CIDR/port policy whenever reliable source-application metadata is unavailable;
- preserve IPv4/IPv6, DNS continuity, extension memory limits, cancellation, and control-plane route exclusions.

A consumer full-device `NEPacketTunnelProvider` must not be assumed to provide source bundle identity for every IP
packet. Apple flow-oriented providers expose `NEFlowMetaData`, including a source signing identifier and audit token,
but iOS per-app deployment is normally tied to managed/MDM configuration. KNet therefore exposes attribution as a
capability:

- `VERIFIED_SIGNING_ID`: policy may match the signing identifier and stable unique identifier;
- `MANAGED_APP_RULE`: policy may match the MDM/per-app rule identity;
- `DESTINATION_ONLY`: policy uses SNI/host/IP/port and clearly labels the source application unavailable;
- `UNAVAILABLE`: the OS did not route the flow through KNet, so no Traffic or Network Conditions claim is made.

An optional future `NEAppProxyProvider` adapter may add flow-level app attribution for supported managed deployments,
but it is not a prerequisite for honest full-device packet-tunnel visibility. The app-proxy and packet-tunnel modes
must share policy and Traffic contracts without running simultaneously or double-capturing a connection.

StoreKit APIs remain local framework/service calls. Physical qualification verifies that the resulting App Store
network activity, when routed through KNet, works as an opaque protected tunnel and that the tested application's own
receipt-validation and entitlement backend remains independently inspectable.

### Darwin: macOS

macOS has two independently promoted paths:

1. **Explicit proxy.** Applications that honor the configured HTTP/SOCKS proxy use the shared JVM/Netty raw tunnel.
   This requires no new Apple packet provider and must be completed with Phase 135.2.
2. **Transparent system capture.** Applications and system services that do not honor an explicit proxy require a
   signed Network Extension, using a supported flow-oriented provider such as `NETransparentProxyProvider` or
   `NEAppProxyProvider`. This is a separate native product adapter and capability gate.

The transparent adapter lives behind a new platform contract; `:engine:proxy`, `:core:*`, and shared application
modules never import NetworkExtension, XPC, or Swift types. The signed extension owns Apple lifecycle and entitlement
handling, obtains verified `NEFlowMetaData` when available, and passes bounded flow metadata plus raw TCP/UDP streams
to the JVM process through an authenticated local IPC boundary. The bridge rejects untrusted peers, prevents desktop
self-recursion, and survives either side terminating independently.

The macOS adapter must define and test installation approval, entitlement absence, extension activation/deactivation,
app/system-extension upgrades, user logout, sleep/wake, route and interface changes, competing VPN/proxy software,
captive portals, local-network destinations, IPv4/IPv6, and uninstall cleanup. The desktop UI reports explicit-proxy
and transparent-extension capabilities separately; installing KNet desktop does not imply that transparent capture is
authorized or active.

Mac Catalyst may reuse only capabilities proven for its actual packaging. watchOS, tvOS, and visionOS are outside this
phase because KNet has no corresponding product; their shared Darwin kernel does not constitute qualification.

### UDP and QUIC

Opaque UDP flows use the existing Phase 134 protected socket path. The companion emits bounded flow-open, counter,
and close events over the authenticated control channel so the desktop can display them without receiving or storing
payloads. QUIC is labelled only when packet evidence is sufficient; KNet does not claim decoded HTTP/3 requests.
DNS continuity remains independently protected and is not reclassified as application traffic.

On Darwin, UDP visibility follows the active provider: the iOS/iPadOS packet tunnel observes routed packets, while a
macOS flow-oriented provider observes only the flows Apple delivers to it. QUIC connection migration, changing remote
addresses, and connection-ID correlation must not create duplicate logical rows when bounded evidence can associate
the flow; otherwise KNet records separate flows rather than guessing. Network Extension providers do not imply that
iCloud Private Relay traffic is available, and KNet reports that platform interaction explicitly.

### Persistence and Traffic

`:storage` adds versioned opaque-flow and protected-rule tables through an explicit Room migration. Flow updates use
monotonic counters and terminal compare-and-set semantics so late close events cannot resurrect completed records.
Capture-session rotation, proxy shutdown, process recovery, and retention cleanup apply to HTTP exchanges and opaque
flows consistently.

`:data:desktop` maps engine/companion flow events into canonical persistence. `:ui:desktop:traffic` consumes a sealed
row model that keeps HTTP-specific controls off opaque rows. Search, filtering, clearing, export, and session counts
must include opaque records without attempting body loading.

## Network Conditions integration

- `TUNNEL` TCP bytes pass through the aggregate Phase 133 upload/download budgets and bounded queues.
- `TUNNEL` does not opt into HTTP-level fault behavior that would corrupt TLS records; faults are applied at defined
  transport-safe units or by closing/timing out the flow.
- UDP/QUIC uses Phase 134 packet conditions without double-shaping a flow already carried by the desktop proxy.
- Every opaque row records the selected condition profile/rule and conditioned byte counters.
- The 100 kbps streaming preset must produce the same aggregate budget behavior for protected TCP tunnels as for
  inspected proxy traffic.
- `BYPASS` rows, when a platform can report them, explicitly state that KNet conditions were not applied.

## Diagnostics and failure handling

Typed outcomes include downstream certificate rejection, malformed/oversized ClientHello, policy block, upstream
connect failure, upstream TLS alert observed as opaque bytes, read/write timeout, queue limit, companion disconnect,
VPN stop, and process interruption. A likely-pinning diagnosis is presented as a hypothesis, not certainty.

KNet may suggest creating a tunnel rule after a downstream TLS failure, but it cannot transparently replay the failed
TLS connection. The user or client retries after the rule is applied. Repeated suggestions are deduplicated by
application/destination and expire without creating policy automatically.

## Delivery slices

### 135.1 — Policy model and persistence

- Add `INSPECT`, `TUNNEL`, `BLOCK`, and `BYPASS` actions, normalized selectors, deterministic precedence, built-in
  compatibility-group IDs, repositories, use cases, Room migration, and corrupt-state fallback.
- Add settings UI for global default, application rules, destination rules, and protected-service groups.
- Keep current inspection behavior as the migration default except for an explicitly enabled Google Play group.

### 135.2 — Desktop raw TLS/TCP tunnel

- Split CONNECT handling into bounded ClientHello classification followed by inspect or raw-tunnel pipelines.
- Reuse the production dialer, admission limits, lifecycle ownership, and proxy shutdown path.
- Add byte/timing/cancellation telemetry and Network Conditions at the raw relay boundary.
- Record a failed TLS-interception attempt even when no HTTP exchange is produced.

### 135.3 — First-class opaque Traffic records

- Add canonical transport-flow models, persistence, repository queries, session cleanup, and Traffic sealed rows.
- Add protected-flow details, filters, rule evidence, context actions, failure guidance, and export-safe metadata.
- Never expose header/body/response controls for opaque records.

### 135.4 — Android source attribution and Google Play Billing

- Preserve the original five-tuple at the TUN boundary and resolve the owner UID/package on supported Android.
- Extend the authenticated companion tunnel envelope with bounded source-app metadata and capability negotiation.
- Add the Google Play Billing compatibility group and broader optional Google system-services group.
- Qualify product/subscription detail retrieval and a license-tester purchase flow on a physical Play-enabled device.

### 135.5 — iOS/iPadOS packet-tunnel protected flows and StoreKit

- Extend `:products:companion:iosPacketTunnel` with bounded flow observation before SOCKS translation, authenticated
  metadata events, destination policy, raw protected TCP tunnelling, and packet-condition integration.
- Add capability-based attribution states rather than fabricating a bundle ID for ordinary packet-tunnel traffic.
- Add the Apple App Store/StoreKit compatibility group without a blanket Apple-domain exception.
- Compile/link Simulator boundaries, then separately build, sign, install, and exercise the extension on physical
  iPhone and iPad hardware with the required Network Extension entitlement.
- Qualify StoreKit sandbox/TestFlight product and subscription loading, purchase/cancel, restore, background recovery,
  and inspectable application-owned receipt/entitlement calls.

### 135.6 — macOS explicit and transparent protected flows

- Qualify protected tunnels through the existing JVM explicit HTTP/SOCKS proxy.
- Add a native, signed Network Extension adapter and authenticated local IPC bridge for transparent system capture.
- Preserve source signing/audit metadata where Apple supplies it and use destination-only policy otherwise.
- Add separate runtime capability states for explicit proxy and transparent extension installation, authorization,
  activation, failure, and unavailable packaging.
- Qualify signing, installation approval, upgrade, sleep/wake, route changes, competing network software, crash
  recovery, and clean uninstall on supported macOS versions and both Apple silicon and Intel where supported.

### 135.7 — Opaque UDP/QUIC visibility

- Publish authenticated companion UDP flow lifecycle/counters without payload transfer.
- Display UDP and evidence-backed QUIC rows with application attribution where available.
- Apply Phase 134 conditions and prove there is no proxy/packet double shaping.
- Qualify QUIC migration/correlation, IPv4/IPv6, and provider-specific Darwin availability without inventing HTTP/3.

### 135.8 — Resilience, privacy, and promotion

- Complete large-flow, malformed-input, resource-release, migration, privacy, accessibility, and physical-device
  matrices.
- Compare protected-flow behavior with Charles tunnel recording and mitmproxy ignored-flow visibility.
- Publish a focused qualification document and update the runtime capability catalog conservatively.

## Automated acceptance gates

The feature is not complete until automated evidence proves:

- selector normalization, duplicates, precedence, user override, built-in group versioning, and restart persistence;
- exact policy evidence for every inspect/tunnel/block decision;
- a pinned test client that fails under MITM, succeeds through `TUNNEL`, and observes the origin certificate unchanged;
- TLS 1.2/1.3, HTTP/1.1, HTTP/2 ALPN, mTLS, absent SNI, IP-literal CONNECT, IPv4, and IPv6 tunnels;
- fragmented, malformed, oversized, slow, and timed-out ClientHello handling within fixed memory/time bounds;
- byte-for-byte raw relay correctness, half-close, backpressure, cancellation, and no duplicate first ClientHello;
- bounded memory and stable throughput with at least 500 MiB offered through slow and concurrent tunnels;
- accurate monotonic byte/timing counters and exactly-once terminal persistence across shutdown and capture rotation;
- typed failed-TLS rows when interception ends before any HTTP request;
- Network Conditions aggregate 100 kbps behavior, latency, offline/recovery, queue bounds, and no double shaping;
- Android API 29+ UID/package attribution, unknown-owner fallback, package removal/update, and multi-user behavior;
- authenticated companion metadata validation, replay resistance, size bounds, and older-peer capability fallback;
- iOS/iPadOS destination-only attribution, optional managed/app-proxy attribution, and honest unavailable states;
- signed iOS packet-tunnel startup/stop, extension memory pressure, provider crash, network change, and reconnect;
- macOS explicit-proxy and transparent-extension capability separation, authenticated IPC, peer loss, and recursion
  prevention;
- verified Darwin signing identifiers/audit tokens where exposed, with no trust in caller-provided bundle labels;
- StoreKit/App Store flows that remain functional through passthrough without fabricated StoreKit-to-flow correlation;
- Darwin IPv4/IPv6, DNS, local-network, captive-portal, sleep/wake, and route-change continuity;
- UDP lifecycle visibility without payload persistence and conservative QUIC labelling;
- Traffic filtering, details, keyboard/accessibility states, clearing, retention, and export behavior;
- control-plane, DNS, KNet Companion, and desktop self-traffic exclusion;
- affected module tests, companion/proxy qualifications, architecture verification, and `git diff --check`.

## Physical and manual qualification

Android promotion requires a Play-enabled physical device and license-tester account proving:

- `BillingClient` connects while KNet inspection is active;
- one-time product and subscription details load successfully;
- the Play purchase sheet opens and a test purchase/cancel path completes without network failure;
- Play Store connections appear as protected tunnel rows with correct package attribution and no decrypted payload;
- the tested application's own inspectable backend/entitlement calls still appear as decrypted HTTP;
- 100 kbps, latency, offline, and recovery conditions visibly affect the protected flow when enabled;
- Wi-Fi/cellular changes, background/foreground, VPN restart, Play Store update, and device reboot recover cleanly.

iOS/iPadOS promotion requires entitlement-signed physical iPhone and iPad evidence proving:

- the existing packet-tunnel extension starts and routes protected TCP plus UDP on Wi-Fi and cellular;
- StoreKit sandbox/TestFlight products and subscriptions load while KNet is active;
- purchase-sheet open/cancel, test purchase, restore, background completion, and network recovery do not fail;
- routed App Store traffic appears as opaque flows when available, without claiming one-to-one StoreKit correlation;
- application-owned receipt-validation and entitlement calls remain normally inspectable where the app trusts KNet;
- source attribution is shown only when Apple provides verified metadata and otherwise reads `Destination only`;
- 100 kbps, latency, offline, and recovery conditions affect eligible protected flows without double shaping;
- extension stop/restart, memory pressure, device sleep/lock, route changes, companion disconnect, and app upgrades
  close or recover records deterministically.

macOS explicit-proxy promotion requires real pinned TLS/mTLS workloads through the packaged JVM application. macOS
transparent-capture promotion additionally requires a correctly signed and entitled native Network/System Extension
on physical supported Macs, proving:

- user approval, activation, revocation, upgrade, login/logout, sleep/wake, and uninstall lifecycle;
- Safari/native apps plus selected system-service protected flows appear without breaking their TLS;
- source signing identity is recorded only from verified `NEFlowMetaData`;
- the JVM/extension IPC authenticates both peers, applies backpressure, and survives independent crashes;
- Wi-Fi/Ethernet/VPN route changes, IPv4/IPv6, local traffic, captive portals, and conflicting proxy/VPN software fail
  visibly and recover without recursion;
- Network Conditions operate on eligible opaque flows with bounded queues and accurate counters.

Simulator compilation/linking and an unsigned local macOS helper are build evidence only. They are not entitlement,
system-extension, StoreKit, or physical packet-path qualification.

## Security and privacy invariants

- Never patch, instrument, root, jailbreak, or disable pinning in third-party/system applications.
- Never log or persist opaque payload bytes, TLS session secrets, payment details, purchase tokens, or DRM material.
- Verify package attribution at the VPN boundary; do not trust caller-provided labels.
- Verify Apple signing identifiers and audit tokens at the Network Extension boundary; do not trust IPC-supplied
  display names or bundle identifiers without provider evidence.
- Preserve upstream certificate verification and end-to-end client/server authentication in `TUNNEL` mode.
- Keep ClientHello buffering, metadata strings, counters, queues, and companion messages explicitly bounded.
- Prevent tunnel recursion and self-targeting using existing protected-socket and proxy loop controls.
- Make inability to inspect content visible; never present an opaque flow as successfully decrypted.

## Explicit non-goals

- Decrypting Google Play, banking, DRM, or other pinned traffic without cooperation from its owner.
- Bypassing certificate pinning through APK patching, Frida, root, jailbreak, or platform-security modifications.
- Displaying `BillingClient` Binder calls as if they were network requests.
- Inferring subscription products, HTTP paths, headers, status codes, or response bodies from encrypted byte patterns.
- Generic QUIC decryption or semantic HTTP/3 capture without protocol/key support.
- Guaranteeing application attribution on platforms or OS versions that do not expose trustworthy ownership data.
- Claiming App Store, StoreKit, APNs, iCloud Private Relay, or other Apple system traffic when the selected provider
  mode does not route it through KNet.
- Treating Simulator, Mac Catalyst, or one Darwin product's evidence as qualification for another Apple platform.

## Competitive baseline

Charles allows SSL decryption to be disabled or scoped and then shows encrypted traffic instead of forcing every
connection through certificate substitution. Mitmproxy provides `ignore_hosts` passthrough and an option to show
ignored non-intercepted flows. Phase 135 targets that protected-flow continuity and visibility, then extends it with
mobile application attribution, explicit policy evidence, typed diagnostics, and Network Conditions integration.

References:

- [Charles SSL certificates](https://www.charlesproxy.com/documentation/using-charles/ssl-certificates/)
- [Charles proxy settings](https://www.charlesproxy.com/documentation/configuration/proxy-settings/)
- [Mitmproxy ignoring domains](https://docs.mitmproxy.org/stable/howto/ignore-domains/)
- [Mitmproxy options](https://docs.mitmproxy.org/stable/concepts/options/)
- [Android `ConnectivityManager.getConnectionOwnerUid`](https://developer.android.com/reference/android/net/ConnectivityManager#getConnectionOwnerUid(int,%20java.net.InetSocketAddress,%20java.net.InetSocketAddress))
- [Android `VpnService.Builder`](https://developer.android.com/reference/android/net/VpnService.Builder)
- [Google Play Billing integration](https://developer.android.com/google/play/billing/integrate.html)
- [Apple `NEPacketTunnelProvider`](https://developer.apple.com/documentation/networkextension/nepackettunnelprovider)
- [Apple `NEAppProxyProvider`](https://developer.apple.com/documentation/networkextension/neappproxyprovider)
- [Apple `NEFlowMetaData.sourceAppSigningIdentifier`](https://developer.apple.com/documentation/networkextension/neflowmetadata/sourceappsigningidentifier)
- [Apple VPN traffic routing](https://developer.apple.com/documentation/networkextension/routing-your-vpn-network-traffic)
- [Apple Network Extension deployment](https://developer.apple.com/documentation/technotes/tn3134-network-extension-provider-deployment)
