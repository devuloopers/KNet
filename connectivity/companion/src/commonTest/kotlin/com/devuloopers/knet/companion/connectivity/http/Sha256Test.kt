package com.devuloopers.knet.companion.connectivity.http

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {
    @Test
    fun emptyInputMatchesPublishedSha256Vector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ByteArray(0).sha256Hex(),
        )
    }

    @Test
    fun asciiInputMatchesPublishedSha256Vector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            "abc".encodeToByteArray().sha256Hex(),
        )
    }
}
