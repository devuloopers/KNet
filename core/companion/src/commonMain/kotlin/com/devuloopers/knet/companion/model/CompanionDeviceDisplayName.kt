package com.devuloopers.knet.companion.model

import kotlin.jvm.JvmInline

/** Human-readable paired-device label retained for desktop management, never used as an authenticator. */
@JvmInline
public value class CompanionDeviceDisplayName(public val value: String) {
    init {
        require(value.length in 1..MAXIMUM_LENGTH && value == value.trim()) {
            "Companion device display name must contain 1 to $MAXIMUM_LENGTH trimmed characters."
        }
        require(value.none(Char::isUnsafeDisplayNameCharacter)) {
            "Companion device display name must not contain control characters."
        }
    }

    public companion object {
        public const val MAXIMUM_LENGTH: Int = 128
    }
}

private fun Char.isUnsafeDisplayNameCharacter(): Boolean = code in 0..31 || code == 127
