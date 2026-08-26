@file:OptIn(kotlinx.cinterop.BetaInteropApi::class, kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.connectivity.http

import com.devuloopers.knet.companion.model.CompanionCertificateProtocol
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.*
import platform.CoreFoundation.CFArrayCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Foundation.*
import platform.Security.*
import platform.posix.memcpy

/** iOS Ktor provider using Darwin URL loading with request-scoped KNet trust anchors. */
internal class IosCompanionKtorClientProvider : CompanionKtorClientProvider {
    override fun create(request: CompanionHttpRequest): CompanionKtorClientHandle {
        val validation = request.security.toIosValidation()
        var securityFailure: CompanionHttpSecurityException? = null
        val client = try {
            HttpClient(Darwin) {
                configureCompanionClient()
                engine {
                    if (validation != null) {
                        handleChallenge { _, _, challenge, completionHandler ->
                            when (val result = validation.validate(challenge)) {
                                IosTlsValidationResult.NotApplicable -> completionHandler(
                                    NSURLSessionAuthChallengePerformDefaultHandling,
                                    null,
                                )
                                IosTlsValidationResult.Accepted -> completionHandler(
                                    NSURLSessionAuthChallengeUseCredential,
                                    challenge.proposedCredential,
                                )
                                is IosTlsValidationResult.Rejected -> {
                                    securityFailure = result.failure
                                    completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                                }
                            }
                        }
                    }
                }
            }
        } catch (failure: Throwable) {
            validation?.close()
            throw failure
        }
        return CompanionKtorClientHandle(
            client = client,
            securityFailure = { securityFailure },
            releasePlatformResources = { validation?.close() },
        )
    }

    private fun CompanionHttpSecurity.toIosValidation(): IosTlsValidation? = when (this) {
        CompanionHttpSecurity.BootstrapRootOnly -> null
        is CompanionHttpSecurity.PinnedRoot -> {
            val rootBytes = rootCertificate.copyBytes()
            if (rootBytes.sha256Hex() != rootCertificateSha256.value) {
                throw CompanionHttpSecurityException.IdentityRejected()
            }
            IosTlsValidation(
                rootCertificate = rootBytes.toSecCertificate()
                    ?: throw CompanionHttpSecurityException.IdentityRejected(),
                expectedRootDer = rootBytes,
                transportIdentitySha256 = transportIdentitySha256.value,
                usePlatformTrust = false,
            )
        }
        is CompanionHttpSecurity.PlatformTrusted -> {
            val rootBytes = expectedRootCertificate.copyBytes()
            if (rootBytes.sha256Hex() != expectedRootCertificateSha256.value) {
                throw CompanionHttpSecurityException.IdentityRejected()
            }
            IosTlsValidation(
                rootCertificate = rootBytes.toSecCertificate()
                    ?: throw CompanionHttpSecurityException.IdentityRejected(),
                expectedRootDer = rootBytes,
                transportIdentitySha256 = transportIdentitySha256.value,
                usePlatformTrust = true,
            )
        }
    }

    private class IosTlsValidation(
        private val rootCertificate: SecCertificateRef,
        private val expectedRootDer: ByteArray,
        private val transportIdentitySha256: String,
        private val usePlatformTrust: Boolean,
    ) {
        fun close() {
            CFRelease(rootCertificate)
        }

        fun validate(challenge: NSURLAuthenticationChallenge): IosTlsValidationResult {
            if (challenge.protectionSpace.authenticationMethod != NSURLAuthenticationMethodServerTrust) {
                return IosTlsValidationResult.NotApplicable
            }
            val trust = challenge.protectionSpace.serverTrust
                ?: return IosTlsValidationResult.Rejected(CompanionHttpSecurityException.TrustRejected())
            if (!configureHostnamePolicy(trust)) {
                return IosTlsValidationResult.Rejected(CompanionHttpSecurityException.IdentityRejected())
            }
            if (!usePlatformTrust) {
                if (!setPinnedAnchor(trust)) {
                    return IosTlsValidationResult.Rejected(CompanionHttpSecurityException.TrustRejected())
                }
            }
            if (!SecTrustEvaluateWithError(trust, null)) {
                return IosTlsValidationResult.Rejected(CompanionHttpSecurityException.TrustRejected())
            }
            val certificates = trust.certificateDerChain()
            if (
                certificates.isEmpty() ||
                certificates.none { certificate -> certificate.contentEquals(expectedRootDer) } ||
                certificates.none { certificate -> certificate.sha256Hex() == transportIdentitySha256 }
            ) {
                return IosTlsValidationResult.Rejected(CompanionHttpSecurityException.IdentityRejected())
            }
            return IosTlsValidationResult.Accepted
        }

        private fun setPinnedAnchor(trust: platform.Security.SecTrustRef): Boolean = memScoped {
            val values = allocArray<COpaquePointerVar>(1)
            values[0] = rootCertificate
            val anchors = CFArrayCreate(null, values, 1, null) ?: return@memScoped false
            val configured =
                SecTrustSetAnchorCertificates(trust, anchors) == errSecSuccess &&
                    SecTrustSetAnchorCertificatesOnly(trust, true) == errSecSuccess
            CFRelease(anchors)
            configured
        }

        private fun configureHostnamePolicy(trust: platform.Security.SecTrustRef): Boolean {
            val hostname = CFStringCreateWithCString(
                null,
                CompanionCertificateProtocol.TLS_SERVER_NAME,
                kCFStringEncodingUTF8,
            ) ?: return false
            val policy = SecPolicyCreateSSL(true, hostname)
            val configured = policy != null && SecTrustSetPolicies(trust, policy) == errSecSuccess
            if (policy != null) {
                CFRelease(policy)
            }
            CFRelease(hostname)
            return configured
        }
    }
}

private sealed interface IosTlsValidationResult {
    data object NotApplicable : IosTlsValidationResult
    data object Accepted : IosTlsValidationResult
    class Rejected(val failure: CompanionHttpSecurityException) : IosTlsValidationResult
}

private fun ByteArray.toSecCertificate(): SecCertificateRef? = usePinned { pinned ->
    val data = NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    val retained = CFBridgingRetain(data) ?: return@usePinned null
    try {
        SecCertificateCreateWithData(null, retained.reinterpret())
    } finally {
        CFRelease(retained)
    }
}

private fun platform.Security.SecTrustRef.certificateDerChain(): List<ByteArray> {
    val count = SecTrustGetCertificateCount(this)
    return (0 until count).mapNotNull { index ->
        val certificate = SecTrustGetCertificateAtIndex(this, index) ?: return@mapNotNull null
        val data = CFBridgingRelease(SecCertificateCopyData(certificate)) as NSData
        data.toByteArray()
    }
}

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned -> memcpy(pinned.addressOf(0), this.bytes, length) }
    }
}
