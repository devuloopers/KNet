package com.devuloopers.knet.companion.android.inspection

import com.devuloopers.knet.companion.application.contract.CompanionInspectionConfiguration
import com.devuloopers.knet.companion.connectivity.inspection.AndroidInspectionBackendResult
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionInspectionMode
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.CompanionRootCertificate
import com.devuloopers.knet.companion.model.CompanionServiceEndpoint
import com.devuloopers.knet.companion.model.Sha256Fingerprint
import com.devuloopers.knet.companion.model.UnsupportedTrafficPolicy
import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.pairing.DeviceScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidInspectionRuntimeCoordinatorTest {
    @Test
    fun `start hands configuration to service and completes once`() = runTest {
        val coordinator = AndroidInspectionRuntimeCoordinator()
        var launches = 0
        val result = async {
            coordinator.requestStart(configuration()) { launches += 1 }
        }

        runCurrent()
        val request = assertNotNull(coordinator.claimStart())
        assertEquals(configuration(), request.configuration)
        assertTrue(coordinator.completeStart(request, AndroidInspectionBackendResult.Started))
        assertFalse(coordinator.completeStart(request, AndroidInspectionBackendResult.Started))

        assertEquals(AndroidInspectionBackendResult.Started, result.await())
        assertEquals(1, launches)
        assertNull(coordinator.claimStart())
    }

    @Test
    fun `running start is idempotent and stop waits for service cleanup`() = runTest {
        val coordinator = AndroidInspectionRuntimeCoordinator()
        val firstStart = async { coordinator.requestStart(configuration()) {} }
        runCurrent()
        val request = assertNotNull(coordinator.claimStart())
        coordinator.completeStart(request, AndroidInspectionBackendResult.Started)
        assertEquals(AndroidInspectionBackendResult.Started, firstStart.await())

        var repeatedLaunches = 0
        assertEquals(
            AndroidInspectionBackendResult.Started,
            coordinator.requestStart(configuration()) { repeatedLaunches += 1 },
        )
        assertEquals(0, repeatedLaunches)

        var stopSignals = 0
        val stopped = async { coordinator.requestStop { stopSignals += 1 } }
        runCurrent()
        assertEquals(1, stopSignals)
        coordinator.completeStop()
        stopped.await()

        var restarted = false
        val nextStart = async { coordinator.requestStart(configuration()) { restarted = true } }
        runCurrent()
        assertTrue(restarted)
        val nextRequest = assertNotNull(coordinator.claimStart())
        coordinator.completeStart(nextRequest, AndroidInspectionBackendResult.Started)
        assertEquals(AndroidInspectionBackendResult.Started, nextStart.await())
    }

    @Test
    fun `unclaimed start times out and leaves coordinator reusable`() = runTest {
        val coordinator = AndroidInspectionRuntimeCoordinator(operationTimeoutMillis = 50L)
        val timedOut = async { coordinator.requestStart(configuration()) {} }

        runCurrent()
        assertNotNull(coordinator.claimStart())
        advanceTimeBy(51L)
        runCurrent()

        val failure = assertIs<AndroidInspectionBackendResult.Failed>(timedOut.await())
        assertEquals(CompanionFailureCode.VPN_START_FAILED, failure.failure.code)
        assertNull(coordinator.claimStart())

        val retry = async { coordinator.requestStart(configuration()) {} }
        runCurrent()
        val retryRequest = assertNotNull(coordinator.claimStart())
        coordinator.completeStart(retryRequest, AndroidInspectionBackendResult.Started)
        assertEquals(AndroidInspectionBackendResult.Started, retry.await())
    }

    private fun configuration(): CompanionInspectionConfiguration = CompanionInspectionConfiguration(
        registration = CompanionRegistration(
            desktopId = CompanionDesktopId("desktop-1"),
            desktopDisplayName = "Development Mac",
            deviceId = RegisteredDeviceId("device-1"),
            controlEndpoint = CompanionServiceEndpoint("192.168.1.2", 8183, secure = true),
            proxyEndpoint = CompanionServiceEndpoint("192.168.1.2", 8184, secure = true),
            transportIdentitySha256 = Sha256Fingerprint("a".repeat(64)),
            rootCertificateSha256 = Sha256Fingerprint("b".repeat(64)),
            rootCertificate = CompanionRootCertificate(byteArrayOf(1, 2, 3)),
            credentialReference = CompanionCredentialReference("credential-reference"),
            scopes = setOf(DeviceScope.PROXY_STREAM),
            pairedAtEpochMillis = 1_000L,
            credentialExpiresAtEpochMillis = 2_000L,
        ),
        mode = CompanionInspectionMode.DEVICE_VPN,
        unsupportedTrafficPolicy = UnsupportedTrafficPolicy.REJECT,
        fullHttpsInspection = true,
    )
}
