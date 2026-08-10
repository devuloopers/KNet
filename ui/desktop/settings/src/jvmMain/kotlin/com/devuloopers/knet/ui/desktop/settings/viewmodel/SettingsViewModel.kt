package com.devuloopers.knet.ui.desktop.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import com.devuloopers.knet.domain.workspace.model.WorkspaceLayoutSettings
import com.devuloopers.knet.domain.workspace.repository.WidgetPreferencesRepository
import com.devuloopers.knet.engine.certificate.CertificateManager
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState
import com.devuloopers.knet.ui.desktop.settings.model.SettingsTab
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel managing state and direct auto-save persistence for KNet Application Settings.
 *
 * Interacts with [WidgetPreferencesRepository] for DataStore settings persistence
 * and [CertificateManager] for OS Root CA status and trust store installation.
 */
class SettingsViewModel(
    private val widgetPreferencesRepository: WidgetPreferencesRepository,
    private val certificateManager: CertificateManager,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsState())
    val uiState: StateFlow<SettingsState> = _uiState.asStateFlow()

    private val defaultDataDir: String = File(System.getProperty("user.home"), ".knet").absolutePath

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
                    certificateManager.isCaTrustedByOs()
                } catch (_: Exception) {
                    false
                }
            }

            _uiState.update {
                it.copy(
                    proxyPort = savedSettings.proxyPort.toString(),
                    autoClearTrafficOnStartup = savedSettings.autoClearTrafficOnStartup,
                    theme = savedSettings.theme,
                    scriptLanguage = savedSettings.scriptLanguage,
                    maxPayloadMb = savedSettings.maxPayloadMb,
                    isCaTrusted = isCaTrusted,
                    dataDirectory = defaultDataDir
                )
            }
        }
    }

    /**
     * Processes user intents from the Settings UI with direct background auto-saving.
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
                    _uiState.update { it.copy(autoClearTrafficOnStartup = intent.enabled, message = "Saved to preferences") }
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
                            certificateManager.isCaTrustedByOs()
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
                    withContext(ioDispatcher) {
                        try {
                            val dir = File(defaultDataDir).apply { mkdirs() }
                            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                                Desktop.getDesktop().open(dir)
                            }
                        } catch (e: Exception) {
                            KNetLogger.warn(LogTags.KNET) { "Failed to open directory '$defaultDataDir': ${e.message}" }
                        }
                    }
                }

                SettingsIntent.ResetDefaults -> {
                    val defaultSettings = WorkspaceLayoutSettings()
                    val newState = _uiState.value.copy(
                        proxyPort = defaultSettings.proxyPort.toString(),
                        autoClearTrafficOnStartup = defaultSettings.autoClearTrafficOnStartup,
                        theme = defaultSettings.theme,
                        scriptLanguage = defaultSettings.scriptLanguage,
                        maxPayloadMb = 10,
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
        withContext(ioDispatcher) {
            val currentSettings = widgetPreferencesRepository.settingsFlow.firstOrNull() ?: WorkspaceLayoutSettings()
            widgetPreferencesRepository.saveSettings(
                currentSettings.copy(
                    proxyPort = portNumber,
                    autoClearTrafficOnStartup = state.autoClearTrafficOnStartup,
                    theme = state.theme,
                    scriptLanguage = state.scriptLanguage,
                    maxPayloadMb = state.maxPayloadMb
                )
            )
        }
    }
}
