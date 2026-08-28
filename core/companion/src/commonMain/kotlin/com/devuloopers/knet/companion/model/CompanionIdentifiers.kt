package com.devuloopers.knet.companion.model

import kotlin.jvm.JvmInline

/** Stable desktop installation identity independent from its current address. */
@JvmInline
public value class CompanionDesktopId(public val value: String) {
    init {
        require(value.isPortableIdentifier(maximumLength = 128)) {
            "CompanionDesktopId must be a safe 1 to 128 character value."
        }
    }
}

/** Opaque handle used to locate a credential in platform-protected storage. */
@JvmInline
public value class CompanionCredentialReference(public val value: String) {
    init {
        require(value.isPortableIdentifier(maximumLength = 512)) {
            "CompanionCredentialReference must be a safe 1 to 512 character value."
        }
    }
}

/** Lowercase SHA-256 fingerprint used for explicit trust checks. */
@JvmInline
public value class Sha256Fingerprint(public val value: String) {
    init {
        require(SHA_256.matches(value)) { "Fingerprint must be 64 lowercase hexadecimal characters." }
    }

    private companion object {
        val SHA_256: Regex = Regex("[0-9a-f]{64}")
    }
}

/** Opaque identifier for one short-lived companion invitation retrieval record. */
@JvmInline
public value class CompanionBootstrapId(public val value: String) {
    init {
        require(value.isPortableIdentifier(maximumLength = 128)) {
            "CompanionBootstrapId must be a safe 1 to 128 character value."
        }
    }
}

/** One-time secret used only to retrieve the complete pairing invitation. */
@JvmInline
public value class CompanionBootstrapSecret(public val value: String) {
    init {
        require(value.length in 16..512 && value.isPortableIdentifier(maximumLength = 512)) {
            "CompanionBootstrapSecret must be a safe 16 to 512 character value."
        }
    }
}

/** Random, single-use challenge value echoed only by an authenticated KNet desktop. */
@JvmInline
public value class CompanionCertificateChallengeNonce(public val value: String) {
    init {
        require(value.length in 32..128 && value.all(Char::isChallengeCharacter)) {
            "Certificate challenge nonce must be a 32 to 128 character Base64URL value."
        }
    }
}

private fun String.isPortableIdentifier(maximumLength: Int): Boolean =
    length in 1..maximumLength && isNotBlank() && this == trim() && none(Char::isControlCharacter)

private fun Char.isControlCharacter(): Boolean = code in 0..31 || code == 127

private fun Char.isChallengeCharacter(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this == '-' || this == '_'
