package com.devuloopers.knet.companion.android

import android.content.Context
import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.application.contract.CompanionInvitationCodec
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.connectivity.android.AndroidCompanionNetworkObserver
import com.devuloopers.knet.companion.data.ProtectedCompanionCredentialStore
import com.devuloopers.knet.companion.data.VersionedCompanionInvitationCodec
import com.devuloopers.knet.companion.data.VersionedCompanionRegistrationRepository
import com.devuloopers.knet.companion.data.android.AndroidCompanionRecordStore
import com.devuloopers.knet.companion.data.android.AndroidKeystoreCompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.data.android.AndroidKeystoreCompanionDeviceProofSigner
import com.devuloopers.knet.companion.data.android.AndroidKeystoreCompanionSecretStore

/**
 * Process-owned Android composition for companion capabilities that have production implementations.
 *
 * Transport, certificate, and VPN packet backends are deliberately absent until real adapters exist. This graph
 * must not substitute successful stubs for unavailable product capabilities.
 */
internal class AndroidCompanionProductGraph(
    val registrations: CompanionRegistrationRepository,
    val credentials: CompanionCredentialStore,
    val invitationCodec: CompanionInvitationCodec,
    val identityProvider: CompanionDeviceIdentityProvider,
    val proofSigner: CompanionDeviceProofSigner,
    val network: CompanionNetworkObserver,
    private val ownedResources: List<AutoCloseable>,
) : AutoCloseable {
    private val lifecycleLock = Any()
    private var closed: Boolean = false

    /** Releases process-owned callback registrations exactly once in reverse ownership order. */
    override fun close() {
        val shouldClose = synchronized(lifecycleLock) {
            if (closed) false else {
                closed = true
                true
            }
        }
        if (!shouldClose) return
        ownedResources.asReversed().forEach { resource -> runCatching(resource::close) }
    }

    companion object {
        /** Builds the Android graph from application-scoped platform services. */
        fun create(context: Context): AndroidCompanionProductGraph {
            val applicationContext = context.applicationContext
            val networkObserver = AndroidCompanionNetworkObserver(applicationContext)
            return AndroidCompanionProductGraph(
                registrations = VersionedCompanionRegistrationRepository(
                    AndroidCompanionRecordStore(applicationContext),
                ),
                credentials = ProtectedCompanionCredentialStore(
                    AndroidKeystoreCompanionSecretStore(applicationContext),
                ),
                invitationCodec = VersionedCompanionInvitationCodec(),
                identityProvider = AndroidKeystoreCompanionDeviceIdentityProvider(),
                proofSigner = AndroidKeystoreCompanionDeviceProofSigner(),
                network = networkObserver,
                ownedResources = listOf(networkObserver),
            )
        }
    }
}
