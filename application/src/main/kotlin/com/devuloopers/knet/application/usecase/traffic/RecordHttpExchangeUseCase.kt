package com.devuloopers.knet.application.usecase.traffic

import com.devuloopers.knet.application.port.traffic.RecordHttpExchangeCommand
import com.devuloopers.knet.application.port.traffic.TrafficRecordPort
import com.devuloopers.knet.application.port.traffic.TrafficRecordReceipt

/**
 * Records a complete application-authored HTTP exchange through the canonical traffic boundary.
 *
 * @property trafficRecordPort Platform adapter owning canonical session and persistence details.
 */
public class RecordHttpExchangeUseCase(
    private val trafficRecordPort: TrafficRecordPort,
) {
    /**
     * Records one canonical request/response exchange.
     *
     * @param command Shared HTTP metadata plus explicitly owned body content.
     * @return Durable canonical session/exchange identity.
     */
    public suspend fun execute(command: RecordHttpExchangeCommand): TrafficRecordReceipt =
        trafficRecordPort.record(command)
}
