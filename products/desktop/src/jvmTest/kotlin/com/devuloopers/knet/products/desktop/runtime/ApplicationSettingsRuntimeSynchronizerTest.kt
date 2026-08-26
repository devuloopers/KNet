package com.devuloopers.knet.products.desktop.runtime

import com.devuloopers.knet.application.contract.breakpoint.BreakpointControl
import com.devuloopers.knet.application.contract.breakpoint.BreakpointDecision
import com.devuloopers.knet.application.contract.breakpoint.PendingBreakpoint
import com.devuloopers.knet.core.http.client.KNetApiClient
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.settings.model.ApplicationSettings
import com.devuloopers.knet.domain.settings.repository.ApplicationSettingsRepository
import com.devuloopers.knet.domain.settings.usecase.ObserveApplicationSettingsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Verifies product-owned propagation from persisted settings into runtime collaborators. */
@OptIn(ExperimentalCoroutinesApi::class)
class ApplicationSettingsRuntimeSynchronizerTest {

    @Test
    fun `timeout changes propagate without persistence owning runtime side effects`() = runTest {
        val settings = MutableStateFlow(ApplicationSettings())
        val repository = object : ApplicationSettingsRepository {
            override val settings: Flow<ApplicationSettings> = settings

            override suspend fun update(transform: (ApplicationSettings) -> ApplicationSettings) {
                settings.value = transform(settings.value)
            }
        }
        val apiClient = KNetApiClient()
        val breakpointControl = RecordingBreakpointControl()
        val synchronizer = ApplicationSettingsRuntimeSynchronizer(
            ObserveApplicationSettingsUseCase(repository),
            apiClient,
            breakpointControl,
        )

        val job = synchronizer.start(this)
        advanceUntilIdle()

        assertEquals(60.seconds.inWholeMilliseconds, apiClient.getConfiguration().timeoutMillis)
        assertEquals(60.seconds.inWholeMilliseconds, breakpointControl.timeoutMillis)

        settings.value = settings.value.copy(
            apiStudioTimeout = 5.minutes,
            liveInterceptionTimeout = 2.minutes,
        )
        advanceUntilIdle()

        assertEquals(5.minutes.inWholeMilliseconds, apiClient.getConfiguration().timeoutMillis)
        assertEquals(2.minutes.inWholeMilliseconds, breakpointControl.timeoutMillis)

        job.cancel()
        apiClient.close()
    }
}

private class RecordingBreakpointControl : BreakpointControl {
    override val pendingBreakpoints = MutableStateFlow<List<PendingBreakpoint>>(emptyList())
    override val isEnabled = MutableStateFlow(true)
    var timeoutMillis: Long? = null

    override fun replaceRules(rules: List<BreakpointRule>) = Unit
    override suspend fun setEnabled(enabled: Boolean) {
        isEnabled.value = enabled
    }

    override fun setDecisionTimeoutMillis(timeoutMillis: Long) {
        this.timeoutMillis = timeoutMillis
    }

    override suspend fun resolve(pendingId: String, decision: BreakpointDecision): Boolean = false
    override suspend fun dropMatching(url: String, method: String): Int = 0
    override suspend fun clear(): Int = 0
}
