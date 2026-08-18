# `:ui:desktop:certificate`

## Responsibility

Owns desktop certificate-management, trust guidance, CA details, mTLS rules, and client-certificate presentation.

## Owns

- Certificate screens, dialogs, ViewModel, UI status/intent state, and rendering.

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
