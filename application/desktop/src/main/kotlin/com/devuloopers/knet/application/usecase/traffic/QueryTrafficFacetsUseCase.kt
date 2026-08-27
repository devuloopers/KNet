package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.traffic.TrafficFacetCounts
import com.devuloopers.knet.application.contract.traffic.TrafficFacetQuery
import com.devuloopers.knet.application.contract.traffic.TrafficFacetReader

/** Loads exact Traffic filter facets without materializing a page of exchanges. */
public class QueryTrafficFacetsUseCase(
    private val reader: TrafficFacetReader,
) {
    public suspend fun execute(query: TrafficFacetQuery): TrafficFacetCounts = reader.queryFacets(query)
}
