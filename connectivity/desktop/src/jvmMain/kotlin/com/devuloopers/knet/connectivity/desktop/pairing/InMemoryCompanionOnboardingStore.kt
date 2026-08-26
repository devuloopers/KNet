package com.devuloopers.knet.connectivity.desktop.pairing

import com.devuloopers.knet.application.contract.pairing.CompanionOnboardingStore
import com.devuloopers.knet.application.contract.pairing.PendingCompanionOnboarding
import com.devuloopers.knet.companion.model.CompanionBootstrapId
import com.devuloopers.knet.companion.model.CompanionPairingInvitation
import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-local bounded store for ephemeral companion onboarding responses.
 *
 * @param maximumRecords hard capacity that prevents unbounded invitation retention.
 * @param nowEpochMillis clock used to prune expired records before publication.
 */
public class InMemoryCompanionOnboardingStore(
    private val maximumRecords: Int = DEFAULT_MAXIMUM_RECORDS,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : CompanionOnboardingStore {
    private val lock: Mutex = Mutex()
    private val records: MutableMap<CompanionBootstrapId, PendingCompanionOnboarding> = mutableMapOf()

    init {
        require(maximumRecords in 1..MAXIMUM_CONFIGURABLE_RECORDS) {
            "Companion onboarding record limit is invalid."
        }
    }

    override suspend fun put(pending: PendingCompanionOnboarding) {
        lock.withLock {
            removeExpired(nowEpochMillis())
            check(records.size < maximumRecords || pending.id in records) {
                "Companion onboarding capacity is exhausted."
            }
            records[pending.id] = pending
        }
    }

    override suspend fun claim(
        id: CompanionBootstrapId,
        retrievalSecretDigest: String,
        nowEpochMillis: Long,
    ): CompanionPairingInvitation? = lock.withLock {
        removeExpired(nowEpochMillis)
        val pending = records[id] ?: return@withLock null
        if (!pending.retrievalSecretDigest.constantTimeEquals(retrievalSecretDigest)) return@withLock null
        records.remove(id)?.invitation
    }

    private fun removeExpired(nowEpochMillis: Long) {
        records.entries.removeIf { (_, pending) -> nowEpochMillis >= pending.expiresAtEpochMillis }
    }

    private fun String.constantTimeEquals(other: String): Boolean = MessageDigest.isEqual(
        encodeToByteArray(),
        other.encodeToByteArray(),
    )

    private companion object {
        const val DEFAULT_MAXIMUM_RECORDS: Int = 4_096
        const val MAXIMUM_CONFIGURABLE_RECORDS: Int = 65_536
    }
}
