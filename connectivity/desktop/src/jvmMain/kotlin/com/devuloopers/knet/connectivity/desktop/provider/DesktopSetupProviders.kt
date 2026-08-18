package com.devuloopers.knet.connectivity.desktop.provider

import com.devuloopers.knet.connectivity.desktop.artifact.SetupArtifactStore
import com.devuloopers.knet.connectivity.model.*
import com.devuloopers.knet.connectivity.spi.SetupDescriptorProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Manual proxy instructions derived only from current endpoint/network snapshots. */
public class ManualProxySetupProvider : SetupDescriptorProvider {
    override val id: ConnectivityMechanismId = ConnectivityMechanismId("manual-proxy")
    override val capabilities: Set<ConnectivityCapability> = setOf(ConnectivityCapability.MANUAL_CONFIGURATION)

    override fun availability(context: ConnectivityContext): Flow<ConnectivityAvailability> =
        flowOf(context.preferredEndpoint()?.let { ConnectivityAvailability.Available }
            ?: ConnectivityAvailability.NetworkUnavailable("proxy_endpoint_missing"))

    override suspend fun describe(context: ConnectivityContext): SetupDescriptor {
        val endpoint = context.preferredEndpoint() ?: error("No proxy endpoint is published.")
        return SetupDescriptor(
            mechanismId = id,
            titleToken = "connectivity.manual.title",
            summaryToken = "connectivity.manual.summary",
            capabilities = capabilities,
            steps = listOf(SetupStep.ConfigureProxy(endpoint)),
            artifacts = emptyList(),
            contextVersion = context.version,
        )
    }
}

/** Deterministic PAC generator and descriptor provider. */
public class PacSetupProvider(
    private val artifacts: SetupArtifactStore,
) : SetupDescriptorProvider {
    override val id: ConnectivityMechanismId = ConnectivityMechanismId("pac")
    override val capabilities: Set<ConnectivityCapability> = setOf(
        ConnectivityCapability.PAC_ARTIFACT,
        ConnectivityCapability.MANUAL_CONFIGURATION,
    )

    override fun availability(context: ConnectivityContext): Flow<ConnectivityAvailability> =
        flowOf(context.preferredEndpoint()?.let { ConnectivityAvailability.Available }
            ?: ConnectivityAvailability.NetworkUnavailable("proxy_endpoint_missing"))

    override suspend fun describe(context: ConnectivityContext): SetupDescriptor {
        val endpoint = context.preferredEndpoint() ?: error("No proxy endpoint is published.")
        val artifact = artifacts.put(
            providerId = id.value,
            version = context.version,
            mediaType = "application/x-ns-proxy-autoconfig",
            extension = "pac",
            bytes = generatePac(endpoint).encodeToByteArray(),
        )
        return SetupDescriptor(
            mechanismId = id,
            titleToken = "connectivity.pac.title",
            summaryToken = "connectivity.pac.summary",
            capabilities = capabilities,
            steps = listOf(SetupStep.OpenUrl(artifact.downloadUrl)),
            artifacts = listOf(artifact),
            contextVersion = context.version,
        )
    }

    public fun generatePac(endpoint: ProxyEndpoint): String {
        val host = endpoint.host.requireSafeHost()
        val authority = if (':' in host && !host.startsWith('[')) "[$host]" else host
        return "function FindProxyForURL(url, host) {\n  return \"PROXY $authority:${endpoint.port}; DIRECT\";\n}\n"
    }
}

/** Apple configuration profile provider with no UI or proxy-engine dependency. */
public class AppleProfileSetupProvider(
    private val artifacts: SetupArtifactStore,
    private val certificateDer: () -> ByteArray,
) : SetupDescriptorProvider {
    override val id: ConnectivityMechanismId = ConnectivityMechanismId("apple-profile")
    override val capabilities: Set<ConnectivityCapability> = setOf(
        ConnectivityCapability.CERTIFICATE_INSTALLATION,
        ConnectivityCapability.MANUAL_CONFIGURATION,
    )

    override fun availability(context: ConnectivityContext): Flow<ConnectivityAvailability> = flowOf(
        if (context.preferredEndpoint() == null) {
            ConnectivityAvailability.NetworkUnavailable("proxy_endpoint_missing")
        } else {
            ConnectivityAvailability.Available
        },
    )

    override suspend fun describe(context: ConnectivityContext): SetupDescriptor {
        val endpoint = context.preferredEndpoint() ?: error("No proxy endpoint is published.")
        val profile = generateProfile(endpoint, certificateDer())
        val artifact = artifacts.put(
            providerId = id.value,
            version = context.version,
            mediaType = "application/x-apple-aspen-config",
            extension = "mobileconfig",
            bytes = profile.encodeToByteArray(),
        )
        return SetupDescriptor(
            mechanismId = id,
            titleToken = "connectivity.apple_profile.title",
            summaryToken = "connectivity.apple_profile.summary",
            capabilities = capabilities,
            steps = listOf(
                SetupStep.OpenUrl(artifact.downloadUrl),
                SetupStep.ConfirmAction("trust_installed_root_ca"),
            ),
            artifacts = listOf(artifact),
            contextVersion = context.version,
        )
    }

    public fun generateProfile(endpoint: ProxyEndpoint, certificate: ByteArray): String {
        val host = endpoint.host.requireSafeHost()
        return AppleProfileTemplateRenderer.render(host, endpoint.port, certificate)
    }
}

/** ADB is described independently; execution is delegated to an allow-listed managed adapter. */
public class AdbSetupProvider : SetupDescriptorProvider {
    override val id: ConnectivityMechanismId = ConnectivityMechanismId("adb-reverse")
    override val capabilities: Set<ConnectivityCapability> = setOf(
        ConnectivityCapability.DEVICE_COMMAND,
        ConnectivityCapability.REQUIRES_ACTIVATION,
    )

    override fun availability(context: ConnectivityContext): Flow<ConnectivityAvailability> = flowOf(
        if (context.proxyEndpoints.endpoints.isEmpty()) {
            ConnectivityAvailability.NetworkUnavailable("proxy_endpoint_missing")
        } else {
            ConnectivityAvailability.Available
        },
    )

    override suspend fun describe(context: ConnectivityContext): SetupDescriptor = SetupDescriptor(
        mechanismId = id,
        titleToken = "connectivity.adb_reverse.title",
        summaryToken = "connectivity.adb_reverse.summary",
        capabilities = capabilities,
        steps = listOf(
            SetupStep.RunCommand("adb_reverse_activate"),
            SetupStep.ConfigureProxy(
                ProxyEndpoint(
                    host = "127.0.0.1",
                    port = context.proxyEndpoints.endpoints.first().port,
                    scope = ProxyEndpointScope.LOOPBACK,
                    accessRequirement = context.proxyEndpoints.endpoints.first().accessRequirement,
                ),
            ),
        ),
        artifacts = emptyList(),
        contextVersion = context.version,
    )
}

private fun ConnectivityContext.preferredEndpoint(): ProxyEndpoint? =
    proxyEndpoints.endpoints.firstOrNull { it.scope == ProxyEndpointScope.LAN }

private fun String.requireSafeHost(): String = apply {
    require(length <= 253 && matches(Regex("[A-Za-z0-9._:%-]+"))) { "Proxy host is unsafe for setup generation." }
}
