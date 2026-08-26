package com.devuloopers.knet.data.desktop.inspection

import com.devuloopers.knet.application.coordinator.inspection.SemanticInspectionScheduler
import com.devuloopers.knet.application.contract.traffic.TrafficPageQuery
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.application.contract.traffic.TrafficSessionCatalog
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.model.ExchangeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Desktop lifecycle owner that reacts to compact capture generations and schedules semantic work
 * after capture. It never runs on a Netty event loop and closes its scope deterministically.
 */
class DesktopSemanticInspectionRuntime(
    sessionCatalog: TrafficSessionCatalog,
    private val trafficQuery: TrafficQuery,
    private val scheduler: SemanticInspectionScheduler,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inspectedStates = object : LinkedHashMap<String, ExchangeState>(MAX_TRACKED_EXCHANGES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ExchangeState>?): Boolean =
            size > MAX_TRACKED_EXCHANGES
    }

    init {
        scope.launch {
            sessionCatalog.latestSessionId
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest { sessionId ->
                    inspectRecent(sessionId)
                    trafficQuery.generations
                        .filter { it.sessionId == sessionId }
                        .collect { inspectRecent(sessionId) }
                }
        }
    }

    private suspend fun inspectRecent(sessionId: CaptureSessionId) {
        try {
            val candidates = buildList {
                var cursor: com.devuloopers.knet.application.contract.traffic.TrafficPageCursor? = null
                for (pageIndex in 0 until MAX_PAGES_PER_GENERATION) {
                    val page = trafficQuery.query(
                        TrafficPageQuery(sessionId = sessionId, cursor = cursor, limit = PAGE_SIZE),
                    )
                    addAll(page.items.map { item -> item.exchange })
                    cursor = page.nextCursor
                    if (cursor == null) break
                }
            }.filter { exchange ->
                synchronized(inspectedStates) {
                    inspectedStates[exchange.id.value] != exchange.state
                }
            }
            coroutineScope {
                candidates.map { exchange ->
                    async {
                        scheduler.inspect(sessionId, exchange.id, nowMillis())
                        synchronized(inspectedStates) { inspectedStates[exchange.id.value] = exchange.state }
                    }
                }.awaitAll()
            }
        } catch (failure: Exception) {
            KNetLogger.warn("SemanticInspectionRuntime") {
                "Semantic inspection cycle failed safely: ${failure::class.simpleName}"
            }
        }
    }

    override fun close() {
        scope.cancel()
        synchronized(inspectedStates) { inspectedStates.clear() }
    }

    private companion object {
        private const val PAGE_SIZE = 200
        private const val MAX_PAGES_PER_GENERATION = 5
        private const val MAX_TRACKED_EXCHANGES = 10_000
    }
}
