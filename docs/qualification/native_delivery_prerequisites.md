# Native network protection delivery inventory

Reviewed: 2026-11-10. Status: **architecture and prerequisites recorded; adapters not delivered**.

This records C00/C05–C07 dependencies. It does not promote native platforms based on JVM loopback or Simulator
compilation. Missing implementation remains software work; missing signing authority is an external delivery
blocker. No host route, driver, VPN or system extension was installed or activated during this work.

| Product path | Current implementation / selected integration boundary | Build and acceptance lane |
| --- | --- | --- |
| Android API 26+ | Existing `VpnService`, `com.zaneschepke:hevtunnel:1.0.1`, local protected SOCKS gateway. Extend a pinned native bridge to expose original tuples before translation; the present AAR boundary does not supply that callback. | Host tests and APK/lint gate exist. Test actual native ABI builds and owner callbacks before attribution promotion; physical VPN acceptance stays separate. |
| iOS/iPadOS 16+ | Existing `NEPacketTunnelProvider`; HEV 2.16.0 pinned to `0a05221275a51a884d93328c55fc2fbc9e9b6974`. Keep packet scheduling/flow observation in the provider, before translation, with destination-only identity where the OS exposes no verified application. | arm64 device compilation and arm64 Simulator test/framework linking exist. Native UDP gateway, original-tuple bridge and full behavior tests are unfinished. Signed app/extension archive is blocked on account/profiles. |
| macOS arm64 / x64 | Selected platform facility: signed Network Extension, with packet-provider routing and an authenticated local JVM control bridge. An app/transparent-proxy provider is a separate attribution-capable mode and must advertise its actual capabilities. | Product packaging lanes use macOS 15 runners. The native extension/helper/IPC product and its tests are not implemented; current JVM packages do not contain it. Minimum supported OS and each mode's availability must be qualified explicitly. |
| Windows x64 | Selected packet interface: official signed Wintun DLL through its published API, with a narrowly privileged native helper and authenticated JVM IPC. Keep driver ownership, routing rollback and translator outside UI/domain code. | Existing desktop packaging uses Windows Server 2025. Adapter/helper/installer/recovery tests and Windows client OS support qualification are unfinished. |
| Linux x64 / arm64 | Selected packet interface: `/dev/net/tun`, accessed by a narrowly privileged helper, with explicit control-plane route exclusions and restoration of only KNet-owned routes. Reuse the pinned translator strategy after callback/ABI qualification. | Existing packaging uses Ubuntu 24.04 x64/arm64. Privilege boundary, native adapter, routing rollback, supported-kernel matrix and distribution packaging tests are unfinished. |

Apple's [Network Extension entitlement instructions](https://developer.apple.com/documentation/bundleresources/entitlements/com.apple.developer.networking.networkextension)
require the appropriate capability, provisioning profile and signing identity. Previous local device-build attempts
reported no configured Xcode account and missing profiles for `com.devuloopers.knet.companion` and its
`.PacketTunnel` extension. Unsigned Simulator output cannot close that archive/signature prerequisite. No credentials
are stored in this report or requested through code.

The [HEV upstream project](https://github.com/heiher/hev-socks5-tunnel) documents TCP/UDP and IPv4/IPv6 support and
an MIT license. That describes upstream scope, not proof that KNet's current platform wrappers implement every
path. Any maintained callback patch needs a pinned source revision, ABI tests, notices and native build artifacts.

[Wintun's official distribution](https://www.wintun.net/) separates its source license from the prebuilt-binary
license. Version 0.14.1 was downloaded to temporary review storage and its published SHA-256 matched
`07c256185d6ee3652e09fa55c0b673e2624b565e02c4b9091c79ca7d2f24ef51`. The archive's `LICENSE.txt` permits the
specified alongside-software API use subject to its terms; packaging must retain the exact license/notices and
use the unmodified supported signed DLL. Nothing was added to KNet's distribution or executed. Native signature,
installer and redistribution-manifest validation remain required deliverables.

The [Linux kernel TUN documentation](https://docs.kernel.org/networking/tuntap.html) describes the file-descriptor
interface and required device-creation capability. The JVM UI must not acquire blanket root privileges or execute
arbitrary route commands from user input. Helper permission, bounds, rollback, crash recovery and peer loss are
software test cases, followed by physical OS authorization/routing acceptance.

## Bandwidth ownership contract

The current JVM engine shares its directional global/rule budgets across proxy channels and HTTP/2 streams. Each
Android gateway currently owns a separate local UDP budget. **These are not yet a coordinated aggregate budget
across TCP, locally forwarded UDP and multiple devices.** Separate measured TCP and UDP rates cannot establish
that combined global cap, and the graph does not yet include locally released UDP bytes.

The version-two policy/control work must introduce one desktop budget coordinator per directional policy scope,
with bounded expiring reservations for local proxy and native consumers. Session/revision identity fences grants;
expired/stale grants cannot mint fresh credit, and disable/Offline recovery cannot release stale reservations as
a burst. Device disconnect, unused quota, changed rules, fairness, control-path exclusion and lost acknowledgements
need deterministic clock and real mixed-transport tests. Native adapters cannot each receive the entire advertised
global cap. This contract is assigned to C03–C05; no coordinator is claimed as implemented here.
