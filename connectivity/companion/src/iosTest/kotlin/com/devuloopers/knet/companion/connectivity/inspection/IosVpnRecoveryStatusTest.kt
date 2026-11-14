package com.devuloopers.knet.companion.connectivity.inspection

import com.devuloopers.knet.companion.model.CompanionInspectionMode
import com.devuloopers.knet.companion.model.CompanionInspectionState
import platform.NetworkExtension.*
import kotlin.test.*

/** Verifies the native status projection without requiring a signed Network Extension in Simulator. */
class IosVpnRecoveryStatusTest {
    @Test fun reassertingPreservesRunningInterfaceAndConnectedClearsRecovery() {
        val running = CompanionInspectionState.Running(CompanionInspectionMode.DEVICE_VPN, 1_000L, true)
        val recovering = assertIs<CompanionInspectionState.Running>(running.withIosVpnStatus(NEVPNStatusReasserting))
        assertTrue(recovering.reconnecting)
        assertEquals(running.startedAtEpochMillis, recovering.startedAtEpochMillis)
        assertEquals(running, recovering.withIosVpnStatus(NEVPNStatusConnected))
        assertEquals(recovering, recovering.withIosVpnStatus(NEVPNStatusConnecting))
    }
    @Test fun disconnectIsTerminalButLateNotificationsCannotResurrectStoppedVpn() {
        val running = CompanionInspectionState.Running(CompanionInspectionMode.DEVICE_VPN, 1_000L, true)
        assertIs<CompanionInspectionState.Failed>(running.withIosVpnStatus(NEVPNStatusDisconnected))
        assertIs<CompanionInspectionState.Failed>(running.withIosVpnStatus(NEVPNStatusInvalid))
        assertEquals(CompanionInspectionState.Stopped, CompanionInspectionState.Stopped.withIosVpnStatus(NEVPNStatusReasserting))
        assertEquals(CompanionInspectionState.Stopping, CompanionInspectionState.Stopping.withIosVpnStatus(NEVPNStatusConnected))
    }
}
