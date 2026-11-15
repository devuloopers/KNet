package com.devuloopers.knet.ui.desktop.traffic.table

import com.devuloopers.knet.application.contract.traffic.TrafficRecordId
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.ui.core.components.menu.*
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficRowSelection

/** Item-specific commands are frozen when the shared menu opens; selection export stays explicit. */
internal data class TrafficContextMenuActions(
    val exportItem: (TrafficRecordId) -> Unit,
    val exportSelection: (() -> Unit)?,
    val sendToStudio: (String) -> Unit,
    val addNetworkCondition: (String) -> Unit,
    val addBreakpoint: (String) -> Unit,
    val protect: (TrafficRecordId, ProtectedTrafficAction) -> Unit,
    val copyDetails: (List<TrafficRecordId>, TrafficCopyPreset) -> Unit = { _, _ -> },
)

/** Export/copy precede icon-labelled sidebar features; both row kinds share future-destination choices. */
internal fun trafficRowContextMenu(id: TrafficRecordId, selection: TrafficRowSelection,
    actions: TrafficContextMenuActions, capabilities: TrafficCopyMenuCapabilities): List<ContextMenuEntry> = buildList {
    val value = when (id) { is TrafficRecordId.Exchange -> id.id.value; is TrafficRecordId.Opaque -> id.id.value }
    val multipleSelected = id in selection.records && selection.records.size > 1
    if (multipleSelected && actions.exportSelection != null)
        add(ContextMenuItem("Export ${selection.records.size} selected items") { actions.exportSelection.invoke() })
    else add(ContextMenuItem("Export this item") { actions.exportItem(id) })
    val copiedRecords = if (multipleSelected) selection.records.toList() else listOf(id)
    add(trafficCopyMenu(copiedRecords, capabilities, actions.copyDetails))
    add(ContextMenuSeparator)
    if (id is TrafficRecordId.Exchange) add(ContextMenuItem("Send to API Studio", KNetIcons.Send) { actions.sendToStudio(value) })
    if (id is TrafficRecordId.Exchange) add(ContextMenuItem("Add Breakpoint Rule", KNetIcons.Pause) { actions.addBreakpoint(value) })
    add(ContextMenuItem("Add to Network Conditions...", KNetIcons.Speed) { actions.addNetworkCondition(value) })
    add(ContextMenuSubmenu("Protected Traffic", listOf(
        ContextMenuItem("Always tunnel this destination") { actions.protect(id, ProtectedTrafficAction.TUNNEL) },
        ContextMenuItem("Attempt inspection next time") { actions.protect(id, ProtectedTrafficAction.INSPECT) },
        ContextMenuItem("Block this destination") { actions.protect(id, ProtectedTrafficAction.BLOCK) },
    ), icon = KNetIcons.Shield))
}
