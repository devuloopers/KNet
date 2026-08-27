package com.devuloopers.knet.companion.sharedui.navigation

import androidx.navigation3.runtime.NavKey
import com.devuloopers.knet.companion.presentation.flow.CompanionFlowStage

/** Maps presentation gates to Navigation 3 keys and safely reconciles restored stacks. */
internal object CompanionNavigator {
    /** Resolves the only route allowed by [stage]. */
    fun routeFor(stage: CompanionFlowStage): CompanionRoute = when (stage) {
        CompanionFlowStage.CONNECT_DESKTOP -> CompanionRoute.ConnectDesktop
        CompanionFlowStage.CERTIFICATE_SETUP -> CompanionRoute.CertificateSetup
        CompanionFlowStage.INSPECTION_HOME -> CompanionRoute.InspectionHome
    }

    /** Replaces stale restored history so Back cannot expose a screen behind an unmet setup gate. */
    fun reconcile(backStack: MutableList<NavKey>, requiredRoute: CompanionRoute) {
        if (backStack.size == 1 && backStack.lastOrNull() == requiredRoute) return
        backStack.clear()
        backStack.add(requiredRoute)
    }
}
