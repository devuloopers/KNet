package com.devuloopers.knet.domain.clientNetwork.decoder

import com.devuloopers.knet.traffic.model.body.ContentEncoding

/**
 * Sealed interface representing the outcome of an HTTP transport content-encoding decoding operation.
 */
sealed interface DecodedBodyResult {
    /**
     * Successfully decompressed payload byte array.
     *
     * @property bytes The uncompressed payload bytes.
     * @property encoding The [ContentEncoding] enum method used.
     */
    data class Success(val bytes: ByteArray, val encoding: ContentEncoding) : DecodedBodyResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return bytes.contentEquals(other.bytes) && encoding == other.encoding
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + encoding.hashCode()
            return result
        }
    }

    /**
     * Uncompressed payload byte array (no Content-Encoding or "identity").
     *
     * @property bytes The raw uncompressed bytes.
     */
    data class Identity(val bytes: ByteArray) : DecodedBodyResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Identity) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /**
     * Payload with an unsupported Content-Encoding (e.g. "br", "zstd").
     *
     * @property encoding The unsupported encoding header value.
     * @property rawBytes The original raw payload bytes.
     */
    data class UnsupportedEncoding(val encoding: String, val rawBytes: ByteArray) : DecodedBodyResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is UnsupportedEncoding) return false
            return encoding == other.encoding && rawBytes.contentEquals(other.rawBytes)
        }

        override fun hashCode(): Int {
            var result = encoding.hashCode()
            result = 31 * result + rawBytes.contentHashCode()
            return result
        }
    }

    /**
     * Payload decompression failure due to stream corruption or truncation.
     *
     * @property encoding The Content-Encoding attempted.
     * @property errorMessage Descriptive exception message.
     * @property rawBytes The original raw payload bytes.
     */
    data class CorruptedEncoding(val encoding: String, val errorMessage: String, val rawBytes: ByteArray) : DecodedBodyResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is CorruptedEncoding) return false
            return encoding == other.encoding && errorMessage == other.errorMessage && rawBytes.contentEquals(other.rawBytes)
        }

        override fun hashCode(): Int {
            var result = encoding.hashCode()
            result = 31 * result + errorMessage.hashCode()
            result = 31 * result + rawBytes.contentHashCode()
            return result
        }
    }
}
