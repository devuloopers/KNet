package com.devuloopers.knet.domain.policy

import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.model.*
import kotlin.test.*

/** Admission-derived identity is independent from network address, application name and UI selection. */
class PolicyWorkspaceTest {
    private val a=PolicyWorkspace.Device(RegisteredDeviceId("full-identity-a"))
    private val b=PolicyWorkspace.Device(RegisteredDeviceId("full-identity-b"))

    @Test fun trustedDeviceAdmissionKindsUseFullIdentityButAnonymousAndCustomKindsDoNotGuess() {
        for(kind in listOf(IngressKind.LanPairedDevice,IngressKind.AdbDevice,IngressKind.CompanionDirect,IngressKind.CompanionRelay)) {
            assertEquals(a,PolicyWorkspace.fromIngress(IngressContext(kind,ClientIdentity(a.id.value),"same.application")))
            assertEquals(PolicyWorkspace.Other,PolicyWorkspace.fromIngress(IngressContext(kind)))
        }
        for(kind in listOf(IngressKind.Local,IngressKind.WifiLanClient,IngressKind.Custom("COMPANION_DIRECT")))
            assertEquals(PolicyWorkspace.Other,PolicyWorkspace.fromIngress(IngressContext(kind,ClientIdentity(a.id.value))))
        assertEquals(PolicyWorkspace.Other,PolicyWorkspace.fromIngress(null))
        assertEquals(PolicyWorkspace.Other,PolicyWorkspace.fromClient(TlsFallbackClientScope.LocalDesktop))
        assertEquals(PolicyWorkspace.Other,PolicyWorkspace.fromClient(TlsFallbackClientScope.NetworkAddress(IngressKind.WifiLanClient,"192.0.2.1")))
    }

    @Test fun privateOwnershipAndSharedAvailabilityRemainSeparateFromActivation() {
        assertFailsWith<IllegalArgumentException> { PolicyDefinition("rule",RuleAvailability.Private(a),1,setOf(b)) }
        val shared=PolicyDefinition("rule",RuleAvailability.Shared,1,emptySet())
        for(workspace in listOf(a,b,PolicyWorkspace.Other)) { assertTrue(shared.visibleTo(workspace)); assertFalse(shared.activeOn(workspace)) }
        val private=PolicyDefinition("rule",RuleAvailability.Private(a),1,setOf(a))
        assertTrue(private.visibleTo(a)); assertFalse(private.visibleTo(b)); assertFalse(private.activeOn(b))
    }

    @Test fun storageKeysRoundTripWithoutCollidingWithOtherTrafficAndMalformedKeysAreRejected() {
        val namedOther=PolicyWorkspace.Device(RegisteredDeviceId("other"))
        assertNotEquals(PolicyWorkspace.Other.key,namedOther.key)
        for(workspace in listOf(a,b,namedOther,PolicyWorkspace.Other)) assertEquals(workspace,PolicyWorkspace.decode(workspace.key))
        for(value in listOf("all","device:","OTHER","")) assertFails { PolicyWorkspace.decode(value) }
    }

    @Test fun editorCopiesRetainTheirSessionAndOriginalAvailabilityButNewEditorsHaveDifferentIds() {
        val definition=PolicyDefinition("rule",RuleAvailability.Private(a),3,setOf(a))
        val first=definition.editContext(a); val second=definition.editContext(a)
        assertNotEquals(first.editorId,second.editorId)
        val promoted=first.copy(availability=RuleAvailability.Shared)
        assertEquals(first.editorId,promoted.editorId)
        assertEquals(RuleAvailability.Private(a),promoted.originalAvailability)
        assertEquals(setOf(a),promoted.expectedUsage)
        assertEquals(3L,promoted.expectedRevision)
    }
}
