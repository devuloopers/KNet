package com.devuloopers.knet.companion.android

import com.devuloopers.knet.companion.application.contract.CompanionCredentialStore
import com.devuloopers.knet.companion.application.contract.CompanionDeviceIdentityProvider
import com.devuloopers.knet.companion.application.contract.CompanionDeviceProofSigner
import com.devuloopers.knet.companion.application.contract.CompanionInvitationCodec
import com.devuloopers.knet.companion.application.contract.CompanionNetworkObserver
import com.devuloopers.knet.companion.application.contract.CompanionRegistrationRepository
import com.devuloopers.knet.companion.application.contract.InvitationDecodeResult
import com.devuloopers.knet.companion.model.CompanionCredentialReference
import com.devuloopers.knet.companion.model.CompanionDesktopId
import com.devuloopers.knet.companion.model.CompanionFailure
import com.devuloopers.knet.companion.model.CompanionFailureCode
import com.devuloopers.knet.companion.model.CompanionNetworkState
import com.devuloopers.knet.companion.model.CompanionRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AndroidCompanionProductGraphTest {
    @Test
    fun `graph exposes composed contracts and closes owned resources once in reverse order`() {
        val registrations = EmptyRegistrationRepository()
        val credentials = EmptyCredentialStore()
        val invitationCodec = CompanionInvitationCodec {
            InvitationDecodeResult.Rejected(
                CompanionFailure(CompanionFailureCode.INVITATION_INVALID, "Invalid invitation.", false),
            )
        }
        val identityProvider = CompanionDeviceIdentityProvider { error("Not invoked by composition test.") }
        val proofSigner = CompanionDeviceProofSigner { _, _ -> error("Not invoked by composition test.") }
        val network = CompanionNetworkObserver { MutableStateFlow(CompanionNetworkState.Unknown) }
        val closed = mutableListOf<String>()
        val graph = AndroidCompanionProductGraph(
            registrations = registrations,
            credentials = credentials,
            invitationCodec = invitationCodec,
            identityProvider = identityProvider,
            proofSigner = proofSigner,
            network = network,
            ownedResources = listOf(
                AutoCloseable { closed += "first" },
                AutoCloseable { closed += "second" },
            ),
        )

        assertSame(registrations, graph.registrations)
        assertSame(credentials, graph.credentials)
        assertSame(invitationCodec, graph.invitationCodec)
        assertSame(identityProvider, graph.identityProvider)
        assertSame(proofSigner, graph.proofSigner)
        assertSame(network, graph.network)

        graph.close()
        graph.close()

        assertEquals(listOf("second", "first"), closed)
    }

    private class EmptyRegistrationRepository : CompanionRegistrationRepository {
        override val registrations: StateFlow<List<CompanionRegistration>> = MutableStateFlow(emptyList())
        override val activeRegistration: StateFlow<CompanionRegistration?> = MutableStateFlow(null)

        override suspend fun upsert(registration: CompanionRegistration, makeActive: Boolean) = Unit
        override suspend fun setActive(desktopId: CompanionDesktopId?): Boolean = desktopId == null
        override suspend fun remove(desktopId: CompanionDesktopId): CompanionRegistration? = null
    }

    private class EmptyCredentialStore : CompanionCredentialStore {
        override suspend fun write(reference: CompanionCredentialReference, credential: String) = Unit
        override suspend fun read(reference: CompanionCredentialReference): String? = null
        override suspend fun remove(reference: CompanionCredentialReference) = Unit
    }
}
