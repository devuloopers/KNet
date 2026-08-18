package com.devuloopers.knet.ui.desktop.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import com.devuloopers.knet.domain.workspace.model.TimeoutUnit
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.application.port.certificate.CertificateManagementPort
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState
import com.devuloopers.knet.ui.desktop.settings.platform.SettingsPlatformActions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel managing state and direct auto-save persistence for KNet Application Settings.
 *
 * Interacts with [WidgetPreferencesRepository] for DataStore settings persistence
 * and [CertificateManagementPort] for OS Root CA status and trust store installation.
 *
 * @param widgetPreferencesRepository Repository persisting user preferences to DataStore.
 * @param certificateManager Certificate management service for OS Root CA trust store registration.
 * @param platformActions Desktop shell actions kept outside presentation state management.
 * @param ioDispatcher Background coroutine dispatcher for asynchronous I/O operations.
 */
class SettingsViewModel(
    private val widgetPreferencesRepository: WidgetPreferencesRepository,
    private val certificateManager: CertificateManagementPort,
    private val platformActions: SettingsPlatformActions,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    private val defaultDataDir: String = platformActions.dataDirectory

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val savedSettings = withContext(ioDispatcher) {
                widgetPreferencesRepository.settingsFlow.firstOrNull() ?: WorkspaceLayoutSettings()
            }
            val isCaTrusted = withContext(ioDispatcher) {
                try {
                    certificateManager.isRootCertificateTrusted()
                } catch (_: Exception) {
                    false
                }
            }

            val (apiStudioVal, apiStudioUnit) = TimeoutUnit.fromSeconds(savedSettings.apiStudioTimeoutSeconds)
            val (liveVal, liveUnit) = TimeoutUnit.fromSeconds(savedSettings.liveInterceptionTimeoutSeconds)

            _uiState.update {
                it.copy(
                    proxyPort = savedSettings.proxyPort.toString(),
                    autoClearTrafficOnStartup = savedSettings.autoClearTrafficOnStartup,
                    theme = savedSettings.theme,
                    scriptLanguage = savedSettings.scriptLanguage,
                    maxPayloadMb = savedSettings.maxPayloadMb,
                    apiStudioTimeoutValue = apiStudioVal.toString(),
                    apiStudioTimeoutUnit = apiStudioUnit,
                    liveInterceptionTimeoutValue = liveVal.toString(),
                    liveInterceptionTimeoutUnit = liveUnit,
                    isCaTrusted = isCaTrusted,
                    dataDirectory = defaultDataDir
                )
            }
        }
    }

    /**
     * Processes user intents from the Settings UI with direct background auto-saving.
     *
     * @param intent User action intent to process.
     */
    fun processIntent(intent: SettingsIntent) {
        viewModelScope.launch {
            when (intent) {
                is SettingsIntent.SelectTab -> {
                    _uiState.update { it.copy(activeTab = intent.tab) }
                }

                is SettingsIntent.UpdateSearchQuery -> {
                    _uiState.update { it.copy(searchQuery = intent.query) }
                }

                is SettingsIntent.UpdateProxyPort -> {
                    val filteredPort = intent.port.filter { it.isDigit() }.take(5)
                    _uiState.update { it.copy(proxyPort = filteredPort, message = "Saved to preferences") }
                    autoSaveSettings(_uiState.value)
                }

                is SettingsIntent.ToggleAutoClearTraffic -> {
                    _uiState.update {
                        it.copy(
                            autoClearTrafficOnStartup = intent.enabled,
                            message = "Saved to preferences"
                        )
                    }
                    autoSaveSettings(_uiState.value)
                }

                is SettingsIntent.SetMaxPayloadMb -> {
                    _uiState.update { it.copy(maxPayloadMb = intent.mb, message = "Saved to preferences") }
                    autoSaveSettings(_uiState.value)
                }

                is SettingsIntent.SetTheme -> {
                    _uiState.update { it.copy(theme = intent.theme, message = "Saved to preferences") }
                    autoSaveSettings(_uiState.value)
                }

                is SettingsIntent.SetScriptLanguage -> {
                    _uiState.update { it.copy(scriptLanguage = intent.language, message = "Saved to preferences") }
                    autoSaveSettings(_uiState.value)
                }

                is SettingsIntent.UpdateApiStudioTimeout -> {
                    val filtered = intent.value.filter { it.isDigit() }.take(4)
                    _uiState.update {
                        it.copy(
                            apiStudioTimeoutValue = filtered,
                            apiStudioTimeoutUnit = intent.unit,
                            message = "Saved to preferences"
                        )
                    }
                    autoSaveSettings(_uiState.value)
                }

                is SettingsIntent.UpdateLiveInterceptionTimeout -> {
                    val filtered = intent.value.filter { it.isDigit() }.take(4)
                    _uiState.update {
                        it.copy(
                            liveInterceptionTimeoutValue = filtered,
                            liveInterceptionTimeoutUnit = intent.unit,
                            message = "Saved to preferences"
                        )
                    }
                    autoSaveSettings(_uiState.value)
                }

                SettingsIntent.InstallRootCa -> {
                    _uiState.update { it.copy(isInstallingCa = true) }
                    val success = withContext(ioDispatcher) {
                        try {
                            certificateManager.installRootCertificate()
                        } catch (e: Exception) {
                            KNetLogger.error(LogTags.CERTIFICATE, e) { "Trust store installation failed" }
                            false
                        }
                    }
                    val isTrustedNow = withContext(ioDispatcher) {
                        try {
                            certificateManager.isRootCertificateTrusted()
                        } catch (_: Exception) {
                            success
                        }
                    }
                    _uiState.update {
                        it.copy(
                            isInstallingCa = false,
                            isCaTrusted = isTrustedNow,
                            message = if (isTrustedNow) "Root CA registered in OS trust store." else "Trust store installation failed."
                        )
                    }
                }

                SettingsIntent.OpenDataDirectory -> {
                    val opened = withContext(ioDispatcher) { platformActions.openDataDirectory() }
                    if (!opened) {
                        KNetLogger.warn(LogTags.KNET) { "Failed to open directory '$defaultDataDir'." }
                    }
                }

                SettingsIntent.ResetDefaults -> {
                    val defaultSettings = WorkspaceLayoutSettings()
                    val (apiStudioVal, apiStudioUnit) = TimeoutUnit.fromSeconds(defaultSettings.apiStudioTimeoutSeconds)
                    val (liveVal, liveUnit) = TimeoutUnit.fromSeconds(defaultSettings.liveInterceptionTimeoutSeconds)
                    val newState = _uiState.value.copy(
                        proxyPort = defaultSettings.proxyPort.toString(),
                        autoClearTrafficOnStartup = defaultSettings.autoClearTrafficOnStartup,
                        theme = defaultSettings.theme,
                        scriptLanguage = defaultSettings.scriptLanguage,
                        maxPayloadMb = 10,
                        apiStudioTimeoutValue = apiStudioVal.toString(),
                        apiStudioTimeoutUnit = apiStudioUnit,
                        liveInterceptionTimeoutValue = liveVal.toString(),
                        liveInterceptionTimeoutUnit = liveUnit,
                        message = "Reset to default settings."
                    )
                    _uiState.value = newState
                    autoSaveSettings(newState)
                }
            }
        }
    }

    private suspend fun autoSaveSettings(state: SettingsState) {
        val portNumber = state.proxyPort.toIntOrNull() ?: 8080
        val apiStudioRaw = state.apiStudioTimeoutValue.toIntOrNull() ?: 60
        val apiStudioSeconds = state.apiStudioTimeoutUnit.toSeconds(apiStudioRaw)

        val liveRaw = state.liveInterceptionTimeoutValue.toIntOrNull() ?: 60
        val liveSeconds = state.liveInterceptionTimeoutUnit.toSeconds(liveRaw)

        withContext(ioDispatcher) {
            val currentSettings = widgetPreferencesRepository.settingsFlow.firstOrNull() ?: WorkspaceLayoutSettings()
            widgetPreferencesRepository.saveSettings(
                currentSettings.copy(
                    proxyPort = portNumber,
                    autoClearTrafficOnStartup = state.autoClearTrafficOnStartup,
                    theme = state.theme,
                    scriptLanguage = state.scriptLanguage,
                    maxPayloadMb = state.maxPayloadMb,
                    apiStudioTimeoutSeconds = apiStudioSeconds,
                    liveInterceptionTimeoutSeconds = liveSeconds
                )
            )
        }
    }
}
