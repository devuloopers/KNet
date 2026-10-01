package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionControlAuthorization
import com.devuloopers.knet.companion.application.contract.CompanionControlOperation
import com.devuloopers.knet.companion.application.contract.CompanionControlRequest
import com.devuloopers.knet.companion.application.contract.CompanionControlTransport
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.PacketConditionConfiguration
import com.devuloopers.knet.companion.model.PacketConditionConfigurationCodec
import kotlinx.coroutines.CancellationException

/** Fetches a bounded packet policy over the paired, pinned, authenticated companion control channel. */
public class FetchCompanionPacketConditionsUseCase(
    private val credentials: CompanionCredentialStore,
    private val control: CompanionControlTransport,
) {
    public suspend fun execute(registration: CompanionRegistration): PacketConditionConfiguration? {
        val credential = try {
            credentials.read(registration.credentialReference)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return null
        } ?: return null
        val response = try {
            control.execute(
                CompanionControlRequest(
                    endpoint = registration.controlEndpoint,
                    transportIdentitySha256 = registration.transportIdentitySha256,
                    rootCertificateSha256 = registration.rootCertificateSha256,
                    rootCertificate = registration.rootCertificate,
                    operation = CompanionControlOperation.FETCH_NETWORK_CONDITIONS,
                    body = ByteArray(0),
                    authorization = CompanionControlAuthorization(registration.deviceId, credential),
                ),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return null
        }
        if (response.statusCode != 200) return null
        return runCatching { PacketConditionConfigurationCodec.decode(response.copyBody()) }.getOrNull()
    }
}
