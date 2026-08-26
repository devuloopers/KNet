package com.devuloopers.knet.connectivity.desktop

import com.devuloopers.knet.application.contract.proxy.ProxyRuntime
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.connectivity.desktop.network.DesktopNetworkSnapshotMonitor
import com.devuloopers.knet.connectivity.model.ConnectivityContext
import com.devuloopers.knet.connectivity.model.ConnectivityContextVersion
import com.devuloopers.knet.connectivity.model.ConnectivityMechanismId
import com.devuloopers.knet.connectivity.model.ProxyEndpoint
import com.devuloopers.knet.connectivity.model.ProxyEndpointSnapshot
import com.devuloopers.knet.connectivity.model.ProxyEndpointVersion
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reconciles endpoint publication with desktop network metadata. It owns no proxy listener and
 * therefore network transitions invalidate descriptors without restarting or flushing traffic.
 */
public class DesktopConnectivityRuntime(
    proxyRuntime: ProxyRuntime,
    public val networkMonitor: DesktopNetworkSnapshotMonitor,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val version = AtomicLong(0L)
    private val endpointVersion = AtomicLong(0L)
    private val publishedEndpoints = MutableStateFlow<Map<ConnectivityMechanismId, ProxyEndpoint>>(emptyMap())
    private val mutableContext = MutableStateFlow(
        ConnectivityContext(
            ConnectivityContextVersion(0L),
            ProxyEndpointSnapshot(ProxyEndpointVersion(0L), emptyList()),
            networkMonitor.snapshots.value,
        ),
    )
    public val context: StateFlow<ConnectivityContext> = mutableContext.asStateFlow()

    init {
        scope.launch {
            combine(proxyRuntime.state, networkMonitor.snapshots, publishedEndpoints) { runtimeState, network, external ->
                val internal = (runtimeState as? ProxyRuntimeState.Running)?.handle?.endpoints?.endpoints.orEmpty()
                val endpoints = if (runtimeState is ProxyRuntimeState.Running) {
                    internal + external.entries.sortedBy { it.key.value }.map { it.value }
                } else {
                    emptyList()
                }
                endpoints to network
            }.collect { (endpointValues, network) ->
                val current = mutableContext.value
                if (current.proxyEndpoints.endpoints != endpointValues ||
                    current.network.addresses != network.addresses ||
                    current.network.defaultRouteAvailable != network.defaultRouteAvailable ||
                    current.network.vpnActive != network.vpnActive
                ) {
                    val endpoints = if (current.proxyEndpoints.endpoints == endpointValues) {
                        current.proxyEndpoints
                    } else {
                        ProxyEndpointSnapshot(
                            version = ProxyEndpointVersion(endpointVersion.incrementAndGet()),
                            endpoints = endpointValues,
                        )
                    }
                    mutableContext.value = ConnectivityContext(
                        version = ConnectivityContextVersion(version.incrementAndGet()),
                        proxyEndpoints = endpoints,
                        network = network,
                    )
                }
            }
        }
    }

    /** Publishes or withdraws one externally reachable endpoint without changing the internal proxy. */
    internal fun publishEndpoint(
        mechanismId: ConnectivityMechanismId,
        endpoint: ProxyEndpoint?,
    ) {
        publishedEndpoints.update { current ->
            if (endpoint == null) current - mechanismId else current + (mechanismId to endpoint)
        }
    }

    override fun close() {
        scope.cancel()
        networkMonitor.close()
    }
}
