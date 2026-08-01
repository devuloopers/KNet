# Core Pairing Module — `:core:pairing`

**Target Module:** `core/pairing/`  
**Gradle Module:** `:core:pairing`  
**Package Namespace:** `com.devuloopers.knet.core.pairing`  
**Platform:** Kotlin Multiplatform (`commonMain`, `commonTest`)  
**Status:** Placeholder / Future Capability Module

---

# 📌 Purpose & Overview

`:core:pairing` is reserved as the shared Kotlin Multiplatform library for Desktop ↔ Mobile Companion app pairing and discovery.

When Mobile Companion app development begins in future phases, this module will contain shared DTOs and contracts, including:

- **`DeviceInfo`**: Device metadata (device name, OS version, IP address, port, device type).
- **`PairingRequest`**: Handshake initiation payload sent during pairing attempts.
- **`PairingResponse`**: Handshake acceptance/rejection response payload.
- **`DiscoveryPacket`**: UDP broadcast/mDNS packet structure for local network discovery.
- **`QrPayload`**: Camera scan payload format for instant pairing.

---

# 🚫 Current Status

Mobile Companion is currently out of immediate scope. This directory serves as an architectural placeholder matching **KNet Architecture (v2.0)**.
