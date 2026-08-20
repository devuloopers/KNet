package com.devuloopers.knet.ui.desktop.settings.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Presentation unit used to edit application timeout durations. */
enum class TimeoutUnit(val label: String) {
    /** Duration expressed in seconds. */
    SECONDS("sec"),

    /** Duration expressed in minutes. */
    MINUTES("min");

    /** Converts [value] into a Kotlin [Duration]. */
    fun toDuration(value: Int): Duration = when (this) {
        SECONDS -> value.seconds
        MINUTES -> value.minutes
    }

    /** Normalizes persisted durations for concise settings presentation. */
    companion object {
        /**
         * Selects minutes for exact minute values and seconds otherwise.
         *
         * @param duration Persisted timeout duration.
         * @return Numeric value paired with its presentation unit.
         */
        fun fromDuration(duration: Duration): Pair<Int, TimeoutUnit> {
            val seconds = duration.inWholeSeconds.toInt()
            return if (seconds >= 60 && seconds % 60 == 0) {
                (seconds / 60) to MINUTES
            } else {
                seconds to SECONDS
            }
        }
    }
}
