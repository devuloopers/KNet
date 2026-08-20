package com.devuloopers.knet.products.desktop.runtime

import com.devuloopers.knet.application.port.breakpoint.BreakpointControlPort
import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.domain.settings.usecase.ObserveApplicationSettingsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Applies persisted timeout settings to process-owned runtime collaborators exactly once per distinct value.
 *
 * Persistence remains side-effect free; the desktop product composition root owns this synchronization because
 * it is the only layer allowed to coordinate concrete runtime implementations.
 *
 * @param observeSettings Observes validated process-level application settings.
 * @param apiClient Runtime API Studio HTTP client configuration target.
 * @param breakpointControl Runtime live-interception deadline target.
 */
internal class ApplicationSettingsRuntimeSynchronizer(
    private val observeSettings: ObserveApplicationSettingsUseCase,
    private val apiClient: KNetApiClient,
    private val breakpointControl: BreakpointControlPort,
) {
    /**
     * Starts synchronization in [scope]. Cancelling the scope terminates observation.
     *
     * @param scope Process-owned lifecycle scope.
     * @return Child job running the settings observation.
     */
    fun start(scope: CoroutineScope): Job = observeSettings.execute()
        .map { settings ->
            RuntimeTimeoutSettings(
                apiStudioTimeoutMillis = settings.apiStudioTimeout.inWholeMilliseconds,
                liveInterceptionTimeoutMillis = settings.liveInterceptionTimeout.inWholeMilliseconds,
            )
        }
        .distinctUntilChanged()
        .onEach { timeouts ->
            apiClient.updateTimeoutMillis(timeouts.apiStudioTimeoutMillis)
            breakpointControl.setDecisionTimeoutMillis(timeouts.liveInterceptionTimeoutMillis)
        }
        .catch { failure ->
            KNetLogger.error("ApplicationSettingsRuntime", failure) {
                "Application settings runtime synchronization stopped."
            }
        }
        .launchIn(scope)

    /** Runtime-only timeout projection used for distinct change detection. */
    private data class RuntimeTimeoutSettings(
        val apiStudioTimeoutMillis: Long,
        val liveInterceptionTimeoutMillis: Long,
    )
}
