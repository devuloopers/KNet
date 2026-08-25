package com.devuloopers.knet.ui.desktop.apistudio.websocket.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.port.apistudio.*
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.port.traffic.CaptureSessionState
import com.devuloopers.knet.application.usecase.apistudio.*
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficCaptureStateUseCase
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.websocket.model.WebSocketStudioMessageKind
import com.devuloopers.knet.ui.desktop.apistudio.websocket.model.WebSocketStudioState
import com.devuloopers.knet.ui.desktop.apistudio.websocket.persistence.WebSocketWorkspaceDraftCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.uuid.Uuid

/** State owner for an independently contributed WebSocket API Studio workspace. */
class WebSocketStudioViewModel(
    private val getWorkspaceDocument: GetApiStudioWorkspaceDocumentUseCase,
    private val createWorkspaceDocument: CreateApiStudioWorkspaceDocumentUseCase,
    private val updateWorkspaceContent: UpdateApiStudioWorkspaceContentUseCase,
    private val createProtocolDocument: CreateApiStudioProtocolDocumentUseCase,
    private val openSession: OpenApiStudioProtocolSessionUseCase,
    observeProxyRuntimeState: ObserveProxyRuntimeStateUseCase,
    observeTrafficCaptureState: ObserveTrafficCaptureStateUseCase,
    private val draftCodec: WebSocketWorkspaceDraftCodec,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WebSocketStudioState(documentId = "", isDirty = false))

    /** Current immutable editor and live-session presentation state. */
    val state: StateFlow<WebSocketStudioState> = mutableState.asStateFlow()

    private val mutableMaterializedDocumentIds = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** IDs created when the first meaningful edit materializes a transient workspace document. */
    val materializedDocumentIds: SharedFlow<String> = mutableMaterializedDocumentIds.asSharedFlow()

    private val proxyState = observeProxyRuntimeState.execute()
    private val captureState = observeTrafficCaptureState.execute()
    private var activeSession: ApiStudioProtocolExecutionSession? = null
    private var sessionJob: Job? = null
    private var documentLoadJob: Job? = null
    private var materializationJob: Job? = null
    private var autoSaveJob: Job? = null
    private var draftRevision: Long = 0L

    /** Opens the selected common workspace document or a transient blank editor. */
    fun openWorkspaceDocument(documentId: String?) {
        val current = mutableState.value
        if (documentId != null && documentId == current.documentId) return
        cancelSession()
        autoSaveJob?.cancel()
        documentLoadJob?.cancel()
        draftRevision++
        documentLoadJob = viewModelScope.launch {
            try {
                materializationJob?.join()
                val outgoing = mutableState.value.takeIf { it.documentId.isNotBlank() && it.isDirty }
                outgoing?.let { draft ->
                    withContext(ioDispatcher) {
                        updateWorkspaceContent.execute(draft.documentId, draftCodec.content(draft))
                    }
                }
                if (documentId == null) {
                    mutableState.value = WebSocketStudioState(documentId = "", isDirty = false)
                    return@launch
                }
                val document = requireNotNull(withContext(ioDispatcher) {
                    getWorkspaceDocument.execute(documentId)
                }) { "The selected WebSocket request no longer exists." }
                require(document.editorId == ApiStudioEditorId.WEBSOCKET) {
                    "The selected request belongs to the '${document.editorId.value}' editor."
                }
                mutableState.value = draftCodec.decode(document)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.value = WebSocketStudioState(
                    documentId = documentId.orEmpty(),
                    isDirty = false,
                    errorMessage = error.message ?: "Unable to open the WebSocket request.",
                )
            }
        }
    }

    /** Updates the authored `ws` or `wss` endpoint and schedules persistence. */
    fun updateUrl(value: String) = updateDraft { it.copy(url = value) }

    /** Retains only numeric timeout input and schedules persistence. */
    fun updateTimeout(value: String) = updateDraft {
        it.copy(connectTimeoutMillis = value.filter(Char::isDigit))
    }

    /** Updates comma-separated handshake subprotocol preferences. */
    fun updateSubprotocols(value: String) = updateDraft { it.copy(subprotocols = value) }

    /** Appends one blank optional handshake-header row. */
    fun addHeader() = updateDraft {
        it.copy(headers = it.headers + ApiStudioProtocolMetadataEntry("", ""))
    }

    /** Updates the name and/or value of one handshake-header row when [index] is valid. */
    fun updateHeader(index: Int, name: String? = null, value: String? = null) = updateDraft { current ->
        current.copy(headers = current.headers.mapIndexed { entryIndex, header ->
            if (entryIndex != index) header else header.copy(
                name = name ?: header.name,
                value = value ?: header.value,
            )
        })
    }

    /** Removes the handshake-header row at [index], or leaves the draft unchanged when absent. */
    fun removeHeader(index: Int) = updateDraft { current ->
        current.copy(headers = current.headers.filterIndexed { entryIndex, _ -> entryIndex != index })
    }

    /** Selects how the outbound composer interprets its content. */
    fun selectMessageKind(kind: WebSocketStudioMessageKind) = updateDraft { it.copy(messageKind = kind) }

    /** Updates the current outbound message composer content. */
    fun updateMessageContent(value: String) = updateDraft { it.copy(messageContent = value) }

    /** Validates the current draft and opens a capture-aware interactive WebSocket session. */
    fun connect() {
        val snapshot = mutableState.value
        val document = createExecutionDocument(snapshot).getOrElse { error ->
            mutableState.update { it.copy(errorMessage = error.message ?: "The WebSocket draft is invalid.") }
            return
        }
        cancelSession()
        val session = openSession.execute(
            ApiStudioProtocolExecutionCommand(document, activeRoute()),
        ).getOrElse { error ->
            mutableState.update { it.copy(errorMessage = error.message ?: "Unable to open the WebSocket session.") }
            return
        }
        activeSession = session
        mutableState.update {
            it.copy(
                isConnecting = true,
                isConnected = false,
                events = emptyList(),
                selectedEventIndex = null,
                errorMessage = null,
            )
        }
        sessionJob = viewModelScope.launch {
            try {
                session.events.takeWhile { event ->
                    appendEvent(event)
                    event !is ApiStudioProtocolExecutionEvent.Completed &&
                            event !is ApiStudioProtocolExecutionEvent.Failed
                }.collect {}
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        isConnecting = false,
                        isConnected = false,
                        errorMessage = error.message ?: "The WebSocket session failed.",
                    )
                }
            } finally {
                if (activeSession === session) activeSession = null
                sessionJob = null
            }
        }
    }

    /** Sends the current composer content through the active session when connected. */
    fun sendMessage() {
        val session = activeSession ?: return
        val snapshot = mutableState.value
        val contentType = when (snapshot.messageKind) {
            WebSocketStudioMessageKind.TEXT -> "text/plain; charset=UTF-8"
            WebSocketStudioMessageKind.BINARY_BASE64 -> "application/octet-stream"
        }
        viewModelScope.launch {
            session.send(ApiStudioProtocolOutboundMessage(snapshot.messageContent, contentType))
                .onFailure { error ->
                    mutableState.update { it.copy(errorMessage = error.message ?: "Unable to send WebSocket message.") }
                }
        }
    }

    /** Requests a normal WebSocket close handshake without blocking the UI thread. */
    fun closeSession() {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.halfClose().onFailure { error ->
                mutableState.update { it.copy(errorMessage = error.message ?: "Unable to close WebSocket session.") }
            }
        }
    }

    /** Aborts the active session and resets transient connection state. */
    fun cancelSession() {
        activeSession?.cancel()
        activeSession = null
        sessionJob?.cancel()
        sessionJob = null
        mutableState.update { it.copy(isConnecting = false, isConnected = false) }
    }

    /** Selects a bounded timeline entry for detail presentation when [index] is valid. */
    fun selectEvent(index: Int) = mutableState.update { current ->
        current.copy(selectedEventIndex = index.takeIf { it in current.events.indices })
    }

    private fun appendEvent(event: ApiStudioProtocolExecutionEvent) {
        mutableState.update { current ->
            val events = (current.events + event).takeLast(MAXIMUM_VISIBLE_EVENTS)
            current.copy(
                events = events,
                selectedEventIndex = when (event) {
                    is ApiStudioProtocolExecutionEvent.Message -> events.lastIndex
                    else -> current.selectedEventIndex
                },
                isConnecting = event !is ApiStudioProtocolExecutionEvent.Started && current.isConnecting,
                isConnected = when (event) {
                    is ApiStudioProtocolExecutionEvent.Started,
                    is ApiStudioProtocolExecutionEvent.Message,
                        -> true

                    is ApiStudioProtocolExecutionEvent.Completed,
                    is ApiStudioProtocolExecutionEvent.Failed,
                        -> false
                },
                errorMessage = (event as? ApiStudioProtocolExecutionEvent.Failed)?.message,
            )
        }
    }

    private fun activeRoute(): ApiStudioProtocolRoute {
        if (captureState.value !is CaptureSessionState.Capturing) return ApiStudioProtocolRoute.Direct
        val port = (proxyState.value as? ProxyRuntimeState.Running)
            ?.handle?.endpoints?.endpoints?.firstOrNull()?.port
            ?: return ApiStudioProtocolRoute.Direct
        return ApiStudioProtocolRoute.LocalProxy(port = port)
    }

    private fun createExecutionDocument(state: WebSocketStudioState) = runCatching {
        val timeout = requireNotNull(
            state.connectTimeoutMillis.toLongOrDefaultIfBlank(DEFAULT_CONNECT_TIMEOUT_MILLIS),
        ) { "Enter a valid connect timeout." }
        createProtocolDocument.execute(
            RequestKindId.WEBSOCKET,
            ApiStudioProtocolDraft(
                id = state.documentId.ifBlank { "transient-websocket" },
                name = draftCodec.content(state).suggestedName,
                targetHost = "",
                targetPort = 0,
                useTls = state.url.trim().startsWith("wss://"),
                operationId = "",
                deadlineMillis = timeout,
                metadata = state.headers,
                outboundMessages = emptyList(),
                schemaSourceId = null,
                targetUri = state.url.trim(),
                requestedProtocols = state.subprotocols.split(',')
                    .map(String::trim)
                    .filter(String::isNotBlank)
                    .distinct(),
            ),
        ).getOrThrow()
    }

    private inline fun updateDraft(transform: (WebSocketStudioState) -> WebSocketStudioState) {
        val current = mutableState.value
        val transformed = transform(current)
        if (transformed == current) return
        val isTransient = current.documentId.isBlank()
        val documentId = current.documentId.ifBlank { "doc_${Uuid.random()}" }
        val updated = transformed.copy(documentId = documentId, isDirty = true, errorMessage = null)
        mutableState.value = updated
        draftRevision++
        if (isTransient) materializeTransientDraft(updated, draftRevision) else scheduleAutoSave()
    }

    private fun materializeTransientDraft(initialState: WebSocketStudioState, initialRevision: Long) {
        materializationJob?.cancel()
        materializationJob = viewModelScope.launch {
            val documentId = initialState.documentId
            var persistedRevision = initialRevision
            try {
                withContext(ioDispatcher) {
                    createWorkspaceDocument.execute(draftCodec.unsavedDocument(initialState))
                }
                while (mutableState.value.documentId == documentId && draftRevision != persistedRevision) {
                    val latestState = mutableState.value
                    val latestRevision = draftRevision
                    withContext(ioDispatcher) {
                        updateWorkspaceContent.execute(documentId, draftCodec.content(latestState))
                    }
                    persistedRevision = latestRevision
                }
                mutableMaterializedDocumentIds.emit(documentId)
                if (mutableState.value.documentId == documentId && draftRevision == persistedRevision) {
                    mutableState.update { it.copy(isDirty = false) }
                } else if (mutableState.value.documentId == documentId) {
                    scheduleAutoSave()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (mutableState.value.documentId == documentId) {
                    mutableState.update {
                        it.copy(
                            documentId = "",
                            isDirty = true,
                            errorMessage = error.message ?: "Unable to create the WebSocket request.",
                        )
                    }
                }
            } finally {
                materializationJob = null
            }
        }
    }

    private fun scheduleAutoSave() {
        val documentId = mutableState.value.documentId
        if (documentId.isBlank()) return
        val revision = draftRevision
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MILLIS)
            materializationJob?.join()
            val snapshot = mutableState.value
            if (snapshot.documentId != documentId) return@launch
            runCatching {
                withContext(ioDispatcher) {
                    updateWorkspaceContent.execute(documentId, draftCodec.content(snapshot))
                }
            }.onSuccess {
                if (revision == draftRevision && mutableState.value.documentId == documentId) {
                    mutableState.update { it.copy(isDirty = false) }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (revision == draftRevision && mutableState.value.documentId == documentId) {
                    mutableState.update {
                        it.copy(errorMessage = error.message ?: "Unable to auto-save the WebSocket request.")
                    }
                }
            }
        }
    }

    override fun onCleared() {
        activeSession?.cancel()
        super.onCleared()
    }

    private companion object {
        const val MAXIMUM_VISIBLE_EVENTS: Int = 1_000
        const val AUTO_SAVE_DEBOUNCE_MILLIS: Long = 300L
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS: Long = 30_000L
    }
}

private fun String.toLongOrDefaultIfBlank(defaultValue: Long): Long? =
    if (isBlank()) defaultValue else toLongOrNull()
