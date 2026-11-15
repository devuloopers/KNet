package com.devuloopers.knet.ui.desktop.traffic.table

import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset
import com.devuloopers.knet.application.contract.traffic.TrafficCopyPreset.*
import com.devuloopers.knet.application.contract.traffic.TrafficRecordId
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.core.components.menu.*
import com.devuloopers.knet.ui.desktop.traffic.model.recordId
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficRowKind
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficRowUiState

import com.devuloopers.knet.ui.desktop.httppanel.copy.*

internal typealias TrafficCopyProtocol = CaptureCopyProtocol
internal typealias TrafficCopyMenuCapabilities = CaptureCopyMenuCapabilities

internal fun CaptureCopyMenuCapabilities.Companion.from(records: Collection<TrafficRecordId>, rows: List<TrafficRowUiState>): CaptureCopyMenuCapabilities {
            val selected = if (records is Set) records else records.toSet()
            val known = rows.filter { it.recordId in selected }
            val knownIds = known.mapTo(hashSetOf()) { it.recordId }
            val unknownHttp = records.any { it is TrafficRecordId.Exchange && it !in knownIds }
            val http = known.filter { it.rowKind == TrafficRowKind.HTTP_EXCHANGE }
            return TrafficCopyMenuCapabilities(records.any { it is TrafficRecordId.Exchange }, unknownHttp || http.any { it.status > 0 },
                unknownHttp || http.any { it.requestKind !in setOf(RequestKindId.GRPC, RequestKindId.WEBSOCKET, RequestKindId.GRAPHQL_WEBSOCKET) },
                buildSet {
                    if (records.any { it is TrafficRecordId.Opaque }) add(TrafficCopyProtocol.OPAQUE)
                    for (row in http) {
                        when (row.requestKind) {
                            RequestKindId.GRAPHQL -> add(TrafficCopyProtocol.GRAPHQL)
                            RequestKindId.WEBSOCKET -> add(TrafficCopyProtocol.WEBSOCKET)
                            RequestKindId.GRAPHQL_WEBSOCKET -> add(TrafficCopyProtocol.GRAPHQL_WEBSOCKET)
                            RequestKindId.GRPC -> add(TrafficCopyProtocol.GRPC)
                        }
                        if (row.contentType?.substringBefore(';')?.trim().equals("text/event-stream", true)) add(TrafficCopyProtocol.SSE)
                    }
                })
        }

/** Freezes exact typed selection once for every shared preset leaf. */
internal fun trafficCopyMenu(records: List<TrafficRecordId>, capabilities: TrafficCopyMenuCapabilities,
    copy: (List<TrafficRecordId>, TrafficCopyPreset) -> Unit): ContextMenuSubmenu {
    val frozen = records.toList()
    return ContextMenuSubmenu(if (frozen.size == 1) "Copy" else "Copy (${frozen.size})",
        captureCopyMenuItems(capabilities) { preset -> copy(frozen, preset) })
}
