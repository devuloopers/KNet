package com.devuloopers.knet.companion.sharedui.navigation

import androidx.navigation3.runtime.NavKey
import com.devuloopers.knet.companion.presentation.flow.CompanionFlowStage
import kotlin.test.Test
import kotlin.test.assertEquals

class CompanionNavigatorTest {
    @Test
    fun `every presentation stage maps to one stable route`() {
        assertEquals(CompanionRoute.ConnectDesktop, CompanionNavigator.routeFor(CompanionFlowStage.CONNECT_DESKTOP))
        assertEquals(CompanionRoute.CertificateSetup, CompanionNavigator.routeFor(CompanionFlowStage.CERTIFICATE_SETUP))
        assertEquals(CompanionRoute.InspectionHome, CompanionNavigator.routeFor(CompanionFlowStage.INSPECTION_HOME))
    }

    @Test
    fun `reconcile removes stale restored routes behind current setup gate`() {
        val backStack = mutableListOf<NavKey>(CompanionRoute.ConnectDesktop, CompanionRoute.CertificateSetup)

        CompanionNavigator.reconcile(backStack, CompanionRoute.CertificateSetup)

        assertEquals(listOf<NavKey>(CompanionRoute.CertificateSetup), backStack)
    }

    @Test
    fun `reconcile keeps an already valid root stable`() {
        val backStack = mutableListOf<NavKey>(CompanionRoute.CertificateSetup)

        CompanionNavigator.reconcile(backStack, CompanionRoute.CertificateSetup)

        assertEquals(listOf<NavKey>(CompanionRoute.CertificateSetup), backStack)
    }
}
