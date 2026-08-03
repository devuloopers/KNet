package com.devuloopers.knet.domain.network.decoder

/**
 * Stage 3 binary detection engine combining [MediaTypeInspector] and byte heuristic sampling via [BinaryDetectionPolicy].
 */
public object BinaryDetector {

    /**
     * Determines whether the given byte payload is binary content.
     *
     * @param bytes Input payload byte array.
     * @param contentType Optional Content-Type header value.
     * @param policy Configurable [BinaryDetectionPolicy] settings.
     * @return [BinaryCategory] if payload is binary, or null if text/printable payload.
     */
    public fun detectBinaryCategory(
        bytes: ByteArray,
        contentType: String?,
        policy: BinaryDetectionPolicy = BinaryDetectionPolicy.DEFAULT
    ): BinaryCategory? {
        if (bytes.isEmpty()) return null

        // 1. Media Type Inspection Stage
        val mediaCategory = MediaTypeInspector.inspectCategory(contentType)
        if (mediaCategory != null) {
            return mediaCategory
        }

        // 2. Configurable Byte Heuristic Sampling Stage
        if (isBinaryHeuristic(bytes, policy)) {
            return BinaryCategory.GENERIC
        }

        return null
    }

    private fun isBinaryHeuristic(bytes: ByteArray, policy: BinaryDetectionPolicy): Boolean {
        val sampleSize = bytes.size.coerceAtMost(policy.maxSampleSizeBytes)
        if (sampleSize == 0) return false

        var controlCharCount = 0

        for (i in 0 until sampleSize) {
            val byteVal = bytes[i].toInt() and 0xFF

            if (!policy.allowNullBytes && byteVal == 0x00) {
                return true
            }

            // Check non-printable ASCII control characters (outside \t 0x09, \n 0x0A, \r 0x0D)
            if (byteVal < 0x20 && byteVal != 0x09 && byteVal != 0x0A && byteVal != 0x0D) {
                controlCharCount++
            } else if (byteVal == 0x7F) {
                controlCharCount++
            }
        }

        val ratio = controlCharCount.toDouble() / sampleSize.toDouble()
        return ratio > policy.maxControlCharRatio
    }
}
