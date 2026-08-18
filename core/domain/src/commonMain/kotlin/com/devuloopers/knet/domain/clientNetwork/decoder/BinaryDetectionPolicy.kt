package com.devuloopers.knet.domain.clientNetwork.decoder

/**
 * Configuration policy for byte-level binary heuristic sampling.
 *
 * @property maxSampleSizeBytes Maximum number of prefix bytes to inspect (default: 4096 bytes).
 * @property maxControlCharRatio Maximum allowable ratio of non-printable control characters before classifying as binary (default: 0.08 / 8%).
 * @property allowNullBytes If false, presence of null bytes (`0x00`) immediately classifies payload as binary.
 */
data class BinaryDetectionPolicy(
    val maxSampleSizeBytes: Int = 4096,
    val maxControlCharRatio: Double = 0.08,
    val allowNullBytes: Boolean = false
) {
    companion object {
        /**
         * Default strict binary inspection policy.
         */
        val DEFAULT: BinaryDetectionPolicy = BinaryDetectionPolicy()
    }
}
