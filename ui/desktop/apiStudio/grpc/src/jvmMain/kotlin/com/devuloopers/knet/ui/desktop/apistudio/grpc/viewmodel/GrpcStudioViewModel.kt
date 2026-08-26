package com.devuloopers.knet.ui.desktop.apistudio.grpc.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devuloopers.knet.application.contract.apistudio.ApiStudioEditorId
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolDraft
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolAuthoredMessage
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolDocument
import com.devuloopers.knet.application.contract.apistudio.ApiStudioOperationShape
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionCommand
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionEvent
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolExecutionSession
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolMetadataEntry
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolOperation
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolOutboundMessage
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolRoute
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolReflectionTarget
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolSchemaSource
import com.devuloopers.knet.application.contract.proxy.ProxyRuntimeState
import com.devuloopers.knet.application.contract.traffic.CaptureSessionState
import com.devuloopers.knet.application.usecase.apistudio.CreateApiStudioProtocolDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.CreateApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.ExecuteApiStudioProtocolDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.GetApiStudioWorkspaceDocumentUseCase
import com.devuloopers.knet.application.usecase.apistudio.ImportApiStudioProtocolSchemaUseCase
import com.devuloopers.knet.application.usecase.apistudio.LoadApiStudioProtocolSchemaUseCase
import com.devuloopers.knet.application.usecase.apistudio.ListApiStudioProtocolOperationsUseCase
import com.devuloopers.knet.application.usecase.apistudio.SaveApiStudioProtocolSchemaUseCase
import com.devuloopers.knet.application.usecase.apistudio.ReflectApiStudioProtocolSchemaUseCase
import com.devuloopers.knet.application.usecase.apistudio.OpenApiStudioProtocolSessionUseCase
import com.devuloopers.knet.application.usecase.apistudio.UpdateApiStudioWorkspaceContentUseCase
import com.devuloopers.knet.application.usecase.proxy.ObserveProxyRuntimeStateUseCase
import com.devuloopers.knet.application.usecase.traffic.ObserveTrafficCaptureStateUseCase
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.devuloopers.knet.ui.desktop.apistudio.grpc.model.GrpcStudioState
import com.devuloopers.knet.ui.desktop.apistudio.grpc.persistence.GrpcWorkspaceDraftCodec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.uuid.Uuid

class GrpcStudioViewModel(
    private val importSchema: ImportApiStudioProtocolSchemaUseCase,
    private val listOperations: ListApiStudioProtocolOperationsUseCase,
    private val createDocument: CreateApiStudioProtocolDocumentUseCase,
    private val executeDocument: ExecuteApiStudioProtocolDocumentUseCase,
    private val getWorkspaceDocument: GetApiStudioWorkspaceDocumentUseCase,
    private val createWorkspaceDocument: CreateApiStudioWorkspaceDocumentUseCase,
    private val updateWorkspaceContent: UpdateApiStudioWorkspaceContentUseCase,
    private val saveSchema: SaveApiStudioProtocolSchemaUseCase,
    private val loadSchema: LoadApiStudioProtocolSchemaUseCase,
    private val reflectSchema: ReflectApiStudioProtocolSchemaUseCase,
    private val openSession: OpenApiStudioProtocolSessionUseCase,
    observeProxyRuntimeState: ObserveProxyRuntimeStateUseCase,
    observeTrafficCaptureState: ObserveTrafficCaptureStateUseCase,
    private val draftCodec: GrpcWorkspaceDraftCodec,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableState = MutableStateFlow(GrpcStudioState(documentId = "", isDirty = false))
    val state: StateFlow<GrpcStudioState> = mutableState.asStateFlow()
    private val mutableMaterializedDocumentIds = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val materializedDocumentIds: SharedFlow<String> = mutableMaterializedDocumentIds.asSharedFlow()

    private val proxyState = observeProxyRuntimeState.execute()
    private val captureState = observeTrafficCaptureState.execute()
    private var executionJob: Job? = null
    private var activeSession: ApiStudioProtocolExecutionSession? = null
    private var documentLoadJob: Job? = null
    private var materializationJob: Job? = null
    private var autoSaveJob: Job? = null
    private var draftRevision: Long = 0L

    /** Opens exactly the workspace document selected by the common API Studio sidebar. */
    fun openWorkspaceDocument(documentId: String?) {
        val current = mutableState.value
        if (documentId != null && documentId == current.documentId) return
        autoSaveJob?.cancel()
        documentLoadJob?.cancel()
        draftRevision++
        documentLoadJob = viewModelScope.launch {
            try {
                materializationJob?.join()
                val outgoingDraft = mutableState.value.takeIf {
                    it.documentId.isNotBlank() && it.isDirty
                }
                outgoingDraft?.let { draft ->
                    withContext(ioDispatcher) {
                        updateWorkspaceContent.execute(draft.documentId, draftCodec.content(draft))
                    }
                }
                if (documentId == null) {
                    mutableState.value = GrpcStudioState(documentId = "", isDirty = false)
                    return@launch
                }
                val document = requireNotNull(withContext(ioDispatcher) {
                    getWorkspaceDocument.execute(documentId)
                }) { "The selected gRPC request no longer exists." }
                require(document.editorId == ApiStudioEditorId.GRPC) {
                    "The selected request belongs to the '${document.editorId.value}' editor."
                }
                val draft = draftCodec.decode(document)
                draft.schemaSourceId?.let { sourceId ->
                    val source = requireNotNull(withContext(ioDispatcher) {
                        loadSchema.execute(RequestKindId.GRPC, sourceId)
                    }) { "The saved descriptor '$sourceId' is unavailable. Import it again." }
                    importSchema.execute(RequestKindId.GRPC, source.sourceId, source.copyPayload()).getOrThrow()
                }
                val operations = listOperations.execute(RequestKindId.GRPC)
                mutableState.value = GrpcStudioState(
                    documentId = document.id,
                    targetHost = draft.targetHost,
                    targetPort = draft.targetPort,
                    useTls = draft.useTls,
                    deadlineMillis = draft.deadlineMillis,
                    schemaSourceId = draft.schemaSourceId,
                    operations = operations,
                    selectedOperation = operations.firstOrNull { it.id == draft.selectedOperationId },
                    metadata = draft.metadata,
                    outboundMessages = draft.outboundMessages,
                    selectedOutboundIndex = draft.selectedOutboundIndex,
                    isDirty = false,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.value = GrpcStudioState(
                    documentId = documentId.orEmpty(),
                    isDirty = false,
                    errorMessage = error.message ?: "Unable to open the gRPC request.",
                )
            }
        }
    }

    fun updateTargetHost(value: String) = updateDraft { it.copy(targetHost = value) }
    fun updateTargetPort(value: String) = updateDraft {
        it.copy(targetPort = value.filter(Char::isDigit))
    }
    fun updateDeadline(value: String) = updateDraft {
        it.copy(deadlineMillis = value.filter(Char::isDigit))
    }
    fun toggleTls() = updateDraft { it.copy(useTls = !it.useTls) }

    fun selectOperation(operationId: String) = updateDraft { current ->
        current.copy(
            selectedOperation = current.operations.firstOrNull { it.id == operationId },
        )
    }

    fun updateOutboundMessage(value: String) = updateDraft { current ->
        current.copy(
            outboundMessages = current.outboundMessages.mapIndexed { index, existing ->
                if (index == current.selectedOutboundIndex) value else existing
            },
        )
    }

    fun addOutboundMessage() = updateDraft { current ->
        current.copy(
            outboundMessages = current.outboundMessages + "",
            selectedOutboundIndex = current.outboundMessages.size,
        )
    }

    fun removeSelectedOutboundMessage() = updateDraft { current ->
        if (current.outboundMessages.size == 1) {
            return@updateDraft current.copy(outboundMessages = listOf(""))
        }
        val messages = current.outboundMessages.filterIndexed { index, _ -> index != current.selectedOutboundIndex }
        current.copy(
            outboundMessages = messages,
            selectedOutboundIndex = current.selectedOutboundIndex.coerceAtMost(messages.lastIndex),
        )
    }

    fun selectOutboundMessage(index: Int) = mutableState.update { current ->
        current.copy(selectedOutboundIndex = index.coerceIn(current.outboundMessages.indices))
    }

    fun addMetadata() = updateDraft { current ->
        current.copy(
            metadata = current.metadata + ApiStudioProtocolMetadataEntry("", ""),
        )
    }

    fun updateMetadata(index: Int, name: String? = null, value: String? = null) = updateDraft { current ->
        current.copy(
            metadata = current.metadata.mapIndexed { entryIndex, entry ->
                if (entryIndex != index) entry else entry.copy(
                    name = name?.lowercase()?.filterNot(Char::isWhitespace) ?: entry.name,
                    value = value ?: entry.value,
                )
            },
        )
    }

    fun removeMetadata(index: Int) = updateDraft { current ->
        current.copy(
            metadata = current.metadata.filterIndexed { entryIndex, _ -> entryIndex != index },
        )
    }

    fun importDescriptor(path: String) {
        viewModelScope.launch {
            runCatching {
                val descriptorPath = Path.of(path)
                val bytes = withContext(ioDispatcher) { Files.readAllBytes(descriptorPath) }
                val sourceId = descriptorPath.fileName.toString()
                val summary = importSchema.execute(RequestKindId.GRPC, sourceId, bytes).getOrThrow()
                saveSchema.execute(ApiStudioProtocolSchemaSource(RequestKindId.GRPC, sourceId, bytes))
                summary
            }.onSuccess { summary ->
                val operations = listOperations.execute(RequestKindId.GRPC)
                updateDraft { current ->
                    current.copy(
                        schemaSourceId = summary.sourceId,
                        operations = operations,
                        selectedOperation = current.selectedOperation
                            ?.let { selected -> operations.firstOrNull { it.id == selected.id } }
                            ?: operations.firstOrNull(),
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                mutableState.update { it.copy(errorMessage = error.message ?: "Descriptor import failed.") }
            }
        }
    }

    fun reportDescriptorImportFailure(message: String) {
        mutableState.update { it.copy(errorMessage = message) }
    }

    fun reflect() {
        val snapshot = mutableState.value
        val port = snapshot.targetPort.toIntOrNull()
        val deadline = snapshot.deadlineMillis.toLongOrDefaultIfBlank(DEFAULT_DEADLINE_MILLIS)
        if (port == null || port !in 1..65_535 || deadline == null || snapshot.targetHost.isBlank()) {
            mutableState.update { it.copy(errorMessage = "Enter a valid target and deadline for reflection.") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isReflecting = true, errorMessage = null) }
            runCatching {
                val reflected = reflectSchema.execute(
                    RequestKindId.GRPC,
                    ApiStudioProtocolReflectionTarget(
                        host = snapshot.targetHost.trim(),
                        port = port,
                        useTls = snapshot.useTls,
                        deadlineMillis = deadline.coerceAtMost(MAXIMUM_REFLECTION_DEADLINE_MILLIS),
                        route = activeRoute(),
                    ),
                ).getOrThrow()
                saveSchema.execute(reflected.source)
                reflected to listOperations.execute(RequestKindId.GRPC)
            }.onSuccess { (reflected, operations) ->
                updateDraft { current ->
                    current.copy(
                        schemaSourceId = reflected.source.sourceId,
                        operations = operations,
                        selectedOperation = current.selectedOperation
                            ?.let { selected -> operations.firstOrNull { it.id == selected.id } }
                            ?: operations.firstOrNull(),
                        isReflecting = false,
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isReflecting = false,
                        errorMessage = error.message ?: "gRPC reflection failed.",
                    )
                }
            }
        }
    }

    fun execute() {
        val document = createCurrentDocument().getOrElse { error ->
            mutableState.update { it.copy(errorMessage = error.message ?: "The gRPC draft is invalid.") }
            return
        }
        executionJob?.cancel()
        activeSession?.cancel()
        val interactive = mutableState.value.selectedOperation?.shape == ApiStudioOperationShape.CLIENT_STREAMING ||
            mutableState.value.selectedOperation?.shape == ApiStudioOperationShape.BIDIRECTIONAL_STREAMING
        if (interactive) {
            openInteractiveSession(document)
            return
        }
        mutableState.update {
            it.copy(
                isExecuting = true,
                isInteractiveSession = false,
                isRequestHalfClosed = false,
                events = emptyList(),
                errorMessage = null,
            )
        }
        executionJob = viewModelScope.launch {
            try {
                executeDocument.execute(ApiStudioProtocolExecutionCommand(document, activeRoute())).collect { event ->
                    appendEvent(event)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.update { it.copy(errorMessage = error.message ?: "gRPC execution failed.") }
            } finally {
                mutableState.update {
                    it.copy(isExecuting = false, isInteractiveSession = false, isRequestHalfClosed = false)
                }
                executionJob = null
            }
        }
    }

    fun sendSelectedMessage() {
        val session = activeSession ?: return
        val message = mutableState.value.selectedOutboundMessage
        viewModelScope.launch {
            session.send(ApiStudioProtocolOutboundMessage(message)).onFailure { error ->
                mutableState.update { it.copy(errorMessage = error.message ?: "Unable to send gRPC message.") }
            }
        }
    }

    fun halfClose() {
        val session = activeSession ?: return
        viewModelScope.launch {
            session.halfClose()
                .onSuccess { mutableState.update { it.copy(isRequestHalfClosed = true) } }
                .onFailure { error ->
                    mutableState.update { it.copy(errorMessage = error.message ?: "Unable to half-close gRPC stream.") }
                }
        }
    }

    fun cancel() {
        activeSession?.cancel()
        activeSession = null
        executionJob?.cancel()
        executionJob = null
        mutableState.update {
            it.copy(isExecuting = false, isInteractiveSession = false, isRequestHalfClosed = false)
        }
    }

    fun selectEvent(index: Int) = mutableState.update { it.copy(selectedEventIndex = index) }

    private fun activeRoute(): ApiStudioProtocolRoute {
        if (captureState.value !is CaptureSessionState.Capturing) return ApiStudioProtocolRoute.Direct
        val port = (proxyState.value as? ProxyRuntimeState.Running)
            ?.handle?.endpoints?.endpoints?.firstOrNull()?.port
            ?: return ApiStudioProtocolRoute.Direct
        return ApiStudioProtocolRoute.LocalProxy(port = port)
    }

    private fun openInteractiveSession(document: ApiStudioProtocolDocument) {
        val session = openSession.execute(ApiStudioProtocolExecutionCommand(document, activeRoute())).getOrElse { error ->
            mutableState.update { it.copy(errorMessage = error.message ?: "Unable to open gRPC stream.") }
            return
        }
        activeSession = session
        mutableState.update {
            it.copy(
                isExecuting = true,
                isInteractiveSession = true,
                isRequestHalfClosed = false,
                events = emptyList(),
                errorMessage = null,
            )
        }
        executionJob = viewModelScope.launch {
            try {
                session.events.collect(::appendEvent)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                mutableState.update { it.copy(errorMessage = error.message ?: "gRPC stream failed.") }
            } finally {
                if (activeSession === session) activeSession = null
                mutableState.update {
                    it.copy(isExecuting = false, isInteractiveSession = false, isRequestHalfClosed = false)
                }
                executionJob = null
            }
        }
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
                errorMessage = (event as? ApiStudioProtocolExecutionEvent.Failed)?.message,
            )
        }
    }

    private fun createCurrentDocument() = runCatching {
        val snapshot = mutableState.value
        val operation = requireNotNull(snapshot.selectedOperation) { "Choose a gRPC method." }
        val port = requireNotNull(snapshot.targetPort.toIntOrNull()) { "Enter a valid target port." }
        require(port in 1..65_535) { "Enter a valid target port." }
        val deadline = requireNotNull(
            snapshot.deadlineMillis.toLongOrDefaultIfBlank(DEFAULT_DEADLINE_MILLIS),
        ) { "Enter a valid deadline." }
        createDocument.execute(
            RequestKindId.GRPC,
            ApiStudioProtocolDraft(
                id = snapshot.documentId,
                name = operation.displayName,
                targetHost = snapshot.targetHost.trim(),
                targetPort = port,
                useTls = snapshot.useTls,
                operationId = operation.id,
                deadlineMillis = deadline,
                metadata = snapshot.authoredMetadata,
                outboundMessages = snapshot.outboundMessages.map { message ->
                    ApiStudioProtocolAuthoredMessage(
                        content = message,
                        contentType = "application/json",
                    )
                },
                schemaSourceId = snapshot.schemaSourceId,
            ),
        ).getOrThrow()
    }

    /** Marks an authoring mutation and schedules one bounded, latest-wins draft write. */
    private inline fun updateDraft(transform: (GrpcStudioState) -> GrpcStudioState) {
        val current = mutableState.value
        val transformed = transform(current)
        if (transformed == current) return

        val isTransient = current.documentId.isBlank()
        val documentId = current.documentId.ifBlank { "doc_${Uuid.random()}" }
        val updated = transformed.copy(documentId = documentId, isDirty = true)
        mutableState.value = updated
        draftRevision++

        if (isTransient) {
            materializeTransientDraft(updated, draftRevision)
        } else {
            scheduleAutoSave()
        }
    }

    /**
     * Persists the first meaningful edit without blocking the editor or losing edits made while Room is writing.
     * Navigation and focus never call this boundary, so simply opening gRPC remains storage-free.
     */
    private fun materializeTransientDraft(initialState: GrpcStudioState, initialRevision: Long) {
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
                autoSaveJob?.cancel()
                if (mutableState.value.documentId == documentId) {
                    mutableState.update {
                        it.copy(
                            documentId = "",
                            isDirty = true,
                            errorMessage = error.message ?: "Unable to create the gRPC request.",
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
                        it.copy(errorMessage = error.message ?: "Unable to auto-save the gRPC request.")
                    }
                }
            }
        }
    }

    private companion object {
        const val MAXIMUM_VISIBLE_EVENTS = 1_000
        const val DEFAULT_DEADLINE_MILLIS = 30_000L
        const val MAXIMUM_REFLECTION_DEADLINE_MILLIS = 120_000L
        const val AUTO_SAVE_DEBOUNCE_MILLIS = 300L
    }
}

private fun String.toLongOrDefaultIfBlank(defaultValue: Long): Long? =
    if (isBlank()) defaultValue else toLongOrNull()
