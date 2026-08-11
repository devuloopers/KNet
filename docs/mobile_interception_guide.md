# KNet Cross-Platform Mobile Interception Guide (Android & iOS)

This document serves as the authoritative architectural specification and operational guide for inspecting HTTPS traffic from **Android** and **iOS** devices, emulators, and simulators using KNet.

---

## 1. Architectural Overview

Intercepting HTTPS traffic on modern mobile operating systems requires navigating OS-level certificate trust policies, SSL Pinning, and specialized networking protocols (QUIC/HTTP3).

```
+-----------------------------------------------------------------------+
|                           KNet Desktop App                            |
|                                                                       |
|   +---------------------+   +-------------------+   +-------------+   |
|   | Embedded CA Portal  |   | System CA Inject  |   | SSL Bypass  |   |
|   | http://knet.local   |   | ADB / simctl CLI  |   | Passthrough |   |
|   +----------+----------+   +---------+---------+   +------+------+   |
+--------------|------------------------|--------------------|----------+
               |                        |                    |
        User-Agent Auto          Direct System Store    Raw TCP Relay
        (.crt / .mobileconfig)    Certificate Push       (Bypass SSL MITM)
               |                        |                    |
               v                        v                    v
     +-------------------+    +--------------------+  +-----------------+
     | Android / iOS     |    | Android Emulator / |  | Pinned / System |
     | Physical Devices  |    | iOS Simulator      |  | Domains (*.yt)  |
     +-------------------+    +--------------------+  +-----------------+
```

---

## 2. Technical Comparison: Android vs. iOS Interception

| Capability / Challenge | Android Platform Behavior | iOS Platform Behavior |
| :--- | :--- | :--- |
| **Default User CA Trust** | Untrusted by native apps in Android 7.0+ (API 24+) by default. Requires System CA or `network_security_config.xml`. | Requires installing Profile, then manually toggling **Full Trust for Root Certificates**. |
| **Emulator Automation** | `adb root` + `adb push` to `/system/etc/security/cacerts/<hash>.0`. | `xcrun simctl keychain <uuid> add-cert knet-ca.pem`. |
| **Physical Device Download** | Download `.crt` via `http://knet.local` / QR Code. | Download `.mobileconfig` Apple Configuration Profile via `http://knet.local` / QR Code. |
| **SSL Pinning** | Enforced by YouTube, Google Play Services, Banking apps. | Enforced by Apple System Services (`*.apple.com`, `*.icloud.com`). |
| **Mitigation** | System CA injection (rooted/emulator) or SSL Bypass rules in KNet. | Simulator Keychain injection or SSL Bypass rules in KNet. |

---

## 3. Phase-by-Phase Implementation Design

### Phase 1: Embedded Mobile CA Portal (`http://knet.local`)
- **Port**: Listens on Netty Proxy Engine HTTP port.
- **URLs Intercepted**: `http://knet.local`, `http://<desktop-ip>:<port>/ca`, `/knet-ca.crt`, `/knet-ca.mobileconfig`.
- **User-Agent Detection Logic**:
  - `User-Agent` contains `iPhone`, `iPad`, `iPod`, or `CFNetwork`: Serves `application/x-apple-aspen-config` (`knet-ca.mobileconfig`).
  - `User-Agent` contains `Android` or default: Serves `application/x-x509-ca-cert` (`knet-ca.crt`).

### Phase 2: Domain-Based SSL Passthrough (Bypass Engine)
- **Problem**: Pinned system services (like YouTube or Apple Push Services) throw `SSLHandshakeException` (`certificate_unknown`) when intercepted.
- **Solution**: KNet evaluates `CONNECT host:port` requests against `sslBypassDomains`.
- **Presets**:
  - **Android System**: `*.youtube.com`, `*.googlevideo.com`, `*.google-analytics.com`
  - **iOS System**: `*.apple.com`, `*.icloud.com`, `*.push.apple.com`, `*.mzstatic.com`
- **Behavior**: When a host matches an active bypass rule, Netty establishes a direct TCP relay without installing SSL MITM handlers.

### Phase 3: Automated ADB & `simctl` CLI Tooling
- **Android Emulators**: Automates computing the OpenSSL subject hash of KNet's Root CA (`openssl x509 -inform PEM -subject_hash_old`) and running ADB system store injection.
- **iOS Simulators**: Detects booted simulators via `xcrun simctl list` and executes `xcrun simctl keychain booted add-cert knet-ca.pem`.

### Phase 4: Certificate Studio UI Integration
- Displays **Mobile Setup Center** with interactive QR code pointing to `http://<desktop-ip>:<port>/ca`.
- Provides 1-click **Inject Android System CA** and **Inject iOS Simulator Certificate** buttons.

---

## 4. Developer Reference Snippets

### Android `network_security_config.xml` (For Custom Android Development Builds)
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <debug-overrides>
        <trust-anchors>
            <!-- Trust KNet User CA in debug builds -->
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

### Apple `.mobileconfig` Payload Structure
```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>PayloadContent</key>
    <array>
        <dict>
            <key>PayloadCertificateFileName</key>
            <string>knet-ca.crt</string>
            <key>PayloadContent</key>
            <data><!-- BASE64 DER CERTIFICATE --></data>
            <key>PayloadType</key>
            <string>com.apple.security.root</string>
            <key>PayloadVersion</key>
            <integer>1</integer>
        </dict>
    </array>
    <key>PayloadDisplayName</key>
    <string>KNet Root CA</string>
    <key>PayloadType</key>
    <string>Configuration</string>
    <key>PayloadVersion</key>
    <integer>1</integer>
</dict>
</plist>
```
