package com.devuloopers.knet.domain.workspace.model

/**
 * Strongly-typed enumeration representing configurable timeout duration units.
 *
 * @property label Display label (e.g. "sec", "min") used in segmented pill UI toggles.
 * @property multiplier Seconds multiplier for duration conversion.
 */
enum class TimeoutUnit(val label: String, val multiplier: Int) {
    /**
     * Seconds duration unit.
     */
    SECONDS("sec", 1),

    /**
     * Minutes duration unit (60 seconds).
     */
    MINUTES("min", 60);

    /**
     * Converts a given input [value] in this unit into total seconds.
     *
     * @param value Numeric magnitude in this unit.
     * @return Total duration in seconds.
     */
    fun toSeconds(value: Int): Int = value * multiplier

    companion object {
        /**
         * Normalizes [totalSeconds] into a cohesive pair of (value, unit).
         * Prefers [MINUTES] if the total duration is an exact multiple of 60 seconds (and >= 60).
         *
         * @param totalSeconds Total duration in seconds.
         * @return Pair containing the integer value and chosen [TimeoutUnit].
         */
        fun fromSeconds(totalSeconds: Int): Pair<Int, TimeoutUnit> {
            return if (totalSeconds >= 60 && totalSeconds % 60 == 0) {
                (totalSeconds / 60) to MINUTES
            } else {
                totalSeconds to SECONDS
            }
        }
    }
}
