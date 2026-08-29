package com.devuloopers.knet.companion.data.crypto

import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionSha256Test {
    @Test
    fun matchesPublishedEmptyAndAbcVectors() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            CompanionSha256.digest(byteArrayOf()).hex(),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            CompanionSha256.digest("abc".encodeToByteArray()).hex(),
        )
    }
}

private fun ByteArray.hex(): String = joinToString("") { byte ->
    byte.toUByte().toString(16).padStart(2, '0')
}
