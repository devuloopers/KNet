package com.devuloopers.knet.ui.core.components.dropdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.devuloopers.knet.ui.core.foundation.interaction.DropdownExpansionCoordinator
import com.devuloopers.knet.ui.core.foundation.interaction.LocalDropdownExpansionCoordinator

/** Composition-scoped expansion state backed by the shared dropdown ownership coordinator. */
@Stable
internal class DropdownExpansionState(
    private val owner: Any,
    private val coordinator: DropdownExpansionCoordinator,
    private val onClosed: () -> Unit,
) {
    var expanded: Boolean by mutableStateOf(false)
        private set

    /** Opens this dropdown and atomically closes the previous owner. */
    fun open() {
        coordinator.open(owner = owner, close = ::closeFromCoordinator)
        expanded = true
    }

    /** Toggles this dropdown using coordinator ownership rather than potentially stale UI state. */
    fun toggle(): Boolean {
        expanded = coordinator.toggle(owner = owner, close = ::closeFromCoordinator)
        return expanded
    }

    /** Closes this dropdown immediately and releases its ownership. */
    fun close() {
        coordinator.release(owner)
        closeFromCoordinator()
    }

    /**
     * Handles popup-level outside dismissal.
     *
     * The coordinator retains a short-lived identity marker so the pointer release can be handed to this or another
     * KNet anchor without reopening this dropdown or affecting a newer owner.
     */
    fun dismissFromPopup() {
        coordinator.dismissFromPopup(owner)
        closeFromCoordinator()
    }

    fun dispose() {
        coordinator.release(owner)
    }

    private fun closeFromCoordinator() {
        if (!expanded) return
        expanded = false
        onClosed()
    }
}

/** Remembers one dropdown owner and releases it when its composable leaves the composition. */
@Composable
internal fun rememberDropdownExpansionState(onClosed: () -> Unit = {}): DropdownExpansionState {
    val coordinator = LocalDropdownExpansionCoordinator.current
    val currentOnClosed by rememberUpdatedState(onClosed)
    val state = remember(coordinator) {
        DropdownExpansionState(
            owner = Any(),
            coordinator = coordinator,
            onClosed = { currentOnClosed() },
        )
    }
    DisposableEffect(state) {
        onDispose(state::dispose)
    }
    return state
}
