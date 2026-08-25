package com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.port.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolAuthoredMessage
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolDraft
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionCommand
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolExecutionSession
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolParameter
import com.devuloopers.knet.application.port.apistudio.ApiStudioProtocolRoute
import com.devuloopers.knet.application.port.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.port.traffic.CaptureSessionState
import com.devuloopers.knet.application.usecase.apistudio.CreateApiStudioProtocolDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.CreateApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.GetApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.OpenApiStudioProtocolSessionUseCase
import com.devuloopers.knet.application.usecase.apistudio.UpdateApiStudioWorkspaceContentUseCase
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficCaptureStateUseCase
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.model.GraphQLWebSocketAuthoringTab
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.model.GraphQLWebSocketStudioState
import com.devuloopers.knet.ui.desktop.apistudio.graphqlwebsocket.persistence.GraphQLWebSocketWorkspaceDraftCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

/** State owner for the contributed modern GraphQL WebSocket API Studio editor. */
class GraphQLWebSocketStudioViewModel(
    private val getWorkspaceDocument: GetApiStudioWorkspaceDocumentUseCase,
    private val createWorkspaceDocument: CreateApiStudioWorkspaceDocumentUseCase,
    private val updateWorkspaceContent: UpdateApiStudioWorkspaceContentUseCase,
    private val createProtocolDocument: CreateApiStudioProtocolDocumentUseCase,
    private val openSession: OpenApiStudioProtocolSessionUseCase,
    observeProxyRuntimeState: ObserveProxyRuntimeStateUseCase,
    observeTrafficCaptureState: ObserveTrafficCaptureStateUseCase,
    private val draftCodec: GraphQLWebSocketWorkspaceDraftCodec,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GraphQLWebSocketStudioState(documentId = "", isDirty = false))

    /** Current immutable authoring and execution state. */
    val state: StateFlow<GraphQLWebSocketStudioState> = mutableState.asStateFlow()

    private val mutableMaterializedDocumentIds = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** IDs emitted when the first meaningful edit materializes a transient document. */
    val materializedDocumentIds: SharedFlow<String> = mutableMaterializedDocumentIds.asSharedFlow()

    private val proxyState = observeProxyRuntimeState.execute()
    private val captureState = observeTrafficCaptureState.execute()
    private var activeSession: ApiStudioProtocolExecutionSession? = null
    private var sessionJob: Job? = null
    private var documentLoadJob: Job? = null
    private var materializationJob: Job? = null
    private var autoSaveJob: Job? = null
    private var draftRevision: Long = 0L

    /** Opens a durable document or restores a transient blank editor. */
    fun openWorkspaceDocument(documentId: String?) {
        if (documentId != null && documentId == mutableState.value.documentId) return
        cancelSession()
        autoSaveJob?.cancel()
        documentLoadJob?.cancel()
        draftRevision++
        documentLoadJob = viewModelScope.launch {
            try {
                materializationJob?.join()
                mutableState.value.takeIf { draft -> draft.documentId.isNotBlank() && draft.isDirty }?.let { draft ->
                    withContext(ioDispatcher) {
                        updateWorkspaceContent.execute(draft.documentId, draftCodec.content(draft))
                    }
                }
                if (documentId == null) {
                    mutableState.value = GraphQLWebSocketStudioState(documentId = "", isDirty = false)
                    return@launch
                }
                val document = requireNotNull(withContext(ioDispatcher) {
                    getWorkspaceDocument.execute(documentId)
                }) { "The selected GraphQL subscription no longer exists." }
                require(document.editorId == ApiStudioEditorId.GRAPHQL_WEBSOCKET) {
                    "The selected request belongs to the '${document.editorId.value}' editor."
                }
                mutableState.value = draftCodec.decode(document)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.value = GraphQLWebSocketStudioState(
                    documentId = documentId.orEmpty(),
                    isDirty = false,
                    errorMessage = error.message ?: "Unable to open the GraphQL subscription.",
                )
            }
        }
    }

    /** Updates the authored `ws` or `wss` endpoint. */
    fun updateUrl(value: String) = updateDraft { state -> state.copy(url = value) }

    /** Updates the connection timeout using numeric input only. */
    fun updateConnectTimeout(value: String) = updateDraft { state ->
        state.copy(connectTimeoutMillis = value.filter(Char::isDigit))
    }

    /** Updates the GraphQL acknowledgement timeout using numeric input only. */
    fun updateAcknowledgementTimeout(value: String) = updateDraft { state ->
        state.copy(acknowledgementTimeoutMillis = value.filter(Char::isDigit))
    }

    /** Updates the stable operation ID used to correlate subscription messages. */
    fun updateOperationId(value: String) = updateDraft { state -> state.copy(operationId = value) }

    /** Updates an optional explicit operation name. */
    fun updateOperationName(value: String) = updateDraft { state -> state.copy(operationName = value) }

    /** Updates the GraphQL document. */
    fun updateQuery(value: String) = updateDraft { state -> state.copy(query = value) }

    /** Updates variables JSON. */
    fun updateVariables(value: String) = updateDraft { state -> state.copy(variablesJson = value) }

    /** Updates extensions JSON. */
    fun updateExtensions(value: String) = updateDraft { state -> state.copy(extensionsJson = value) }

    /** Updates optional `connection_init.payload` JSON. */
    fun updateConnectionParameters(value: String) = updateDraft { state ->
        state.copy(connectionParametersJson = value)
    }

    /** Selects one authoring document without persisting a semantic content change. */
    fun selectAuthoringTab(tab: GraphQLWebSocketAuthoringTab) {
        mutableState.update { state -> state.copy(selectedAuthoringTab = tab) }
    }

    /** Adds one optional handshake-header row. */
    fun addHeader() = updateDraft { state ->
        state.copy(headers = state.headers + ApiStudioProtocolMetadataEntry("", ""))
    }

    /** Updates one handshake-header row when [index] exists. */
    fun updateHeader(index: Int, name: String? = null, value: String? = null) = updateDraft { state ->
        state.copy(headers = state.headers.mapIndexed { entryIndex, header ->
            if (entryIndex != index) header else header.copy(
                name = name ?: header.name,
                value = value ?: header.value,
            )
        })
    }

    /** Removes one handshake-header row when [index] exists. */
    fun removeHeader(index: Int) = updateDraft { state ->
        state.copy(headers = state.headers.filterIndexed { entryIndex, _ -> entryIndex != index })
    }

    /** Opens a capture-aware subscription session after strict engine validation. */
    fun connect() {
        val document = createExecutionDocument(mutableState.value).getOrElse { error ->
            mutableState.update { state ->
                state.copy(errorMessage = error.message ?: "The GraphQL subscription is invalid.")
            }
            return
        }
        cancelSession()
        val session = openSession.execute(ApiStudioProtocolExecutionCommand(document, activeRoute())).getOrElse { error ->
            mutableState.update { state ->
                state.copy(errorMessage = error.message ?: "Unable to open the GraphQL subscription.")
            }
            return
        }
        activeSession = session
        mutableState.update { state ->
            state.copy(
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
                mutableState.update { state ->
                    state.copy(
                        isConnecting = false,
                        isConnected = false,
                        errorMessage = error.message ?: "The GraphQL subscription failed.",
                    )
                }
            } finally {
                if (activeSession === session) activeSession = null
                sessionJob = null
            }
        }
    }

    /** Sends the GraphQL `complete` operation and performs a normal WebSocket close. */
    fun stopSession() {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.halfClose().onFailure { error ->
                mutableState.update { state ->
                    state.copy(errorMessage = error.message ?: "Unable to stop the GraphQL subscription.")
                }
            }
        }
    }

    /** Immediately cancels the active connection and clears transient connection state. */
    fun cancelSession() {
        activeSession?.cancel()
        activeSession = null
        sessionJob?.cancel()
        sessionJob = null
        mutableState.update { state -> state.copy(isConnecting = false, isConnected = false) }
    }

    /** Selects one bounded timeline event for detail presentation. */
    fun selectEvent(index: Int) = mutableState.update { state ->
        state.copy(selectedEventIndex = index.takeIf { value -> value in state.events.indices })
    }

    private fun createExecutionDocument(state: GraphQLWebSocketStudioState) = runCatching {
        val connectTimeout = state.connectTimeoutMillis.toLongOrNull() ?: DEFAULT_CONNECT_TIMEOUT_MILLIS
        createProtocolDocument.execute(
            RequestKindId.GRAPHQL_WEBSOCKET,
            ApiStudioProtocolDraft(
                id = state.documentId.ifBlank { "transient-graphql-websocket" },
                name = draftCodec.content(state).suggestedName,
                targetHost = "",
                targetPort = 0,
                useTls = state.url.trim().startsWith("wss://"),
                operationId = state.operationId.trim(),
                deadlineMillis = connectTimeout,
                metadata = state.headers,
                outboundMessages = listOf(ApiStudioProtocolAuthoredMessage(state.query, GRAPHQL_DOCUMENT_CONTENT_TYPE)),
                schemaSourceId = null,
                targetUri = state.url.trim(),
                parameters = listOf(
                    ApiStudioProtocolParameter(ACKNOWLEDGEMENT_TIMEOUT, state.acknowledgementTimeoutMillis),
                    ApiStudioProtocolParameter(CONNECTION_PARAMETERS, state.connectionParametersJson),
                    ApiStudioProtocolParameter(OPERATION_NAME, state.operationName),
                    ApiStudioProtocolParameter(VARIABLES, state.variablesJson),
                    ApiStudioProtocolParameter(EXTENSIONS, state.extensionsJson),
                ),
            ),
        ).getOrThrow()
    }

    private fun appendEvent(event: ApiStudioProtocolExecutionEvent) {
        mutableState.update { state ->
            val events = (state.events + event).takeLast(MAXIMUM_VISIBLE_EVENTS)
            state.copy(
                events = events,
                selectedEventIndex = if (event is ApiStudioProtocolExecutionEvent.Message) {
                    events.lastIndex
                } else {
                    state.selectedEventIndex
                },
                isConnecting = event !is ApiStudioProtocolExecutionEvent.Started && state.isConnecting,
                isConnected = when (event) {
                    is ApiStudioProtocolExecutionEvent.Started,
                    is ApiStudioProtocolExecutionEvent.Message -> true
                    is ApiStudioProtocolExecutionEvent.Completed,
                    is ApiStudioProtocolExecutionEvent.Failed -> false
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

    private inline fun updateDraft(transform: (GraphQLWebSocketStudioState) -> GraphQLWebSocketStudioState) {
        val current = mutableState.value
        val transformed = transform(current)
        if (transformed == current) return
        val wasTransient = current.documentId.isBlank()
        val documentId = current.documentId.ifBlank { "doc_${Uuid.random()}" }
        val updated = transformed.copy(documentId = documentId, isDirty = true, errorMessage = null)
        mutableState.value = updated
        draftRevision++
        if (wasTransient) materializeTransientDraft(updated, draftRevision) else scheduleAutoSave()
    }

    private fun materializeTransientDraft(initialState: GraphQLWebSocketStudioState, initialRevision: Long) {
        materializationJob?.cancel()
        materializationJob = viewModelScope.launch {
            val documentId = initialState.documentId
            var persistedRevision = initialRevision
            try {
                withContext(ioDispatcher) { createWorkspaceDocument.execute(draftCodec.unsavedDocument(initialState)) }
                while (mutableState.value.documentId == documentId && draftRevision != persistedRevision) {
                    val latest = mutableState.value
                    val latestRevision = draftRevision
                    withContext(ioDispatcher) {
                        updateWorkspaceContent.execute(documentId, draftCodec.content(latest))
                    }
                    persistedRevision = latestRevision
                }
                mutableMaterializedDocumentIds.emit(documentId)
                if (mutableState.value.documentId == documentId && draftRevision == persistedRevision) {
                    mutableState.update { state -> state.copy(isDirty = false) }
                } else if (mutableState.value.documentId == documentId) {
                    scheduleAutoSave()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (mutableState.value.documentId == documentId) {
                    mutableState.update { state ->
                        state.copy(
                            documentId = "",
                            isDirty = true,
                            errorMessage = error.message ?: "Unable to create the GraphQL subscription.",
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
                    mutableState.update { state -> state.copy(isDirty = false) }
                }
            }.onFailure { error ->
                if (error is CancellationException) throw error
                if (revision == draftRevision && mutableState.value.documentId == documentId) {
                    mutableState.update { state ->
                        state.copy(errorMessage = error.message ?: "Unable to save the GraphQL subscription.")
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
        const val GRAPHQL_DOCUMENT_CONTENT_TYPE: String = "application/graphql"
        const val ACKNOWLEDGEMENT_TIMEOUT: String = "acknowledgement-timeout-millis"
        const val CONNECTION_PARAMETERS: String = "connection-parameters-json"
        const val OPERATION_NAME: String = "operation-name"
        const val VARIABLES: String = "variables-json"
        const val EXTENSIONS: String = "extensions-json"
    }
}
