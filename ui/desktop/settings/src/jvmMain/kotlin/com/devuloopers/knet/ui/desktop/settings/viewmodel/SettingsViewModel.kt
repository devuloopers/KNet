package com.devuloopers.knet.ui.desktop.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.certificate.CertificateManagement
import com.devuloopers.knet.application.contract.certificate.TrustInstallationResult
import com.devuloopers.knet.core.logger.KNetLogger
import com.devuloopers.knet.core.logger.LogTags
import com.devuloopers.knet.domain.settings.model.ApplicationSettings
import com.devuloopers.knet.domain.settings.model.ProxyPort
import com.devuloopers.knet.domain.settings.usecase.ObserveApplicationSettingsUseCase
import com.devuloopers.knet.domain.settings.usecase.UpdateApplicationSettingsUseCase
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.ui.desktop.settings.model.SettingsField
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsNotice
import com.devuloopers.knet.ui.desktop.settings.model.SettingsNoticeTone
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState
import com.devuloopers.knet.ui.desktop.settings.model.TimeoutUnit
import com.devuloopers.knet.ui.desktop.settings.platform.SettingsPlatformActions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Owns validated Settings drafts and coordinates application-setting use cases.
 *
 * Numeric fields remain local drafts until an explicit commit, preventing intermediate keystrokes from changing
 * the running proxy or timeout configuration. Persistence is atomic and success is reported only after completion.
 *
 * @param observeApplicationSettings Observes validated process-level application preferences.
 * @param updateApplicationSettings Atomically updates process-level application preferences.
 * @param certificateManager Application boundary for root-certificate trust operations.
 * @param platformActions Desktop shell actions kept outside presentation state management.
 * @param ioDispatcher Dispatcher used for persistence and platform operations.
 */
class SettingsViewModel(
    private val observeApplicationSettings: ObserveApplicationSettingsUseCase,
    private val updateApplicationSettings: UpdateApplicationSettingsUseCase,
    private val certificateManager: CertificateManagement,
    private val platformActions: SettingsPlatformActions,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        SettingsState(dataDirectory = platformActions.dataDirectory),
    )

    /** Immutable Settings state consumed by Compose. */
    val uiState: StateFlow<SettingsState> = mutableUiState.asStateFlow()

    private val certificateOperationMutex = Mutex()
    private val persistedSettings = MutableStateFlow(ApplicationSettings())

    init {
        observeSettings()
        refreshCertificateTrust()
    }

    /** Applies one user interaction without exposing persistence or platform collaborators to Compose. */
    fun processIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectTab -> mutableUiState.update { it.copy(activeTab = intent.tab) }
            is SettingsIntent.UpdateProxyPort -> updateProxyPortDraft(intent.port)
            SettingsIntent.CommitProxyPort -> commitProxyPort()
            is SettingsIntent.ToggleAutoClearTraffic -> updateAutoClearTraffic(intent.enabled)
            is SettingsIntent.SetScriptLanguage -> updateScriptLanguage(intent.language)
            is SettingsIntent.UpdateApiStudioTimeout -> updateApiStudioTimeoutDraft(intent.value, intent.unit)
            SettingsIntent.CommitApiStudioTimeout -> commitApiStudioTimeout()
            is SettingsIntent.UpdateLiveInterceptionTimeout ->
                updateLiveInterceptionTimeoutDraft(intent.value, intent.unit)
            SettingsIntent.CommitLiveInterceptionTimeout -> commitLiveInterceptionTimeout()
            SettingsIntent.InstallRootCa -> installRootCertificate()
            SettingsIntent.OpenDataDirectory -> openDataDirectory()
            SettingsIntent.RequestResetDefaults -> mutableUiState.update {
                it.copy(isResetConfirmationVisible = true)
            }
            SettingsIntent.CancelResetDefaults -> mutableUiState.update {
                it.copy(isResetConfirmationVisible = false)
            }
            SettingsIntent.ConfirmResetDefaults -> resetDefaults()
            SettingsIntent.DismissNotice -> mutableUiState.update { it.copy(notice = null) }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch(ioDispatcher) {
            observeApplicationSettings.execute()
                .catch { failure ->
                    mutableUiState.update {
                        it.copy(
                            isLoading = false,
                            notice = SettingsNotice(
                                summary = "Settings could not be loaded.",
                                tone = SettingsNoticeTone.ERROR,
                                details = failure.message,
                            ),
                        )
                    }
                }
                .collect { settings ->
                    persistedSettings.value = settings
                    mutableUiState.update { current -> current.mergePersisted(settings) }
                }
        }
    }

    private fun refreshCertificateTrust() {
        viewModelScope.launch(ioDispatcher) {
            val trusted = try {
                certificateManager.isRootCertificateTrusted()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                KNetLogger.warn(LogTags.CERTIFICATE) {
                    "Root certificate trust status could not be read: ${failure.message}"
                }
                false
            }
            mutableUiState.update { it.copy(isCaTrusted = trusted) }
        }
    }

    private fun updateProxyPortDraft(value: String) {
        val filtered = value.filter(Char::isDigit).take(5)
        mutableUiState.update {
            it.copy(
                proxyPort = filtered,
                proxyPortError = validateProxyPort(filtered),
                dirtyFields = it.dirtyFields + SettingsField.PROXY_PORT,
                notice = null,
            )
        }
    }

    private fun commitProxyPort() {
        val state = mutableUiState.value
        val port = state.proxyPort.toIntOrNull()
        val error = validateProxyPort(state.proxyPort)
        if (port == null || error != null) {
            mutableUiState.update { it.copy(proxyPortError = error ?: "Proxy port is required.") }
            return
        }
        persistField(
            field = SettingsField.PROXY_PORT,
            successMessage = "Proxy port saved.",
        ) { settings -> settings.copy(proxyPort = ProxyPort(port)) }
    }

    private fun updateAutoClearTraffic(enabled: Boolean) {
        if (SettingsField.AUTO_CLEAR_TRAFFIC in mutableUiState.value.savingFields) return
        mutableUiState.update {
            it.copy(
                autoClearTrafficOnStartup = enabled,
                dirtyFields = it.dirtyFields + SettingsField.AUTO_CLEAR_TRAFFIC,
                notice = null,
            )
        }
        persistField(
            field = SettingsField.AUTO_CLEAR_TRAFFIC,
            successMessage = "Startup traffic policy saved.",
            revertOnFailure = true,
        ) { settings -> settings.copy(autoClearTrafficOnStartup = enabled) }
    }

    private fun updateScriptLanguage(language: ScriptLanguage) {
        if (SettingsField.SCRIPT_LANGUAGE in mutableUiState.value.savingFields) return
        mutableUiState.update {
            it.copy(
                scriptLanguage = language,
                dirtyFields = it.dirtyFields + SettingsField.SCRIPT_LANGUAGE,
                notice = null,
            )
        }
        persistField(
            field = SettingsField.SCRIPT_LANGUAGE,
            successMessage = "Default scripting language saved.",
            revertOnFailure = true,
        ) { settings -> settings.copy(defaultScriptLanguage = language) }
    }

    private fun updateApiStudioTimeoutDraft(value: String, unit: TimeoutUnit) {
        val filtered = value.filter(Char::isDigit).take(4)
        mutableUiState.update {
            it.copy(
                apiStudioTimeoutValue = filtered,
                apiStudioTimeoutUnit = unit,
                apiStudioTimeoutError = validateTimeout(filtered, unit),
                dirtyFields = it.dirtyFields + SettingsField.API_STUDIO_TIMEOUT,
                notice = null,
            )
        }
    }

    private fun commitApiStudioTimeout() {
        val state = mutableUiState.value
        val value = state.apiStudioTimeoutValue.toIntOrNull()
        val error = validateTimeout(state.apiStudioTimeoutValue, state.apiStudioTimeoutUnit)
        if (value == null || error != null) {
            mutableUiState.update { it.copy(apiStudioTimeoutError = error ?: "Timeout is required.") }
            return
        }
        val duration = state.apiStudioTimeoutUnit.toDuration(value)
        persistField(
            field = SettingsField.API_STUDIO_TIMEOUT,
            successMessage = "API Studio timeout saved.",
        ) { settings -> settings.copy(apiStudioTimeout = duration) }
    }

    private fun updateLiveInterceptionTimeoutDraft(value: String, unit: TimeoutUnit) {
        val filtered = value.filter(Char::isDigit).take(4)
        mutableUiState.update {
            it.copy(
                liveInterceptionTimeoutValue = filtered,
                liveInterceptionTimeoutUnit = unit,
                liveInterceptionTimeoutError = validateTimeout(filtered, unit),
                dirtyFields = it.dirtyFields + SettingsField.LIVE_INTERCEPTION_TIMEOUT,
                notice = null,
            )
        }
    }

    private fun commitLiveInterceptionTimeout() {
        val state = mutableUiState.value
        val value = state.liveInterceptionTimeoutValue.toIntOrNull()
        val error = validateTimeout(state.liveInterceptionTimeoutValue, state.liveInterceptionTimeoutUnit)
        if (value == null || error != null) {
            mutableUiState.update { it.copy(liveInterceptionTimeoutError = error ?: "Timeout is required.") }
            return
        }
        val duration = state.liveInterceptionTimeoutUnit.toDuration(value)
        persistField(
            field = SettingsField.LIVE_INTERCEPTION_TIMEOUT,
            successMessage = "Live interception timeout saved.",
        ) { settings -> settings.copy(liveInterceptionTimeout = duration) }
    }

    private fun persistField(
        field: SettingsField,
        successMessage: String,
        revertOnFailure: Boolean = false,
        transform: (ApplicationSettings) -> ApplicationSettings,
    ) {
        val state = mutableUiState.value
        if (state.isLoading || field in state.savingFields) return
        mutableUiState.update {
            it.copy(savingFields = it.savingFields + field, notice = null)
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                updateApplicationSettings.execute(transform)
                mutableUiState.update {
                    it.copy(
                        dirtyFields = it.dirtyFields - field,
                        savingFields = it.savingFields - field,
                        notice = SettingsNotice(successMessage, SettingsNoticeTone.SUCCESS),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                mutableUiState.update { current ->
                    val restored = if (revertOnFailure) {
                        current.restoreField(field, persistedSettings.value)
                    } else {
                        current
                    }
                    restored.copy(
                        savingFields = restored.savingFields - field,
                        notice = SettingsNotice(
                            summary = "Setting could not be saved.",
                            tone = SettingsNoticeTone.ERROR,
                            details = failure.message,
                        ),
                    )
                }
            }
        }
    }

    private fun installRootCertificate() {
        if (!certificateOperationMutex.tryLock()) return
        mutableUiState.update { it.copy(isInstallingCa = true, notice = null) }
        viewModelScope.launch(ioDispatcher) {
            try {
                val result = certificateManager.installRootCertificate()
                val trusted = try {
                    certificateManager.isRootCertificateTrusted()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    result is TrustInstallationResult.Installed
                }
                mutableUiState.update {
                    it.copy(
                        isCaTrusted = trusted,
                        notice = result.toNotice(trusted),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                KNetLogger.error(LogTags.CERTIFICATE, failure) { "Trust store installation failed." }
                mutableUiState.update {
                    it.copy(
                        notice = SettingsNotice(
                            summary = "Root CA installation failed.",
                            tone = SettingsNoticeTone.ERROR,
                            details = failure.message,
                        ),
                    )
                }
            } finally {
                mutableUiState.update { it.copy(isInstallingCa = false) }
                certificateOperationMutex.unlock()
            }
        }
    }

    private fun openDataDirectory() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val opened = platformActions.openDataDirectory()
                mutableUiState.update {
                    it.copy(
                        notice = if (opened) null else SettingsNotice(
                            summary = "Data directory could not be opened.",
                            tone = SettingsNoticeTone.ERROR,
                            details = platformActions.dataDirectory,
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                mutableUiState.update {
                    it.copy(
                        notice = SettingsNotice(
                            summary = "Data directory could not be opened.",
                            tone = SettingsNoticeTone.ERROR,
                            details = failure.message,
                        ),
                    )
                }
            }
        }
    }

    private fun resetDefaults() {
        if (SettingsField.RESET_DEFAULTS in mutableUiState.value.savingFields) return
        mutableUiState.update {
            it.copy(
                isResetConfirmationVisible = false,
                savingFields = it.savingFields + SettingsField.RESET_DEFAULTS,
                notice = null,
            )
        }
        viewModelScope.launch(ioDispatcher) {
            try {
                val defaults = ApplicationSettings()
                updateApplicationSettings.execute { defaults }
                mutableUiState.update {
                    it.mergePersisted(defaults).copy(
                        dirtyFields = emptySet(),
                        savingFields = it.savingFields - SettingsField.RESET_DEFAULTS,
                        notice = SettingsNotice(
                            "Application settings reset to defaults.",
                            SettingsNoticeTone.SUCCESS,
                        ),
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                mutableUiState.update {
                    it.copy(
                        savingFields = it.savingFields - SettingsField.RESET_DEFAULTS,
                        notice = SettingsNotice(
                            summary = "Default settings could not be restored.",
                            tone = SettingsNoticeTone.ERROR,
                            details = failure.message,
                        ),
                    )
                }
            }
        }
    }

    private fun validateProxyPort(value: String): String? {
        val port = value.toIntOrNull() ?: return "Proxy port is required."
        return if (port in ProxyPort.MINIMUM_VALUE..ProxyPort.MAXIMUM_VALUE) null
        else "Use a port between 1 and 65535."
    }

    private fun validateTimeout(value: String, unit: TimeoutUnit): String? {
        val magnitude = value.toIntOrNull() ?: return "Timeout is required."
        val duration = unit.toDuration(magnitude)
        return if (duration in ApplicationSettings.MINIMUM_TIMEOUT..ApplicationSettings.MAXIMUM_TIMEOUT) null
        else "Use a timeout between 1 second and 60 minutes."
    }

    private fun SettingsState.mergePersisted(settings: ApplicationSettings): SettingsState {
        val (apiValue, apiUnit) = TimeoutUnit.fromDuration(settings.apiStudioTimeout)
        val (liveValue, liveUnit) = TimeoutUnit.fromDuration(settings.liveInterceptionTimeout)
        return copy(
            isLoading = false,
            proxyPort = if (SettingsField.PROXY_PORT in dirtyFields) proxyPort else settings.proxyPort.value.toString(),
            proxyPortError = if (SettingsField.PROXY_PORT in dirtyFields) proxyPortError else null,
            autoClearTrafficOnStartup = if (SettingsField.AUTO_CLEAR_TRAFFIC in dirtyFields) {
                autoClearTrafficOnStartup
            } else {
                settings.autoClearTrafficOnStartup
            },
            scriptLanguage = if (SettingsField.SCRIPT_LANGUAGE in dirtyFields) {
                scriptLanguage
            } else {
                settings.defaultScriptLanguage
            },
            apiStudioTimeoutValue = if (SettingsField.API_STUDIO_TIMEOUT in dirtyFields) {
                apiStudioTimeoutValue
            } else {
                apiValue.toString()
            },
            apiStudioTimeoutUnit = if (SettingsField.API_STUDIO_TIMEOUT in dirtyFields) {
                apiStudioTimeoutUnit
            } else {
                apiUnit
            },
            apiStudioTimeoutError = if (SettingsField.API_STUDIO_TIMEOUT in dirtyFields) {
                apiStudioTimeoutError
            } else {
                null
            },
            liveInterceptionTimeoutValue = if (SettingsField.LIVE_INTERCEPTION_TIMEOUT in dirtyFields) {
                liveInterceptionTimeoutValue
            } else {
                liveValue.toString()
            },
            liveInterceptionTimeoutUnit = if (SettingsField.LIVE_INTERCEPTION_TIMEOUT in dirtyFields) {
                liveInterceptionTimeoutUnit
            } else {
                liveUnit
            },
            liveInterceptionTimeoutError = if (SettingsField.LIVE_INTERCEPTION_TIMEOUT in dirtyFields) {
                liveInterceptionTimeoutError
            } else {
                null
            },
        )
    }

    private fun SettingsState.restoreField(
        field: SettingsField,
        settings: ApplicationSettings,
    ): SettingsState = when (field) {
        SettingsField.AUTO_CLEAR_TRAFFIC -> copy(
            autoClearTrafficOnStartup = settings.autoClearTrafficOnStartup,
            dirtyFields = dirtyFields - field,
        )
        SettingsField.SCRIPT_LANGUAGE -> copy(
            scriptLanguage = settings.defaultScriptLanguage,
            dirtyFields = dirtyFields - field,
        )
        else -> this
    }

    private fun TrustInstallationResult.toNotice(trusted: Boolean): SettingsNotice = when {
        trusted -> SettingsNotice("Root CA registered in the OS trust store.", SettingsNoticeTone.SUCCESS)
        this is TrustInstallationResult.ManualActionRequired -> SettingsNotice(
            summary = message,
            tone = SettingsNoticeTone.WARNING,
            details = instructions,
        )
        this is TrustInstallationResult.Failed -> SettingsNotice(
            summary = message,
            tone = SettingsNoticeTone.ERROR,
        )
        else -> SettingsNotice(
            summary = "Root CA installation could not be verified.",
            tone = SettingsNoticeTone.WARNING,
        )
    }
}
