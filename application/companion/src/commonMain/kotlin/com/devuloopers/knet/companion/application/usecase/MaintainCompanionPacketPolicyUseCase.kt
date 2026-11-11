package com.devuloopers.knet.companion.application.usecase

import com.devuloopers.knet.companion.application.contract.CompanionPacketPolicySink
import com.devuloopers.knet.companion.application.contract.CompanionPacketPolicySource
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.PacketPolicyUpdateResult
import com.devuloopers.knet.companion.model.CompanionRegistration
import com.devuloopers.knet.companion.model.PacketConditionConfiguration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.TimeSource

/** Why a native owner stopped accepting live updates; normal session cancellation propagates instead. */
public enum class PacketPolicyMaintenanceResult {
    /** The active pairing changed, so old-session reads are no longer authoritative. */
    REGISTRATION_CHANGED,
    /** The native owner has stopped. */
    INACTIVE,
    /** The native owner cannot apply policies without restart. */
    UNSUPPORTED,
}

/**
 * Maintains one running provider's global policy over the pinned authenticated control path.
 *
 * The service owns this coroutine. Reads are sequential and bounded; stale in-flight results are rejected after
 * pairing changes or cancellation. A transient read failure retains the last policy for a bounded lease; expiry
 * releases shaping rather than holding stale Offline/slow work indefinitely. This does not create a job per flow.
 */
public class MaintainCompanionPacketPolicyUseCase(
    private val source: CompanionPacketPolicySource,
    private val registrations: CompanionRegistrationRepository,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val pollIntervalMillis: Long = 250L,
    private val readTimeoutMillis: Long = 750L,
    private val leaseMillis: Long = 5_000L,
) {
    init {
        require(pollIntervalMillis > 0 && readTimeoutMillis > 0 && leaseMillis >= pollIntervalMillis + readTimeoutMillis)
    }

    /** Runs until the native session cancels, pairing changes, or the sink reports unavailable capability. */
    public suspend fun execute(
        registration: CompanionRegistration,
        initiallyApplied: PacketConditionConfiguration,
        sink: CompanionPacketPolicySink,
    ): PacketPolicyMaintenanceResult {
        var applied = initiallyApplied
        var lastFreshRead = timeSource.markNow()
        while (true) {
            val active = registrations.activeRegistration.value
            if (!sameIdentity(registration, active)) {
                sink.apply(PacketConditionConfiguration.Disabled)
                return PacketPolicyMaintenanceResult.REGISTRATION_CHANGED
            }
            active!!
            val fetched = try {
                withTimeoutOrNull(readTimeoutMillis) { source.fetch(active) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { null }
            currentCoroutineContext().ensureActive()
            // Endpoint/credential refresh may race a read. Use its result only if that exact registration is still active.
            if (registrations.activeRegistration.value != active) continue
            if (fetched != null) lastFreshRead = timeSource.markNow()
            val target = fetched ?: if (lastFreshRead.elapsedNow().inWholeMilliseconds >= leaseMillis)
                PacketConditionConfiguration.Disabled else null
            if (target != null && target != applied) {
                when (sink.apply(target)) {
                    PacketPolicyUpdateResult.APPLIED -> applied = target
                    PacketPolicyUpdateResult.INACTIVE -> return PacketPolicyMaintenanceResult.INACTIVE
                    PacketPolicyUpdateResult.UNSUPPORTED -> return PacketPolicyMaintenanceResult.UNSUPPORTED
                }
            }
            delay(pollIntervalMillis)
        }
    }

    private fun sameIdentity(original: CompanionRegistration, active: CompanionRegistration?): Boolean =
        active != null && active.desktopId == original.desktopId && active.deviceId == original.deviceId &&
            active.rootCertificateSha256 == original.rootCertificateSha256 &&
            active.transportIdentitySha256 == original.transportIdentitySha256
}
