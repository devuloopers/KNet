package com.devuloopers.knet.domain.clientNetwork.decoder

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Internal signal used to distinguish a bounded decoding rejection from malformed content. */
internal class DecodedOutputLimitException : Exception()

/** Reads this decoded stream without retaining more than [maximumOutputBytes]. */
internal fun InputStream.readBoundedBytes(maximumOutputBytes: Int): ByteArray {
    require(maximumOutputBytes > 0) { "Decoded output limit must be positive." }
    val output = ByteArrayOutputStream(minOf(maximumOutputBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var retainedBytes = 0
    while (true) {
        val readBytes = read(buffer)
        if (readBytes < 0) break
        if (readBytes == 0) continue
        if (retainedBytes > maximumOutputBytes - readBytes) {
            throw DecodedOutputLimitException()
        }
        output.write(buffer, 0, readBytes)
        retainedBytes += readBytes
    }
    return output.toByteArray()
}
