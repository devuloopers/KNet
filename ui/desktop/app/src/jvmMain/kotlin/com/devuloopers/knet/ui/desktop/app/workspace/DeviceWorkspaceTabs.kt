package com.devuloopers.knet.ui.desktop.app.workspace

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.application.contract.device.DevicePresenceStatus
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.ui.core.components.tabs.*

/**
 * Maps eligible identities into the shared title-bar strip without querying storage or touching a socket.
 * [onClose] targets the clicked offline device, independently of selection. [closeInProgress] disables
 * Close during deletion; connected/grace-period devices expose a disabled control with explanatory help.
 */
@Composable
fun DeviceWorkspaceTabs(state: DeviceWorkspaceState, onSelect: (TrafficSource) -> Unit, modifier: Modifier = Modifier,
    onInteractiveBoundsChanged: (List<androidx.compose.ui.geometry.Rect>) -> Unit = {},
    onClose: (com.devuloopers.knet.identity.RegisteredDeviceId) -> Unit = {}, closeInProgress: Boolean = false) {
    val labels = deviceWorkspaceTabLabels(state.devices)
    val tabs = listOf(WorkspaceTabItem("other", "Other traffic")) + state.devices.map { entry ->
        val label = labels.getValue(entry.device.id)
        WorkspaceTabItem(TrafficSource.Device(entry.device.id).preferenceKey, label.name, when (entry.status) {
            DevicePresenceStatus.CONNECTED -> WorkspaceTabStatus.CONNECTED
            DevicePresenceStatus.RECONNECTING -> WorkspaceTabStatus.RECONNECTING
            DevicePresenceStatus.OFFLINE -> WorkspaceTabStatus.OFFLINE
        }, close = WorkspaceTabCloseState(
            enabled = entry.status == DevicePresenceStatus.OFFLINE && !closeInProgress,
            description = when {
                entry.status != DevicePresenceStatus.OFFLINE -> "Connected devices remain visible. Use Clear to remove traffic."
                closeInProgress -> "Traffic is being cleared."
                else -> "Clear traffic and close ${label.fullLabel}"
            },
        ), secondaryLabel = label.identitySuffix)
    }
    KNetWorkspaceTabs(tabs, state.source.preferenceKey, { key -> onSelect(TrafficSource.fromPreference(key)) },
        modifier, onInteractiveBoundsChanged, onClose = { key ->
            (TrafficSource.fromPreference(key) as? TrafficSource.Device)?.let { source -> onClose(source.id) }
        })
}

/**
 * Display-only device name and collision-safe suffix; retained registration data is never rewritten.
 * @property name Model/custom name with a matching generated Companion suffix removed.
 * @property identitySuffix Shortest distinct desktop suffix, retaining the identity's original case.
 */
internal data class DeviceWorkspaceTabLabel(val name: String, val identitySuffix: String) {
    /** Complete accessible/hover/confirmation label with exactly one desktop suffix. */
    val fullLabel: String get() = "$name · $identitySuffix"
}

/** Returns complete display labels for confirmations using the same policy as the tab strip. */
internal fun deviceWorkspaceLabels(entries: List<com.devuloopers.knet.application.contract.device.DeviceWorkspaceEntry>): Map<com.devuloopers.knet.identity.RegisteredDeviceId, String> =
    deviceWorkspaceTabLabels(entries).mapValues { (_, label) -> label.fullLabel }

/** Uses distinct identity suffixes and removes only the legacy Companion suffix proven by that exact identity. */
internal fun deviceWorkspaceTabLabels(entries: List<com.devuloopers.knet.application.contract.device.DeviceWorkspaceEntry>): Map<com.devuloopers.knet.identity.RegisteredDeviceId, DeviceWorkspaceTabLabel> {
    val ids = entries.map { entry -> entry.device.id }
    return entries.associate { entry ->
        val identity = entry.device.id
        var length = minOf(6, identity.value.length)
        while (ids.any { other -> other != identity && other.value.takeLast(length) == identity.value.takeLast(length) } && length < identity.value.length) length++
        // Existing Companion registrations append an uppercase four-character alphanumeric ID. Verify
        // the exact generated ending before removing it, leaving unrelated custom names unchanged.
        val companionSuffix = identity.value.filter(Char::isLetterOrDigit).takeLast(4).uppercase().ifBlank { "KNET" }
        val name = entry.device.displayName.removeSuffix(" · $companionSuffix")
            .takeIf(String::isNotBlank) ?: entry.device.displayName
        identity to DeviceWorkspaceTabLabel(name, identity.value.takeLast(length))
    }
}
