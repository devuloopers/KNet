package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.*
import com.devuloopers.knet.companion.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** Reads bounded pinned/authenticated desktop readiness; legacy peers remain explicitly unknown. */
public class FetchCompanionCaptureStatusUseCase(
    private val credentials: CompanionCredentialStore,
    private val control: CompanionControlTransport,
) {
    /** An unsupported/unavailable/malformed response never fabricates a stopped or recording state. */
    public suspend fun execute(registration: CompanionRegistration): CompanionDesktopCaptureState =
        withTimeoutOrNull(3_000L) {
            try {
                val credential = credentials.read(registration.credentialReference) ?: return@withTimeoutOrNull CompanionDesktopCaptureState.UNKNOWN
                val response = control.execute(CompanionControlRequest(
                    registration.controlEndpoint, registration.transportIdentitySha256,
                    registration.rootCertificateSha256, registration.rootCertificate,
                    CompanionControlOperation.FETCH_CAPTURE_STATUS, ByteArray(0),
                    CompanionControlAuthorization(registration.deviceId, credential),
                ))
                if (response.statusCode == 200) CompanionCaptureStatusCodec.decode(response.copyBody())
                else CompanionDesktopCaptureState.UNKNOWN
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { CompanionDesktopCaptureState.UNKNOWN }
        } ?: CompanionDesktopCaptureState.UNKNOWN
}
