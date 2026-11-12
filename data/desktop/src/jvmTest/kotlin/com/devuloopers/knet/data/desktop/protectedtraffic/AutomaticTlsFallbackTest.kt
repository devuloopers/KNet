package com.devuloopers.knet.data.desktop.protectedtraffic

import com.devuloopers.knet.application.contract.protectedtraffic.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.engine.proxy.tls.*
import com.devuloopers.knet.traffic.id.CaptureSessionId
import com.devuloopers.knet.traffic.model.*
import kotlinx.coroutines.test.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Automatic decisions require explicit certificate evidence and retain bounded, isolated, temporary ownership. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AutomaticTlsFallbackTest {
    private val session = CaptureSessionId("session-a")
    private val client = TlsFallbackClientScope.Identified(IngressKind.CompanionDirect, ClientIdentity("device-a"))
    private val request = TlsInterceptionRequest("192.0.2.10", 443, "api.example.com",
        clientContext = TlsInterceptionClientContext(session, client), clientHelloObserved = true)
    private val rejected = TlsInspectionFailure(InspectionFailureReason.CERTIFICATE_REJECTED, TlsCertificatePresentation.PRESENTED)

    @Test fun `confirmed rejection automatically tunnels a new classified connection without creating a saved rule`() = runTest {
        val cache = InMemoryAutomaticTlsFallback(backgroundScope, elapsedMillis = { testScheduler.currentTime })
        cache.activateSession(session)
        val configuration = ProtectedTrafficConfiguration()
        val policy = ProtectedTrafficTlsInterceptionPolicy({ configuration }, automaticFallback = cache, inspectionAuthorityIdentity = { "valid-ca" })
        val initial = policy.decide(request)
        assertEquals(TlsInterceptionMode.INSPECT, initial.mode)
        policy.inspectionFailed(request, initial, rejected)
        val next = policy.decide(request)
        assertEquals(TlsInterceptionMode.TUNNEL, next.mode)
        assertEquals(ProtectionDecisionOrigin.AUTOMATIC, next.origin)
        assertEquals(TlsInterceptionPolicyAction.TUNNEL, next.policyAction)
        assertNull(next.ruleId)
        assertTrue(configuration.rules.isEmpty())
        assertEquals(TlsInterceptionMode.INSPECT, policy.decide(request.copy(serverName = null, clientHelloObserved = false)).mode,
            "CONNECT alone must not reuse a learned SNI decision")
    }

    @Test fun `unknown handshake setup failure absent presentation invalid authority and anonymous clients never learn`() = runTest {
        val cache = InMemoryAutomaticTlsFallback(backgroundScope); cache.activateSession(session)
        var authority: String? = "valid-ca"
        val policy = ProtectedTrafficTlsInterceptionPolicy({ ProtectedTrafficConfiguration() }, automaticFallback = cache, inspectionAuthorityIdentity = { authority })
        for (reason in InspectionFailureReason.entries) for (presentation in TlsCertificatePresentation.entries) {
            if (reason == InspectionFailureReason.CERTIFICATE_REJECTED && presentation == TlsCertificatePresentation.PRESENTED) continue
            policy.inspectionFailed(request, policy.decide(request), TlsInspectionFailure(reason, presentation))
            assertTrue(cache.state.value.keys.isEmpty(), "$reason / $presentation must not create fallback")
        }
        for (unavailable in listOf(request.copy(clientContext = null), request.copy(clientHelloObserved = false))) {
            assertNull(policy.decide(unavailable).automaticFallbackGeneration)
            policy.inspectionFailed(unavailable, policy.decide(unavailable), rejected)
        }
        authority = null
        policy.inspectionFailed(request, policy.decide(request), rejected)
        assertTrue(cache.state.value.keys.isEmpty())
    }

    @Test fun `client application exact hostname port and session boundaries never share fallback`() = runTest {
        val cache = InMemoryAutomaticTlsFallback(backgroundScope); cache.activateSession(session)
        val policy = ProtectedTrafficTlsInterceptionPolicy({ ProtectedTrafficConfiguration() }, automaticFallback = cache, inspectionAuthorityIdentity = { "ca" })
        policy.inspectionFailed(request, policy.decide(request), rejected)
        val alternatives = listOf(request.copy(serverName = "other.example.com"), request.copy(connectPort = 8443),
            request.copy(sourceApplication = TlsSourceApplicationId("com.example.other")),
            request.copy(clientContext = TlsInterceptionClientContext(session, TlsFallbackClientScope.Identified(IngressKind.CompanionDirect, ClientIdentity("device-b")))),
            request.copy(clientContext = TlsInterceptionClientContext(CaptureSessionId("other"), client)))
        alternatives.forEach { assertEquals(TlsInterceptionMode.INSPECT, policy.decide(it).mode, "$it crossed an automatic boundary") }
        assertEquals(TlsInterceptionMode.TUNNEL, policy.decide(request.copy(serverName = "API.EXAMPLE.COM.")).mode)
        assertNull(automaticTlsFallbackKey(session, client, "*.example.com", 443, null))
    }

    @Test fun `manual inspect tunnel block and imported exceptions always win and cannot learn again`() = runTest {
        val cache = InMemoryAutomaticTlsFallback(backgroundScope); cache.activateSession(session)
        var configuration = ProtectedTrafficConfiguration()
        val policy = ProtectedTrafficTlsInterceptionPolicy({ configuration }, automaticFallback = cache, inspectionAuthorityIdentity = { "ca" })
        val initial = policy.decide(request)
        policy.inspectionFailed(request, initial, rejected)
        for (action in ProtectedTrafficAction.entries) {
            configuration = configuration.copy(rules = listOf(ProtectedTrafficRule(ProtectedTrafficRuleId("manual"), action = action,
                destination = ProtectedDestinationSelector.parse("api.example.com"))))
            val decision = policy.decide(request)
            assertNull(decision.automaticFallbackGeneration)
            assertEquals(ProtectionDecisionOrigin.MANUAL, decision.origin)
            policy.inspectionFailed(request, initial, rejected)
            policy.inspectionFailed(request, decision, rejected)
            assertTrue(cache.state.value.keys.isEmpty())
        }
        configuration = configuration.copy(rules = configuration.rules.map { it.copy(origin = ProtectedTrafficRuleOrigin.MIGRATED_PRESET) })
        assertNull(policy.decide(request).automaticFallbackGeneration)
    }

    @Test fun `expiry is monotonic publishes removal and never extends when read or re-recorded`() = runTest {
        val cache = InMemoryAutomaticTlsFallback(backgroundScope, lifetimeMillis = 1_000, elapsedMillis = { testScheduler.currentTime })
        cache.activateSession(session)
        val epoch = cache.authorize(ProtectedTrafficConfiguration(), "ca")!!
        val key = automaticTlsFallbackKey(session, client, "api.example.com", 443, null)!!
        assertTrue(cache.record(key, epoch)); runCurrent()
        advanceTimeBy(900); runCurrent()
        cache.authorize(ProtectedTrafficConfiguration(), "ca"); assertTrue(cache.record(key, epoch))
        advanceTimeBy(101); runCurrent()
        assertTrue(cache.state.value.keys.isEmpty(), "Expiry must publish even without another connection")
    }

    @Test fun `session certificate mode and explicit user changes fence late handshake callbacks`() = runTest {
        val cache = InMemoryAutomaticTlsFallback(backgroundScope); cache.activateSession(session)
        val key = automaticTlsFallbackKey(session, client, "api.example.com", 443, null)!!
        val configuration = ProtectedTrafficConfiguration()
        var epoch = cache.authorize(configuration, "ca-1")!!
        cache.clear(); assertFalse(cache.record(key, epoch))
        epoch = cache.authorize(configuration, "ca-1")!!
        cache.authorize(configuration, "ca-2"); assertFalse(cache.record(key, epoch))
        epoch = cache.authorize(configuration, "ca-2")!!
        assertNull(cache.authorize(configuration.copy(automaticTlsFallbackEnabled = false), "ca-2"))
        assertFalse(cache.record(key, epoch))
        epoch = cache.authorize(configuration, "ca-2")!!
        cache.activateSession(CaptureSessionId("session-b")); assertFalse(cache.record(key, epoch))
        assertTrue(cache.state.value.keys.isEmpty())
        cache.activateSession(null); assertNull(cache.authorize(configuration, "ca-2"))
    }

    @Test fun `bounded cache evicts oldest entries and remains safe under concurrent rejection callbacks`() = runTest {
        val cache = InMemoryAutomaticTlsFallback(backgroundScope, maximumEntries = 8); cache.activateSession(session)
        val epoch = cache.authorize(ProtectedTrafficConfiguration(), "ca")!!
        val executor = Executors.newFixedThreadPool(4)
        try {
            repeat(100) { index -> executor.submit { cache.record(automaticTlsFallbackKey(session, client, "host-$index.example.com", 443, null)!!, epoch) } }
        } finally { executor.shutdown(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)) }
        assertEquals(8, cache.state.value.keys.size)
        val last = automaticTlsFallbackKey(session, client, "last.example.com", 443, null)!!
        assertTrue(cache.record(last, epoch)); assertEquals(8, cache.state.value.keys.size)
        assertTrue(last in cache.state.value.keys)
    }

    @Test fun `manual edits forget matching destinations without disrupting unrelated learned traffic`() = runTest {
        val cache = InMemoryAutomaticTlsFallback(backgroundScope); cache.activateSession(session)
        val configuration = ProtectedTrafficConfiguration()
        val epoch = cache.authorize(configuration, "ca")!!
        val first = automaticTlsFallbackKey(session, client, "api.example.com", 443, null)!!
        val other = automaticTlsFallbackKey(session, client, "other.example.com", 443, null)!!
        cache.record(first, epoch); cache.record(other, epoch)
        val rule = ProtectedTrafficRule(ProtectedTrafficRuleId("manual"), action = ProtectedTrafficAction.INSPECT,
            destination = ProtectedDestinationSelector.parse("api.example.com"), port = 443)
        cache.forget(rule)
        assertEquals(setOf(other), cache.state.value.keys)
        assertFalse(cache.record(first, epoch), "Manual choice must fence in-flight learning")
        cache.authorize(configuration.copy(rules = listOf(rule)), "ca")
        assertEquals(setOf(other), cache.state.value.keys)
    }

    @Test fun `classified TLS without SNI learns only its exact CONNECT IP destination`() = runTest {
        val cache = InMemoryAutomaticTlsFallback(backgroundScope); cache.activateSession(session)
        val policy = ProtectedTrafficTlsInterceptionPolicy({ ProtectedTrafficConfiguration() }, automaticFallback = cache, inspectionAuthorityIdentity = { "ca" })
        val direct = request.copy(serverName = null)
        policy.inspectionFailed(direct, policy.decide(direct), rejected)
        assertEquals(TlsInterceptionMode.TUNNEL, policy.decide(direct).mode)
        assertEquals(TlsInterceptionMode.INSPECT, policy.decide(direct.copy(connectHost = "192.0.2.11")).mode)
    }

    @Test fun `global inspect only tunnel block unready settings and absent capture disable automatic learning`() = runTest {
        val cache = InMemoryAutomaticTlsFallback(backgroundScope); cache.activateSession(session)
        for (configuration in listOf(ProtectedTrafficConfiguration(automaticTlsFallbackEnabled = false),
            ProtectedTrafficConfiguration(defaultAction = ProtectedTrafficAction.TUNNEL),
            ProtectedTrafficConfiguration(defaultAction = ProtectedTrafficAction.BLOCK),
            ProtectedTrafficConfiguration(configurationReady = false))) {
            val policy = ProtectedTrafficTlsInterceptionPolicy({ configuration }, automaticFallback = cache, inspectionAuthorityIdentity = { "ca" })
            val decision = policy.decide(request)
            assertNull(decision.automaticFallbackGeneration)
            policy.inspectionFailed(request, decision, rejected)
            assertTrue(cache.state.value.keys.isEmpty())
        }
        cache.activateSession(null)
        assertNull(cache.authorize(ProtectedTrafficConfiguration(), "ca"))
    }
}
