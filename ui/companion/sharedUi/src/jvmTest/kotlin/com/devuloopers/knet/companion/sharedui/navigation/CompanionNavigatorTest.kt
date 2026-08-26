package com.devuloopers.knet.companion.sharedui.navigation

import androidx.navigation3.runtime.NavKey
import com.devuloopers.knet.companion.presentation.flow.CompanionFlowStage
import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionNavigatorTest {
    @Test
    fun `every presentation stage maps to one stable route`() {
        assertEquals(CompanionRoute.ConnectDesktop, CompanionNavigator.routeFor(CompanionFlowStage.CONNECT_DESKTOP))
        assertEquals(
            CompanionRoute.ScanInvitation,
            CompanionNavigator.routeFor(CompanionFlowStage.SCAN_INVITATION),
        )
        assertEquals(CompanionRoute.ConfirmDesktop, CompanionNavigator.routeFor(CompanionFlowStage.CONFIRM_DESKTOP))
        assertEquals(CompanionRoute.CertificateSetup, CompanionNavigator.routeFor(CompanionFlowStage.CERTIFICATE_SETUP))
        assertEquals(
            CompanionRoute.InspectionPermission,
            CompanionNavigator.routeFor(CompanionFlowStage.INSPECTION_PERMISSION),
        )
        assertEquals(CompanionRoute.Home, CompanionNavigator.routeFor(CompanionFlowStage.HOME))
    }

    @Test
    fun `reconcile removes stale restored routes behind current setup gate`() {
        val backStack = mutableListOf<NavKey>(CompanionRoute.ConnectDesktop, CompanionRoute.Home)

        CompanionNavigator.reconcile(backStack, CompanionRoute.CertificateSetup)

        assertEquals(listOf<NavKey>(CompanionRoute.CertificateSetup), backStack)
    }

    @Test
    fun `reconcile keeps an already valid root stable`() {
        val backStack = mutableListOf<NavKey>(CompanionRoute.Home)

        CompanionNavigator.reconcile(backStack, CompanionRoute.Home)

        assertEquals(listOf<NavKey>(CompanionRoute.Home), backStack)
    }
}
