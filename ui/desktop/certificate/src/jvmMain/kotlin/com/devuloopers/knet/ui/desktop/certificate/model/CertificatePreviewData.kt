package com.devuloopers.knet.ui.desktop.certificate.model

/**
 * Dummy preview object used for UI checks before wiring real engine data.
 */
public object CertificatePreviewData {
    public val state: CertificateState = CertificateState(
        caStatus = CaStatus.AVAILABLE,
        caDetails = CaDetails(
            subject = "CN=KNet Local Authority (Development)",
            issuer = "CN=KNet Local Authority (Development)",
            serialNumber = "04:D3:2F:B1:8C:7A",
            signatureAlgorithm = "SHA256withRSA",
            validFrom = "2023-01-01T00:00:00Z",
            validUntil = "2033-01-01T00:00:00Z",
            sha1Fingerprint = "1B:2C:3D:4E:5F:6G",
            sha256Fingerprint = "11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00"
        ),
        trustState = TrustInstallationState.INSTALLED,
        clientCertificates = listOf(
            ClientCertificate(
                alias = "banking-api-prod-cert",
                subject = "CN=banking-api",
                host = "*.api.internal.bank.com",
                expiration = "2025-01-01",
                enabled = true,
                format = CertificateFormat.PKCS12,
                daysUntilExpiration = 420,
                subjectDn = "CN=banking-api",
                issuerDn = "CN=Internal Banking CA",
                serialNumber = "1A:2B:3C:4D",
                sanList = listOf("DNS:*.api.internal.bank.com"),
                publicKeyAlgorithm = "RSA 2048-bit",
                sha256Fingerprint = "AA:BB:CC:DD"
            ),
            ClientCertificate(
                alias = "legacy-payment-gateway",
                subject = "CN=legacy-payment",
                host = "gw.payments.legacy.net",
                expiration = "2023-12-01",
                enabled = false,
                format = CertificateFormat.PEM,
                daysUntilExpiration = 12,
                subjectDn = "CN=legacy-payment",
                issuerDn = "CN=Legacy CA",
                serialNumber = "5E:6F:7G:8H",
                sanList = listOf("DNS:gw.payments.legacy.net", "IP:192.168.1.100"),
                publicKeyAlgorithm = "RSA 4096-bit",
                sha256Fingerprint = "11:22:33:44"
            )
        ),
        mtlsRules = listOf(
            MtlsRule(
                ruleName = "Bank API Rule",
                hostPattern = "*.api.internal.bank.com",
                certificateAlias = "banking-api-prod-cert",
                enabled = true
            )
        ),
        activeTab = CertificateTab.CLIENT_CERTS,
        activeSidebarItem = CertificateSidebarItem.ROOT_CAS
    )
}
