package com.devuloopers.knet.companion.model

/** Shared wire constants for the authenticated companion inspection data plane. */
public object CompanionProxyProtocol {
    /** Authenticated readiness probe handled by the gateway without entering the inspected proxy. */
    public const val READINESS_PATH: String = "/companion/v3/proxy/readiness"
}
