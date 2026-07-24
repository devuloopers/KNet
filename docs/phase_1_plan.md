# KNet Phase 1 Plan: Cryptography & CA Management (certificateManager) [COMPLETED]

**Status**: Completed

This document specifies the exact design, files, dependencies, and implementation details for Phase 1 of KNet development, which establishes the Certificate Authority (CA) system and MITM decryption requirements.

---

## 1. Module Registration and Build Infrastructure

### 1.1 settings.gradle.kts
Register the `certificateManager` module:
```kotlin
include(":desktopApp")
include(":shared")
include(":certificateManager")
```

### 1.2 gradle/libs.versions.toml
Define BouncyCastle dependencies:
```toml
[versions]
bouncycastle = "1.85"

[libraries]
bouncycastle-prov = { module = "org.bouncycastle:bcprov-jdk18on", version.ref = "bouncycastle" }
bouncycastle-pkix = { module = "org.bouncycastle:bcpkix-jdk18on", version.ref = "bouncycastle" }
```

### 1.3 certificateManager/build.gradle.kts
Set up the subproject targeting the JVM:
```kotlin
plugins {
    kotlin("jvm") version "2.4.0"
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    testImplementation(kotlin("test"))
}
```

---

## 2. Core Cryptography Architecture

All classes will reside in the `com.devuloopers.knet.crypto` package.

### 2.1 CertificateAuthority
Responsible for the generation, persistence, and loading of the Root CA certificate and private key.
* **Fields**:
  * `privateKey: PrivateKey`
  * `certificate: X509Certificate`
* **Methods**:
  * `generate(commonName: String, org: String, validityDays: Int): CertificateAuthority`: Generates a new Root CA (RSA 4096-bit key pair) self-signed certificate valid for the specified days.
  * `saveToPem(certFile: File, keyFile: File)`: Exports the certificate and private key as PEM files.
  * `loadFromPem(certFile: File, keyFile: File): CertificateAuthority`: Loads the Root CA from existing PEM files.

### 2.2 LeafCertificateGenerator
Dynamically generates leaf certificates for targeted remote domains signed by KNet's Root CA.
* **Inputs**:
  * `hostname: String` (e.g., `*.github.com` or `google.com`)
  * `ca: CertificateAuthority`
* **Outputs**:
  * Dynamic `KeyPair` and `X509Certificate` valid for the target host.
* **Logic**:
  * Extract Subject Alternative Names (SAN) from the hostname.
  * Generate a new RSA 2048-bit or ECDSA KeyPair for the leaf.
  * Construct and sign the X.509 v3 certificate containing the target SNI in the Common Name (CN) and SAN fields.

### 2.3 CertificateCache
In-memory cache for leaf certificates to prevent dynamics-signing delays on consecutive handshakes.
* **Underlying Structure**: ConcurrentHashMap mapping hostnames to `KeyPair` and `X509Certificate` pairs.
* **Eviction Policy**: Clean up expired certificates periodically or configure a soft-reference based memory model.

### 2.4 TrustStoreInstaller
A cross-platform installer to insert the KNet Root CA certificate into the system trust stores.
* **Supported Environments**:
  * **Windows**: Invokes `certutil -addstore -user ROOT knet_root_ca.crt` via PowerShell/CMD.
  * **macOS**: Invokes `security add-trusted-cert -d -r trustRoot -k ~/Library/Keychains/login.keychain knet_root_ca.crt` via process execution.
  * **Linux**: Copies the cert to `/usr/local/share/ca-certificates/` and runs `update-ca-certificates`.

---

## 3. Verification Plan

A suite of unit tests in `certificateManager/src/test/kotlin` will assert the following expectations:
1. **Root CA Generation**: Assert that a generated CA certificate is self-signed, is marked as a Basic Constraint Certificate Authority, has correct metadata (CN, ORG), and has a valid key size.
2. **Leaf Signing Validity**: Generate a leaf certificate for `example.com`. Assert that it is signed by the Root CA, contains `example.com` in its SAN fields, and matches the validity period constraints.
3. **Cache Performance**: Assert cache hit and cache miss sequences, confirming that cached domain queries return identical certificate instances.
4. **Installer Pre-flight Checks**: Assert OS detection logic behaves correctly.
