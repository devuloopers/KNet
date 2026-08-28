package com.devuloopers.knet.companion.model

import kotlin.jvm.JvmInline

/** Human-readable desktop label shown during pairing, never used as an identity or authenticator. */
@JvmInline
public value class CompanionDesktopDisplayName(public val value: String) {
    init {
        require(value.length in 1..MAXIMUM_LENGTH && value == value.trim()) {
            "Companion desktop display name must contain 1 to $MAXIMUM_LENGTH trimmed characters."
        }
        require(value.none(Char::isUnsafeDesktopDisplayNameCharacter)) {
            "Companion desktop display name must not contain control characters."
        }
    }

    public companion object {
        public const val MAXIMUM_LENGTH: Int = 128
    }
}

private fun Char.isUnsafeDesktopDisplayNameCharacter(): Boolean = code in 0..31 || code == 127
