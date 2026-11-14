package com.devuloopers.knet.products.desktop.policy

import com.devuloopers.knet.application.contract.device.*
import com.devuloopers.knet.application.contract.breakpoint.*
import com.devuloopers.knet.application.contract.pairing.TrustedDeviceStore
import com.devuloopers.knet.application.coordinator.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.coordinator.pairing.PairingCoordinator
import com.devuloopers.knet.connectivity.desktop.gateway.*
import com.devuloopers.knet.connectivity.desktop.pairing.JvmPairingCrypto
import com.devuloopers.knet.data.desktop.policy.*
import com.devuloopers.knet.data.desktop.protectedtraffic.ProtectedTrafficTlsInterceptionPolicy
import com.devuloopers.knet.domain.policy.*
import com.devuloopers.knet.domain.networkconditions.*
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.domain.rules.model.*
import com.devuloopers.knet.engine.certificate.*
import com.devuloopers.knet.engine.interceptor.*
import com.devuloopers.knet.engine.proxy.*
import com.devuloopers.knet.engine.proxy.tls.ServerTlsContextProvider
import com.devuloopers.knet.engine.simulator.*
import com.devuloopers.knet.identity.*
import com.devuloopers.knet.pairing.*
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.storage.device.entity.RegisteredDeviceEntity
import com.devuloopers.knet.traffic.model.*
import io.netty.handler.ssl.SslContextBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.KeyStore
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.*
import kotlin.test.*

/** Real Room -> immutable runtime -> authenticated carrier -> proxy -> real origin, with no desktop UI. */
class DevicePolicySocketEndToEndTest {
    @Test fun sharedDefinitionShapesOnlyEnabledDeviceAndEachActivatedDeviceGetsItsOwnBudget(): Unit = runBlocking {
        Fixture().use { fixture ->
            fixture.library.saveCondition(a, fixture.rule, RuleAvailability.Shared)
            fixture.library.updateSettings(a) { it.copy(networkEnabled=true) }
            fixture.library.updateSettings(b) { it.copy(networkEnabled=true) }
            fixture.awaitNetwork(a,true)
            val first = async(Dispatchers.IO) { fixture.request("a",bytes=12_500) }
            val sibling = async(Dispatchers.IO) { fixture.request("b",bytes=12_500) }
            assertTrue(first.await() >= 850,"Device A must be shaped")
            assertTrue(sibling.await() < 800,"Shared availability must not shape inactive B")
            fixture.library.activate(b,PolicyRuleKind.NETWORK_CONDITION,"shared-speed",true)
            fixture.awaitNetwork(b,true)
            val started=System.nanoTime()
            val both = listOf("a","b").map { id -> async(Dispatchers.IO) { fixture.request(id,bytes=12_500) } }.awaitAll()
            assertTrue(both.all { it >= 850 && it < 1_850 },"Each device has one independent 100 kbps reservation: $both")
            assertTrue((System.nanoTime()-started)/1_000_000 < 1_850,"Two device budgets must run concurrently")
            val uploads = listOf("a","b").map { id -> async(Dispatchers.IO) { fixture.request(id,bytes=12_500,upload=true) } }.awaitAll()
            assertTrue(uploads.all { it >= 850 && it < 1_850 },"Upload ownership must be independent: $uploads")
        }
    }

    @Test fun oneDeviceBreakpointDoesNotPauseSiblingAndPolicyEditDoesNotTransferPendingOwner(): Unit = runBlocking {
        Fixture().use { fixture ->
            val rule=BreakpointRule("shared-break","pause","*",phase=BreakpointPhase.REQUEST)
            fixture.library.saveBreakpoint(a,rule,RuleAvailability.Shared)
            withTimeout(5_000) { while(!fixture.coordinator.mayInterceptFor(a,fixture.requestSnapshot(),BreakpointPhase.REQUEST)) delay(10) }
            val suspended=async(Dispatchers.IO) { fixture.request("a") }
            val pending=withTimeout(5_000) { fixture.coordinator.pendingBreakpoints.first { it.isNotEmpty() }.single() }
            assertEquals(a,pending.candidate.workspace)
            assertTrue(fixture.request("b") < 1_000)
            fixture.library.activate(a,PolicyRuleKind.BREAKPOINT,rule.id,false)
            assertEquals(pending.id,fixture.coordinator.pendingBreakpoints.value.single().id)
            assertTrue(fixture.coordinator.resolve(pending.id,BreakpointDecision.ContinueUnchanged))
            suspended.await()
            withTimeout(5_000) { fixture.coordinator.pendingBreakpoints.first { it.isEmpty() } }
        }
    }

    @Test fun tlsTunnelBlockAndInspectionResolveFromCarrierIdentityEvenWithCaptureUnavailable(): Unit = runBlocking {
        Fixture().use { fixture ->
            fixture.library.updateSettings(a) { it.copy(protectedAction=ProtectedTrafficAction.TUNNEL) }
            fixture.library.updateSettings(b) { it.copy(protectedAction=ProtectedTrafficAction.BLOCK) }
            fixture.request("a",secure=true)
            assertEquals(0,fixture.inspectionCertificates.get(),"Tunneling must retain origin certificate without issuing a KNet leaf")
            assertFails { fixture.request("b",secure=true) }
            assertEquals(0,fixture.inspectionCertificates.get(),"Blocked device cannot reach inspection")
            fixture.library.updateSettings(b) { it.copy(protectedAction=ProtectedTrafficAction.INSPECT) }
            fixture.request("b",secure=true)
            assertEquals(1,fixture.inspectionCertificates.get())
        }
    }

    @Test fun automaticExactReplacementChangesOnlyItsAuthenticatedDeviceWhileSiblingStreamingContinues(): Unit = runBlocking {
        Fixture().use { fixture ->
            fixture.library.saveCondition(a,fixture.rule,RuleAvailability.Shared)
            for (workspace in listOf(a,b)) fixture.library.updateSettings(workspace) { it.copy(networkEnabled=true) }
            fixture.library.activate(b,PolicyRuleKind.NETWORK_CONDITION,fixture.rule.id.value,true)
            fixture.awaitNetwork(a,true); fixture.awaitNetwork(b,true)
            val queued=async { fixture.engine.observeWorkspace(b).first { it.activeFlows>0 && it.queuedBytes>0 } }
            val sibling=async(Dispatchers.IO) { fixture.request("b",bytes=25_000) }
            withTimeout(5_000) { queued.await() }
            val blocked=fixture.rule.copy(id=NetworkConditionRuleId("replacement-offline"),profileId=NetworkConditionBuiltIns.OFFLINE.id)
            val result=fixture.library.saveCondition(a,blocked,RuleAvailability.Shared)
            assertEquals(setOf(fixture.rule.id.value),result.replacedRuleIds)
            withTimeout(5_000) { while (fixture.engine.configurationSnapshot(a).rules.singleOrNull()?.id!=blocked.id) delay(10) }
            assertFails { fixture.request("a") }
            assertTrue(sibling.await() in 1_850..3_500,"Sibling's queued stream must finish with its original budget")
            assertEquals(fixture.rule.id,fixture.engine.configurationSnapshot(b).rules.single().id)
            val recovered=blocked.copy(id=NetworkConditionRuleId("replacement-recovered"),profileId=fixture.rule.profileId)
            assertEquals(setOf(blocked.id.value),fixture.library.saveCondition(a,recovered,RuleAvailability.Shared).replacedRuleIds)
            withTimeout(5_000) { while (fixture.engine.configurationSnapshot(a).rules.singleOrNull()?.id!=recovered.id) delay(10) }
            fixture.request("a")
        }
    }

    @Test fun offlineDeviceDoesNotBreakSiblingAndRemovingConditionsRestoresForwarding(): Unit = runBlocking {
        Fixture().use { fixture ->
            fixture.library.updateSettings(a) { it.copy(networkEnabled=true,profileId=NetworkConditionBuiltIns.OFFLINE.id) }
            withTimeout(5_000) { while(fixture.engine.configurationSnapshot(a).globalProfileId!=NetworkConditionBuiltIns.OFFLINE.id) delay(10) }
            assertFails { fixture.request("a") }
            fixture.request("b")
            fixture.library.updateSettings(a) { it.copy(networkEnabled=false) }
            fixture.awaitNetwork(a,false)
            fixture.request("a")
            assertFails { fixture.request("a",credential="invalid") }
            fixture.request("b")
        }
    }

    /** Opt-in physical clients use disposable credentials/CA; existing Companion data is never accessed. */
    @org.junit.experimental.categories.Category(PhysicalDevicePolicyQualification::class)
    @Test fun physicalAndroidClientsVerifyIndependentDownloadUploadAndBreakpointOwnership(): Unit = runBlocking {
        val apk=checkNotNull(System.getenv("KNET_POLICY_PROBE_APK")) { "Set KNET_POLICY_PROBE_APK to the disposable qualifier APK." }
        val adb="/opt/homebrew/bin/adb"
        val devices=command(adb,"devices").lineSequence().map { it.split(Regex("\\s+")) }
            .filter { it.size>=2 && it[1]=="device" }.map { it[0] }.toList().take(2)
        assertEquals(2,devices.size,"Two authorized ADB devices are required")
        devices.forEach { serial ->
            val mappings = command(adb,"-s",serial,"reverse","--list").split(Regex("\\s+"))
            check("tcp:19291" !in mappings) { "Qualification port 19291 is already in use on $serial." }
        }
        Fixture().use { fixture ->
            val evidence=java.util.concurrent.CopyOnWriteArrayList<String>()
            try {
                devices.forEach { serial ->
                    command(adb,"-s",serial,"install","-r",apk!!)
                    command(adb,"-s",serial,"reverse","tcp:19291","tcp:${fixture.gatewayPort}")
                }
                fixture.library.saveCondition(a,fixture.rule,RuleAvailability.Shared)
                for (workspace in listOf(a,b)) fixture.library.updateSettings(workspace) { it.copy(networkEnabled=true) }
                fixture.awaitNetwork(a,true)
                suspend fun probes(upload: Boolean): List<Long> = devices.mapIndexed { index, serial -> async(Dispatchers.IO) {
                    val id=if(index==0) "a" else "b"
                    val output=command(adb,"-s",serial,"shell","am","instrument","-w","-e","device",id,"-e","credential",fixture.credentials.getValue(id),
                        "-e","certificate",fixture.caBase64,"-e","originPort",fixture.originPort.toString(),"-e","upload",upload.toString(),
                        "com.devuloopers.knet.policyqualifier/com.devuloopers.knet.policyqualifier.Probe")
                    check("passed=true" in output) { "Physical probe failed for $serial: $output" }
                    val elapsed=Regex("elapsedMillis=([0-9]+)").find(output)!!.groupValues[1].toLong()
                    evidence += "$id upload=$upload elapsedMillis=$elapsed"
                    elapsed
                } }.awaitAll()
                var times=probes(false)
                assertTrue(times[0]>=850); assertTrue(times[1]<800)
                fixture.library.activate(b,PolicyRuleKind.NETWORK_CONDITION,"shared-speed",true); fixture.awaitNetwork(b,true)
                times=probes(false); assertTrue(times.all { it in 850..1_850 },"Concurrent physical downloads: $times")
                times=probes(true); assertTrue(times.all { it in 850..1_850 },"Concurrent physical uploads: $times")
                for (workspace in listOf(a,b)) fixture.library.updateSettings(workspace) { it.copy(networkEnabled=false) }
                fixture.awaitNetwork(a,false); fixture.awaitNetwork(b,false)
                fixture.library.saveBreakpoint(a,BreakpointRule("physical-break",urlPattern="*",phase=BreakpointPhase.REQUEST),RuleAvailability.Shared)
                delay(100)
                val live=async { probes(false) }
                val pending=withTimeout(10_000) { fixture.coordinator.pendingBreakpoints.first { it.isNotEmpty() }.single() }
                assertEquals(a,pending.candidate.workspace)
                delay(200)
                assertTrue(fixture.coordinator.resolve(pending.id,BreakpointDecision.ContinueUnchanged))
                live.await()
                evidence += "breakpoint owner=a sibling=b forwarded"
                val report=System.getenv("KNET_POLICY_PROBE_REPORT")
                if(report!=null) java.io.File(report).writeText(evidence.joinToString("\n"))
            } finally {
                devices.forEach { serial ->
                    runCatching { command(adb,"-s",serial,"reverse","--remove","tcp:19291") }
                    runCatching { command(adb,"-s",serial,"uninstall","com.devuloopers.knet.policyqualifier") }
                }
            }
        }
    }

    private class Fixture : AutoCloseable {
        private val directory=Files.createTempDirectory("knet-device-socket-").toFile()
        private val database=DatabaseFactory.create(directory.resolve("test.db"))
        private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        val library=RoomDevicePolicyLibrary(database,scope)
        val coordinator=BreakpointCoordinator()
        private val views=PolicyRepositoryViews(library,coordinator,scope)
        val engine=NetworkConditionEngine({ NetworkConditionConfiguration() })
        private val profile=NetworkConditionProfile(NetworkConditionProfileId("socket-speed"),"Socket speed",
            download=NetworkDirectionCondition(100_000),upload=NetworkDirectionCondition(100_000))
        val rule=NetworkConditionRule(NetworkConditionRuleId("shared-speed"),NetworkConditionTarget.parse("127.0.0.1"),profile.id)
        val inspectionCertificates=AtomicInteger()
        private val ca=CertificateAuthority.generate(commonName="Disposable device policy fixture")
        val caBase64:String=java.util.Base64.getEncoder().encodeToString(ca.certificate.encoded)
        private val contexts=contexts(ca)
        private val crypto=JvmPairingCrypto()
        val credentials=mapOf("a" to crypto.randomToken(48),"b" to crypto.randomToken(48))
        private val attributions=IngressAttributionRegistry()
        private val workers=Executors.newCachedThreadPool()
        private val origin=ServerSocket(0)
        val originPort:Int get()=origin.localPort
        private val secureOrigin=contexts.first.serverSocketFactory.createServerSocket(0)
        private val proxy:KNetProxyServer
        private val gateway:AuthenticatedProxyGateway
        val gatewayPort:Int get()=gateway.boundPort!!
        init {
            runBlocking {
                for(id in listOf("a","b")) database.registeredDeviceDao().upsertRegisteredDevice(RegisteredDeviceEntity(id,"Same device name","PAIRED_COMPANION",1,1,null))
                library.snapshot.first { it.ready }
                library.saveProfile(profile)
            }
            engine.bindWorkspaces(ProjectedStateFlow(library.snapshot) { state -> state.settings.keys.associateWith(state::runtimeNetwork) },scope)
            for(server in listOf(origin,secureOrigin)) workers.submit {
                while(!server.isClosed) {
                    val socket=try { server.accept() } catch (_:java.net.SocketException) { break }
                    workers.submit { socket.use {
                        val head=readHeader(it.inputStream)
                        val count=Regex("(?im)^Content-Length: *([0-9]+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                        if(count>0) check(it.inputStream.readNBytes(count).size==count)
                        val size=Regex("bytes=([0-9]+)").find(head)?.groupValues?.get(1)?.toInt() ?: 4
                        val body=ByteArray(if(count>0) 4 else size) { 65 }
                        it.outputStream.write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        it.outputStream.write(body); it.outputStream.flush()
                    } }
                }
            }
            proxy=KNetProxyServer(port=ServerSocket(0).use { it.localPort },
                serverTlsContextProvider=ServerTlsContextProvider { host,_ ->
                    inspectionCertificates.incrementAndGet()
                    val leaf=LeafCertificateGenerator.generate(host,ca)
                    CompletableFuture.completedFuture(SslContextBuilder.forServer(leaf.keyPair.private,leaf.certificate).build())
                },verifyUpstreamTls=false,ingressAttribution=attributions,
                runtimePolicy=KNetProxyRuntimePolicy(gracefulShutdownTimeoutMillis=100),
                pipelineInitializers=listOf { pipeline ->
                    pipeline.addLast("device-condition",NetworkConditionChannelHandler(engine))
                    pipeline.addLast("device-breakpoint-aggregate",KNetBreakpointRequestAggregator(coordinator))
                    pipeline.addLast("device-breakpoint",KNetInterceptorHandler(coordinator))
                },tlsInterceptionPolicy=ProtectedTrafficTlsInterceptionPolicy({ library.snapshot.value.runtimeProtection(PolicyWorkspace.Other) },
                    configurationForWorkspace={ library.snapshot.value.runtimeProtection(it) }))
            proxy.start()
            val store=object:TrustedDeviceStore {
                private val devices=credentials.map { (id,credential) -> TrustedDevice(RegisteredDevice(RegisteredDeviceId(id),"Same name",DeviceRegistrationKind.PAIRED_COMPANION,1,1),
                    "fixture",crypto.digest(credential),setOf(DeviceScope.PROXY_STREAM),1,Long.MAX_VALUE) }
                override suspend fun getDevice(id:RegisteredDeviceId)=devices.firstOrNull { it.id==id }
                override fun observeDevices():Flow<List<TrustedDevice>> = MutableStateFlow(devices)
                override suspend fun putDevice(device:TrustedDevice)=error("unused")
                override suspend fun putInvitation(invitation:PendingPairingInvitation)=error("unused")
                override suspend fun claimInvitation(id:PairingInvitationId,secretDigest:String,nowEpochMillis:Long):PendingPairingInvitation?=null
                override suspend fun rotateCredential(id:RegisteredDeviceId,expectedCredentialDigest:String,newCredentialDigest:String,credentialExpiresAtEpochMillis:Long)=false
                override suspend fun revoke(id:RegisteredDeviceId,revokedAtEpochMillis:Long)=false
            }
            gateway=AuthenticatedProxyGateway("127.0.0.1",0,contexts.first.serverSocketFactory,{proxy.boundAddress()},PairingCoordinator(store,crypto,System::currentTimeMillis),attributions)
            gateway.start()
        }
        suspend fun awaitNetwork(workspace:PolicyWorkspace,enabled:Boolean) {
            withTimeout(5_000) { while(engine.configurationSnapshot(workspace).enabled!=enabled || enabled && library.snapshot.value.conditions.any { it.activeOn(workspace) } && engine.configurationSnapshot(workspace).rules.none { it.enabled }) delay(10) }
        }
        fun requestSnapshot()=com.devuloopers.knet.traffic.model.HttpRequestSnapshot(com.devuloopers.knet.traffic.model.http.RequestHead(
            com.devuloopers.knet.traffic.model.http.HttpMethod.GET,com.devuloopers.knet.traffic.model.http.RequestTarget.Absolute(
                com.devuloopers.knet.traffic.model.http.HttpScheme.fromToken("http"),com.devuloopers.knet.traffic.model.http.Authority("127.0.0.1",originPort),"/"),com.devuloopers.knet.traffic.model.http.ApplicationProtocol.fromToken("HTTP/1.1"), headers=emptyList()))
        fun request(id:String,bytes:Int=4,upload:Boolean=false,secure:Boolean=false,credential:String=credentials.getValue(id)):Long {
            val started=System.nanoTime()
            val carrier=contexts.second.socketFactory.createSocket("127.0.0.1",gatewayPort) as SSLSocket
            carrier.soTimeout=5_000
            carrier.use {
                val port=if(secure) secureOrigin.localPort else originPort
                val address="127.0.0.1:$port"
                var socket:Socket=carrier
                if(secure) {
                    carrier.outputStream.write("CONNECT $address HTTP/1.1\r\nHost: $address\r\nProxy-Authorization: Bearer $id:$credential\r\n\r\n".toByteArray()); carrier.outputStream.flush()
                    check(readHeader(carrier.inputStream).startsWith("HTTP/1.1 200"))
                    socket=contexts.second.socketFactory.createSocket(carrier,"127.0.0.1",port,true) as SSLSocket
                    socket.soTimeout=5_000
                }
                val path=if(secure) "/?bytes=$bytes" else "http://$address/?bytes=$bytes"
                val payload=if(upload) ByteArray(bytes) { 66 } else byteArrayOf()
                val auth=if(secure) "" else "Proxy-Authorization: Bearer $id:$credential\r\n"
                socket.outputStream.write("${if(upload) "POST" else "GET"} $path HTTP/1.1\r\nHost: $address\r\n${auth}Content-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                socket.outputStream.write(payload); socket.outputStream.flush()
                val head=readHeader(socket.inputStream); check(head.startsWith("HTTP/1.1 200")) { head }
                val length=Regex("(?im)^Content-Length: *([0-9]+)").find(head)!!.groupValues[1].toInt()
                check(socket.inputStream.readNBytes(length).size==length)
                socket.close()
            }
            return (System.nanoTime()-started)/1_000_000
        }
        override fun close() {
            gateway.close(); proxy.stop(); origin.close(); secureOrigin.close(); workers.shutdownNow()
            views.close(); runBlocking { scope.coroutineContext.job.cancelAndJoin() }; database.close(); directory.deleteRecursively()
        }
    }
    private companion object {
        val a=PolicyWorkspace.Device(RegisteredDeviceId("a")); val b=PolicyWorkspace.Device(RegisteredDeviceId("b"))
        fun readHeader(input:InputStream):String {
            val result=StringBuilder()
            while(!result.endsWith("\r\n\r\n")) { val value=input.read(); check(value>=0) { "Peer closed before response" }; result.append(value.toChar()); check(result.length<65_536) }
            return result.toString()
        }
        fun contexts(ca:CertificateAuthority):Pair<SSLContext,SSLContext> {
            val leaf=LeafCertificateGenerator.generate("127.0.0.1",ca)
            val keys=KeyStore.getInstance("PKCS12").apply { load(null); setKeyEntry("server",leaf.keyPair.private,charArrayOf(),arrayOf(leaf.certificate,ca.certificate)) }
            val manager=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keys,charArrayOf()) }
            val trust=KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("fixture",ca.certificate) }
            val trusted=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trust) }
            return SSLContext.getInstance("TLS").apply { init(manager.keyManagers,null,null) } to SSLContext.getInstance("TLS").apply { init(null,trusted.trustManagers,null) }
        }
        fun command(vararg arguments:String):String {
            val process=ProcessBuilder(*arguments).redirectErrorStream(true).start()
            val output=process.inputStream.bufferedReader().readText()
            check(process.waitFor(45,TimeUnit.SECONDS) && process.exitValue()==0) { "Fixture command failed: $output" }
            return output
        }
    }
}

/** Category selected only by the explicit physical qualification Gradle task. */
interface PhysicalDevicePolicyQualification
