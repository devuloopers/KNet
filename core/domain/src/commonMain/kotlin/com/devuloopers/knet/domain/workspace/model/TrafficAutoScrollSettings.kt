package com.devuloopers.knet.domain.workspace.model

/** User-selected Traffic scrolling strategy, persisted independently of any device viewport. */
enum class TrafficAutoScrollMode {
    /** Follow only while the newest row is completely visible at the top. */
    SMART,
    /** Follow according to the explicit user-controlled switch. */
    MANUAL,
}

/**
 * Saved Traffic scrolling controls; transient history positions and unread counts are never persisted.
 * @property mode Smart by default, including installations without these preference keys.
 * @property manualEnabled Last explicit Manual switch value, retained when switching modes.
 */
data class TrafficAutoScrollSettings(
    val mode: TrafficAutoScrollMode = TrafficAutoScrollMode.SMART,
    val manualEnabled: Boolean = true,
)
