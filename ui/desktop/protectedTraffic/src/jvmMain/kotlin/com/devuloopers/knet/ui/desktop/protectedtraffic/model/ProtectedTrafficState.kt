package com.devuloopers.knet.ui.desktop.protectedtraffic.model

import com.devuloopers.knet.domain.protectedtraffic.ProtectedIpFamily
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficAction
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTrafficConfiguration
import com.devuloopers.knet.domain.protectedtraffic.ProtectedTransportProtocol

/** Immutable presentation state for protected-traffic policy management. */
data class ProtectedTrafficState(
    val configuration: ProtectedTrafficConfiguration = ProtectedTrafficConfiguration(),
    val ruleDraft: ProtectedTrafficRuleDraft? = null,
    val errorMessage: String? = null,
)

/** User-editable values used to construct one normalized protected-traffic rule. */
data class ProtectedTrafficRuleDraft(
    val sourceApplication: String = "",
    val destination: String = "",
    val port: String = "443",
    val transport: ProtectedTransportProtocol = ProtectedTransportProtocol.TCP,
    val ipFamily: ProtectedIpFamily? = null,
    val action: ProtectedTrafficAction = ProtectedTrafficAction.TUNNEL,
)
