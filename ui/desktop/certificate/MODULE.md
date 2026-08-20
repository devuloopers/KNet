# `:ui:desktop:certificate`

## Responsibility

Owns desktop certificate-management, trust guidance, CA details, mTLS rules, and client-certificate presentation.

## Owns

- Certificate screens, responsive inline/side-drawer layouts, dialogs, ViewModel, UI status/intent state, and rendering.
- Serialized mutation/refresh coordination, confirmation state, complete manual trust instructions, operation progress, and stale-selection prevention.
- A testable desktop client-identity file-picker boundary. The primary implementation uses the host operating system dialog on macOS, Windows, and Linux, with Swing retained only as a fallback; both stay outside the import composable.

## Does not own

- Key generation/storage internals, trust-store mutation logic, Apple profiles, proxy TLS handling, or product DI bindings.
- A second CA, client-certificate, certificate-format, or mTLS-rule model.

## Dependency rule

Invoke `CertificateManagementPort` and connectivity use cases; never depend on concrete cryptographic implementations.

## Current state

All certificate operations cross `CertificateManagementPort` and render its canonical
`CertificateAuthoritySummary`, `ClientCertificateSummary`, and `MtlsRuleSpec` values directly. The
old proxy-pipeline mobile setup widget and portal dependency are removed. The single CA runtime and
feature assembly live in `:products:desktop` under `di/certificate`.
Wide layouts retain the Root CA sidebar and inline certificate inspector. Compact layouts retain the same
information in the shared side drawer, with trust and certificate drawers kept mutually exclusive. Lists and
the certificate viewer expose overflow scrollbars; all visible help, rule toggle/edit/delete, identity
toggle/delete/import, refresh, trust, and confirmation actions are wired.
