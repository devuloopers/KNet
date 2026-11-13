# iOS packet-tunnel runtime

This module is the small Kotlin/Native runtime embedded only in the KNet packet-tunnel app extension.
It owns start-option validation, pinned desktop TLS, the loopback SOCKS5 gateway, NetworkExtension
settings, and the lifecycle of the pinned hev-socks5-tunnel engine.

It intentionally does not depend on Compose, Koin, Ktor, companion persistence, or the main iOS app
framework. Swift remains only as the Apple-required `NEPacketTunnelProvider` entry shim.

## Multi-device workspace and control sessions (2026-11-13)

The packet-tunnel extension owns its Darwin control channel and STOP/close cleanup. Native routes exclude the paired control address to prevent recursion. iOS reports unsupported packet shaping honestly while retaining presence and proxy-side shaping.
