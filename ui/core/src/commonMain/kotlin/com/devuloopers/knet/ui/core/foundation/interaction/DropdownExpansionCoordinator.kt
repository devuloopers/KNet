package com.devuloopers.knet.ui.core.foundation.interaction

import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Coordinates expansion ownership across every KNet dropdown in one themed composition.
 *
 * The coordinator deliberately stores no feature state. It retains only the active owner's identity and a
 * close callback, allowing a new anchor to close the previous owner and acquire expansion in one pointer event.
 */
internal class DropdownExpansionCoordinator(
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val clickThroughGuardDuration: Duration = 500.milliseconds,
) {
    private data class ActiveDropdown(
        val owner: Any,
        val close: () -> Unit,
    )

    private var activeDropdown: ActiveDropdown? = null
    private var recentlyDismissedDropdown: DismissedDropdown? = null

    private data class DismissedDropdown(
        val owner: Any,
        val dismissedAt: TimeMark,
    )

    /** Makes [owner] active, closing the previous owner before returning. */
    fun open(owner: Any, close: () -> Unit) {
        recentlyDismissedDropdown = null
        val previous = activeDropdown
        activeDropdown = ActiveDropdown(owner = owner, close = close)
        if (previous?.owner !== owner) previous?.close?.invoke()
    }

    /** Toggles [owner] and returns whether it owns expansion after the operation. */
    fun toggle(owner: Any, close: () -> Unit): Boolean {
        if (consumeDismissClickThrough(owner)) {
            close()
            return false
        }

        val current = activeDropdown
        if (current?.owner === owner) {
            activeDropdown = null
            current.close()
            return false
        }

        activeDropdown = ActiveDropdown(owner = owner, close = close)
        current?.close?.invoke()
        return true
    }

    /** Releases [owner] without affecting a newer dropdown owner. */
    fun release(owner: Any) {
        if (activeDropdown?.owner === owner) activeDropdown = null
    }

    /**
     * Records a popup dismissal without affecting a newer expansion owner.
     *
     * Desktop popup dismissal is delivered on pointer press, before the same click reaches the underlying anchor
     * on release. The short-lived owner marker lets that anchor consume the release as a close instead of reopening.
     */
    fun dismissFromPopup(owner: Any) {
        if (activeDropdown?.owner !== owner) return
        activeDropdown = null
        recentlyDismissedDropdown = DismissedDropdown(
            owner = owner,
            dismissedAt = timeSource.markNow(),
        )
    }

    internal fun ownsExpansion(owner: Any): Boolean = activeDropdown?.owner === owner

    private fun consumeDismissClickThrough(owner: Any): Boolean {
        val dismissed = recentlyDismissedDropdown ?: return false
        recentlyDismissedDropdown = null
        return dismissed.owner === owner && dismissed.dismissedAt.elapsedNow() <= clickThroughGuardDuration
    }
}

internal val LocalDropdownExpansionCoordinator = staticCompositionLocalOf<DropdownExpansionCoordinator> {
    error("KNet dropdowns must be hosted inside KNetTheme.")
}
