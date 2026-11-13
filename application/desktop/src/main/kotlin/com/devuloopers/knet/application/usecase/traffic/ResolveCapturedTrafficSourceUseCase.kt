package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.application.contract.traffic.TrafficSource
import com.devuloopers.knet.traffic.id.ExchangeId

/** Resolves a pending exchange's source from canonical admitted ingress, never from request headers or host. */
public class ResolveCapturedTrafficSourceUseCase(private val query: TrafficQuery) {
    /** Returns null until capture admission is durable; callers must not assign unknown pending rows to a device. */
    public suspend fun execute(exchangeId: ExchangeId): TrafficSource? {
        val exchange = query.getExchange(exchangeId) ?: return null
        val connectionId = exchange.connectionId ?: return TrafficSource.Other
        val ingress = query.getConnectionIngress(connectionId)?.ingress ?: return null
        return TrafficSource.fromIngress(ingress)
    }
}
