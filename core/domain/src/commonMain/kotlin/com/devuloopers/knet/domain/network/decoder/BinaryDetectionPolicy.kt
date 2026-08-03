package com.devuloopers.knet.domain.network.decoder

/**
 * Configuration policy for byte-level binary heuristic sampling.
 *
 * @property maxSampleSizeBytes Maximum number of prefix bytes to inspect (default: 4096 bytes).
 * @property maxControlCharRatio Maximum allowable ratio of non-printable control characters before classifying as binary (default: 0.08 / 8%).
 * @property allowNullBytes If false, presence of null bytes (`0x00`) immediately classifies payload as binary.
 */
public data class BinaryDetectionPolicy(
    val maxSampleSizeBytes: Int = 4096,
    val maxControlCharRatio: Double = 0.08,
    val allowNullBytes: Boolean = false
) {
    public companion object {
        /**
         * Default strict binary inspection policy.
         */
        public val DEFAULT: BinaryDetectionPolicy = BinaryDetectionPolicy()
    }
}
