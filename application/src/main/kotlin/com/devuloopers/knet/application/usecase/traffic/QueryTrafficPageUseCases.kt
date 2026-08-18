package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.port.traffic.TrafficGeneration
import com.devuloopers.knet.application.port.traffic.TrafficPage
import com.devuloopers.knet.application.port.traffic.TrafficPageQuery
import com.devuloopers.knet.application.port.traffic.TrafficQueryPort
import com.devuloopers.knet.application.port.traffic.TrafficSessionCatalogPort
import com.devuloopers.knet.traffic.id.CaptureSessionId
import kotlinx.coroutines.flow.Flow

/** Observes the latest session selected by the storage adapter for bounded presentation queries. */
public class ObserveLatestTrafficSessionUseCase(
    private val catalog: TrafficSessionCatalogPort,
) {
    /** Returns the compact latest-session stream. */
    public fun execute(): Flow<CaptureSessionId?> = catalog.latestSessionId
}

/** Executes one bounded indexed traffic page query. */
public class QueryTrafficPageUseCase(
    private val trafficQuery: TrafficQueryPort,
) {
    /** Returns one page and its opaque continuation cursor. */
    public suspend fun execute(query: TrafficPageQuery): TrafficPage = trafficQuery.query(query)
}

/** Observes compact store-change generations used to invalidate live pages. */
public class ObserveTrafficGenerationsUseCase(
    private val trafficQuery: TrafficQueryPort,
) {
    /** Returns generation signals without materializing exchange rows. */
    public fun execute(): Flow<TrafficGeneration> = trafficQuery.generations
}
