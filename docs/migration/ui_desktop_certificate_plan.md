# UI Desktop Certificate Module Plan — `:ui:desktop:certificate` (Phase 7)

**Target Module:** `ui/desktop/certificate/`  
**Gradle Module:** `:ui:desktop:certificate`  
**Package Namespace:** `com.devuloopers.knet.ui.desktop.certificate`  
**Platform:** Compose Multiplatform (Desktop JVM)  
**Status:** [COMPLETED] Approved for Migration

---

# 📌 Vision

`:ui:desktop:certificate` is KNet's Certificate & PKI Management UI.

It provides a complete desktop interface for managing KNet's Certificate Authority (CA), trusted certificates, client certificates, mutual TLS (mTLS) configuration, and certificate trust installation.

This module is responsible **only for presentation and user interaction**.

All certificate generation, signing, installation, validation, export, import, and cryptographic operations are delegated to `:engine:certificate`.

---

# 🎯 Responsibilities

The module owns:

## Certificate Authority
- Root CA dashboard
- CA status
- CA details
- CA metadata
- Certificate fingerprints
- Certificate validity information

## Trust Management
- Trust installation wizard
- Platform installation guides
- Trust status
- Installation progress
- Validation results

## Client Certificates
- Client certificate browser
- Import / Export dialogs
- Add/Edit / Delete dialogs
- Certificate selection

## Mutual TLS
- mTLS rule list
- Rule editor
- Rule assignment
- Host matching
- Certificate assignment

## Certificate Viewer
- Certificate details panel
- Subject, Issuer, Validity information
- SAN entries, Fingerprints, Extensions

## UI
- ViewModel
- UDF State & User Intents
- Screen navigation & Dialog management

---

# 🚫 Explicitly Out of Scope

This module MUST NOT contain:
- Certificate / CSR generation
- Private key generation
- Certificate signing
- Root CA creation
- Trust store installation logic
- X509 parsing implementation
- Cryptography
- Proxy logic
- HTTP execution
- Database implementation
- Netty

All runtime functionality belongs to `:engine:certificate`.

---

# 📂 Directory Structure

```text
ui/
└── desktop/
    └── certificate/
        ├── build.gradle.kts
        │
        └── src/
            ├── jvmMain/
            │   └── kotlin/
            │       └── com/devuloopers/knet/ui/desktop/certificate/
            │
            │           ├── model/
            │           │     ├── CertificateState.kt
            │           │     ├── CertificateIntent.kt
            │           │     ├── CaStatus.kt
            │           │     ├── CaDetails.kt
            │           │     ├── CertificateSummary.kt
            │           │     ├── ClientCertificate.kt
            │           │     ├── MtlsRule.kt
            │           │     └── TrustInstallationState.kt
            │           │
            │           ├── overview/
            │           │     ├── CaStatusCard.kt
            │           │     ├── CaDetailsPanel.kt
            │           │     └── CertificateOverview.kt
            │           │
            │           ├── trust/
            │           │     ├── TrustWizard.kt
            │           │     ├── TrustStatusCard.kt
            │           │     └── InstallationGuide.kt
            │           │
            │           ├── client/
            │           │     ├── ClientCertificateList.kt
            │           │     ├── ClientCertificateDialog.kt
            │           │     ├── ImportCertificateDialog.kt
            │           │     └── ExportCertificateDialog.kt
            │           │
            │           ├── mtls/
            │           │     ├── MtlsRuleList.kt
            │           │     ├── MtlsRuleDialog.kt
            │           │     └── HostCertificateMapping.kt
            │           │
            │           ├── viewer/
            │           │     ├── CertificateViewer.kt
            │           │     ├── FingerprintSection.kt
            │           │     ├── ExtensionsSection.kt
            │           │     └── SubjectIssuerSection.kt
            │           │
            │           ├── view/
            │           │     └── CertificateManagerScreen.kt
            │           │
            │           ├── viewmodel/
            │           │     └── CertificateViewModel.kt
            │           │
            │           └── di/
            │                 └── CertificateModule.kt
            │
            └── jvmTest/
                └── kotlin/
                    └── com/devuloopers/knet/ui/desktop/certificate/
                        ├── CertificateViewModelTest.kt
                        ├── CertificateStateTest.kt
                        ├── CaStatusTest.kt
                        ├── CaDetailsTest.kt
                        ├── TrustWizardTest.kt
                        ├── ClientCertificateListTest.kt
                        ├── ClientCertificateDialogTest.kt
                        ├── MtlsRuleTest.kt
                        ├── CertificateViewerTest.kt
                        ├── TrustInstallationStateTest.kt
                        └── MigrationRegressionTest.kt
```

---

# 📦 Dependencies

Depends on:
- `:ui:core`
- `:ui:desktop:shell`
- `:engine:certificate`
- `:core:domain`
- `:core:logger`

Must NOT depend on:
- `:engine:proxy`
- `:core:http`
- SQL
- Netty

---

# ✅ Verification Criteria

- All certificate operations remain UI-only.
- Certificate generation and cryptographic operations are delegated entirely to `:engine:certificate`.
- The module presents Root CA, Trust Store, Client Certificates, and mTLS management through dedicated UI components.
- The ViewModel communicates only with the public API exposed by `:engine:certificate`.
- The module is independently testable.
- No cryptographic or certificate manipulation logic exists in the UI layer.
- Clean Architecture boundaries are preserved.
