package com.devuloopers.knet.domain.clientNetwork.decoder

/**
 * Known HTTP Content-Encoding tokens (RFC 7231 / IANA HTTP Content Coding Registry).
 *
 * @property headerValue Standard lowercase HTTP header representation (e.g. "gzip", "deflate").
 */
public enum class ContentEncoding(public val headerValue: String) {
    /**
     * GNU zip compression format (RFC 1952).
     */
    GZIP("gzip"),

    /**
     * zlib format with deflate compression (RFC 1950).
     */
    DEFLATE("deflate"),

    /**
     * Brotli compression format (RFC 7932).
     */
    BROTLI("br"),

    /**
     * Zstandard compression format (RFC 8878).
     */
    ZSTD("zstd"),

    /**
     * Identity encoding / uncompressed raw payload.
     */
    IDENTITY("identity");

    public companion object {
        /**
         * Resolves a [ContentEncoding] enum constant from a raw header token string (case-insensitive).
         *
         * @param raw Raw HTTP Content-Encoding string header value.
         * @return Matching [ContentEncoding] enum, or null if unsupported/custom.
         */
        public fun fromHeaderValue(raw: String): ContentEncoding? {
            val normalized = raw.trim().lowercase()
            return entries.find { it.headerValue == normalized }
        }
    }
}
