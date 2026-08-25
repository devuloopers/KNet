package com.devuloopers.knet.ui.desktop.apistudio.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.application.port.apistudio.ApiStudioEditorId
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.components.progress.CircularProgress
import com.devuloopers.knet.ui.core.components.tabs.KNetTab
import com.devuloopers.knet.ui.core.components.tabs.KNetTabRow
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.dialog.CreateCollectionDialog
import com.devuloopers.knet.ui.desktop.apistudio.dialog.RenameCollectionDialog
import com.devuloopers.knet.ui.desktop.apistudio.dialog.RenameRequestDialog
import com.devuloopers.knet.ui.desktop.apistudio.dialog.SaveRequestDialog
import com.devuloopers.knet.ui.desktop.apistudio.editor.RequestUrlBar
import com.devuloopers.knet.ui.desktop.apistudio.layout.ApiStudioSplitWorkspace
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponseInspectorState
import com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext
import com.devuloopers.knet.ui.desktop.apistudio.protocol.ApiStudioWorkspaceContribution
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseInspectorActions
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseInspectorView
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.CollectionsSidebar
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.CollectionsSidebarActions
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.CollectionsViewModel
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanel
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanelActions
import kotlin.uuid.Uuid

/**
 * Renders API Studio from two explicit state owners without coordinating persistence in composition.
 *
 * [ApiStudioViewModel] owns the active document and execution, while [CollectionsViewModel] owns only
 * sidebar observation and collection CRUD. Selecting a row therefore emits one atomic load intent.
 *
 * @param viewModel Required active-document ViewModel supplied by product dependency injection.
 * @param collectionsViewModel Required sidebar ViewModel supplied by product dependency injection.
 * @param modifier Layout modifier applied to the complete workspace.
 */
@Composable
fun ApiStudioScreen(
    viewModel: ApiStudioViewModel,
    collectionsViewModel: CollectionsViewModel,
    protocolContributions: List<ApiStudioWorkspaceContribution> = emptyList(),
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    var responseSplitRatio by remember { mutableStateOf(0.5f) }
    val uiState by viewModel.uiState.collectAsState()
    val collectionsState by collectionsViewModel.uiState.collectAsState()
    var selectedEditorId by remember { mutableStateOf(ApiStudioEditorId.HTTP) }
    var selectedWorkspaceDocumentIds by remember {
        mutableStateOf<Map<ApiStudioEditorId, String>>(emptyMap())
    }
    var workspaceSaveItem by remember { mutableStateOf<SidebarRequestItem?>(null) }
    val selectedContribution = protocolContributions.firstOrNull {
        it.editorId == selectedEditorId
    }
    val allSidebarItems = collectionsState.unsavedRequests + collectionsState.collections.flatMap { it.requests }
    val selectedWorkspaceDocumentId = selectedWorkspaceDocumentIds[selectedEditorId]?.takeIf { selectedId ->
        allSidebarItems.any { it.id == selectedId && it.editorId == selectedEditorId }
    }
    val selectedWorkspaceItem = allSidebarItems.firstOrNull { it.id == selectedWorkspaceDocumentId }

    fun rememberWorkspaceSelection(editorId: ApiStudioEditorId, documentId: String?) {
        selectedWorkspaceDocumentIds = if (documentId == null) {
            selectedWorkspaceDocumentIds - editorId
        } else {
            selectedWorkspaceDocumentIds + (editorId to documentId)
        }
    }

    fun selectRequest(item: SidebarRequestItem) {
        if (item.workspaceDocument == null) {
            selectedEditorId = ApiStudioEditorId.HTTP
            viewModel.openRequest(item)
        } else {
            selectedEditorId = item.editorId
            rememberWorkspaceSelection(item.editorId, item.id)
        }
    }

    fun createProtocolDraft(contribution: ApiStudioWorkspaceContribution) {
        val document = contribution.createInitialDocument("doc_${Uuid.random()}")
        collectionsViewModel.createWorkspaceDraft(document) {
            selectedEditorId = contribution.editorId
            rememberWorkspaceSelection(contribution.editorId, document.id)
        }
    }

    fun openSaveFor(item: SidebarRequestItem) {
        selectRequest(item)
        if (item.workspaceDocument == null) {
            viewModel.openSaveDialog()
        } else {
            workspaceSaveItem = item
        }
    }

    Box(modifier = modifier.fillMaxSize().background(themeColors.surface)) {
        Row(modifier = Modifier.fillMaxSize().background(themeColors.surface)) {
            CollectionsSidebar(
                state = collectionsState,
                selectedRequestId = if (selectedEditorId == ApiStudioEditorId.HTTP) {
                    uiState.selectedRequestId
                } else {
                    selectedWorkspaceDocumentId
                },
                canSaveActiveRequest = if (selectedEditorId == ApiStudioEditorId.HTTP) {
                    uiState.sessionContext is SessionContext.UnsavedDraft
                } else {
                    selectedWorkspaceItem?.isUnsaved == true
                },
                actions = CollectionsSidebarActions(
                    onRequestSelected = ::selectRequest,
                    onSaveUnsavedRequest = ::openSaveFor,
                    onDeleteUnsavedRequest = { item ->
                        collectionsViewModel.deleteUnsavedRequest(item) {
                            if (item.workspaceDocument == null) {
                                viewModel.closeTab(item.id)
                            } else if (selectedWorkspaceDocumentIds[item.editorId] == item.id) {
                                rememberWorkspaceSelection(item.editorId, null)
                            }
                        }
                    },
                    onNewUnsavedSessionClicked = {
                        selectedContribution?.let(::createProtocolDraft) ?: viewModel.createNewDraft()
                    },
                    onRenameCollection = { item ->
                        collectionsViewModel.openRenameDialog(item.collectionId, item.name)
                    },
                    onDeleteCollection = { item ->
                        collectionsViewModel.deleteCollection(item.collectionId) {
                            if (item.requests.any { it.id == uiState.selectedRequestId }) {
                                uiState.selectedRequestId?.let(viewModel::closeTab)
                            }
                            item.requests.filter { it.workspaceDocument != null }.forEach { request ->
                                if (selectedWorkspaceDocumentIds[request.editorId] == request.id) {
                                    rememberWorkspaceSelection(request.editorId, null)
                                }
                            }
                        }
                    },
                    onRenameSavedRequest = collectionsViewModel::openRenameRequestDialog,
                    onDeleteSavedRequest = { item ->
                        collectionsViewModel.deleteSavedRequest(item) {
                            if (item.workspaceDocument == null) {
                                viewModel.closeTab(item.id)
                            } else if (selectedWorkspaceDocumentIds[item.editorId] == item.id) {
                                rememberWorkspaceSelection(item.editorId, null)
                            }
                        }
                    },
                    onSaveActiveRequest = {
                        if (selectedEditorId == ApiStudioEditorId.HTTP) {
                            viewModel.openSaveDialog()
                        } else {
                            selectedWorkspaceItem?.let { workspaceSaveItem = it }
                        }
                    },
                    onNewCollectionClicked = collectionsViewModel::openCreateCollectionDialog
                )
            )

            VerticalDivider(color = themeColors.border)

            Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (protocolContributions.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = KNetTheme.spacing.md),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        KNetTabRow {
                            KNetTab(
                                title = "HTTP",
                                selected = selectedEditorId == ApiStudioEditorId.HTTP,
                                onClick = { selectedEditorId = ApiStudioEditorId.HTTP },
                            )
                            protocolContributions.forEach { contribution ->
                                KNetTab(
                                    title = contribution.label,
                                    selected = selectedEditorId == contribution.editorId,
                                    onClick = {
                                        selectedEditorId = contribution.editorId
                                        val rememberedId = selectedWorkspaceDocumentIds[contribution.editorId]
                                        val existing = allSidebarItems.firstOrNull {
                                            it.editorId == contribution.editorId && it.id == rememberedId
                                        } ?: allSidebarItems.firstOrNull {
                                            it.editorId == contribution.editorId
                                        }
                                        rememberWorkspaceSelection(contribution.editorId, existing?.id)
                                    },
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = themeColors.border)
                }

                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    if (selectedContribution == null) {
                        ApiStudioSplitWorkspace(
                            authoringRatio = responseSplitRatio,
                            onAuthoringRatioChange = { responseSplitRatio = it },
                            authoringPane = { paneModifier ->
                                Column(
                                    modifier = paneModifier.fillMaxSize().background(themeColors.surface)
                                ) {
                                    RequestUrlBar(
                                        method = uiState.editorState.method,
                                        url = uiState.editorState.url,
                                        httpVersionPreference = uiState.editorState.httpVersionPreference,
                                        onMethodChanged = viewModel::updateMethod,
                                        onHttpVersionPreferenceChanged = viewModel::updateHttpVersionPreference,
                                        onUrlChanged = viewModel::updateUrl,
                                        onSendClicked = viewModel::executeRequest,
                                        onCancelClicked = viewModel::cancelExecution,
                                        isExecuting = uiState.executionState == ExecutionState.EXECUTING
                                    )

                                    uiState.persistenceErrorMessage?.let { message ->
                                        Text(
                                            text = message,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(themeColors.semantic.errorContainer)
                                                .padding(
                                                    horizontal = KNetTheme.spacing.md,
                                                    vertical = KNetTheme.spacing.xs,
                                                ),
                                            style = KNetTheme.typography.caption.copy(
                                                color = themeColors.semantic.error
                                            )
                                        )
                                    }

                                    RequestEditorPanel(
                                        bodyState = uiState.editorState.bodyState,
                                        queryParams = uiState.editorState.queryParams,
                                        headers = uiState.editorState.headers,
                                        cookies = uiState.editorState.cookies,
                                        authState = uiState.editorState.authState,
                                        preRequestScript = uiState.editorState.preRequestScript,
                                        testScript = uiState.editorState.testScript,
                                        activeSubTab = uiState.editorState.activeSubTab,
                                        activeScriptPhase = uiState.editorState.activeScriptPhase,
                                        scriptLanguage = uiState.editorState.scriptLanguage,
                                        actions = RequestEditorPanelActions(
                                            onBodyStateChanged = viewModel::updateBodyState,
                                            onGraphQlStateChanged = viewModel::updateGraphQlState,
                                            onQueryParamsChanged = viewModel::updateQueryParams,
                                            onHeadersChanged = viewModel::updateHeaders,
                                            onCookiesChanged = viewModel::updateCookies,
                                            onAuthStateChanged = viewModel::updateAuthState,
                                            onPreRequestScriptChanged = viewModel::updatePreRequestScript,
                                            onTestScriptChanged = viewModel::updateTestScript,
                                            onSubTabSelected = viewModel::updateActiveSubTab,
                                            onScriptPhaseSelected = viewModel::updateActiveScriptPhase,
                                            onScriptLanguageChanged = viewModel::updateScriptLanguage
                                        )
                                    )
                                }
                            },
                            resultPane = { paneModifier ->
                                val inspectorState = uiState.responseInspection?.copy(
                                    executionState = uiState.executionState,
                                    errorMessage = uiState.errorMessage
                                ) ?: ResponseInspectorState(
                                    executionState = uiState.executionState,
                                    errorMessage = uiState.errorMessage
                                )
                                ResponseInspectorView(
                                    state = inspectorState,
                                    actions = ResponseInspectorActions(
                                        onClearResponse = viewModel::clearResponse,
                                        onClearVisibleLiveRecords = viewModel::clearVisibleLiveRecords,
                                        onLiveRecordSelected = viewModel::selectLiveRecord,
                                    ),
                                    activeSubTab = uiState.editorState.activeResponseSubTab,
                                    onSubTabSelected = viewModel::updateActiveResponseSubTab,
                                    modifier = paneModifier
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        selectedContribution.Content(
                            documentId = selectedWorkspaceDocumentId,
                            onDocumentCreated = { documentId ->
                                rememberWorkspaceSelection(selectedContribution.editorId, documentId)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    if (selectedEditorId == ApiStudioEditorId.HTTP && uiState.isRestoring) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(themeColors.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgress(modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }

        if (uiState.isSaveDialogOpen) {
            SaveRequestDialog(
                defaultName = uiState.activeDocumentTitle,
                existingCollections = collectionsState.collections,
                onDismiss = viewModel::closeSaveDialog,
                onConfirm = viewModel::saveRequestToCollection
            )
        }

        workspaceSaveItem?.let { request ->
            SaveRequestDialog(
                defaultName = request.name,
                existingCollections = collectionsState.collections,
                onDismiss = { workspaceSaveItem = null },
                onConfirm = { requestName, mode, selectedFolder, newCollectionName ->
                    collectionsViewModel.promoteWorkspaceDocument(
                        request = request,
                        requestName = requestName,
                        mode = mode,
                        selectedFolder = selectedFolder,
                        newCollectionName = newCollectionName,
                        onSaved = { workspaceSaveItem = null },
                    )
                },
            )
        }

        if (collectionsState.isCreateCollectionDialogOpen) {
            CreateCollectionDialog(
                onDismiss = collectionsViewModel::closeCreateCollectionDialog,
                onConfirm = collectionsViewModel::createCollection
            )
        }

        if (collectionsState.isRenameDialogOpen) {
            collectionsState.renamingCollectionId?.let { collectionId ->
                RenameCollectionDialog(
                    currentName = collectionsState.renamingCollectionName,
                    onDismiss = collectionsViewModel::closeRenameDialog,
                    onConfirm = { newName ->
                        collectionsViewModel.renameCollection(collectionId, newName)
                    }
                )
            }
        }

        if (collectionsState.isRenameRequestDialogOpen) {
            collectionsState.renamingRequestItem?.let { request ->
                RenameRequestDialog(
                    currentName = request.name,
                    onDismiss = collectionsViewModel::closeRenameRequestDialog,
                    onConfirm = { newName ->
                        collectionsViewModel.renameSavedRequest(request, newName) {
                            if (request.workspaceDocument == null) {
                                viewModel.renameActiveDocument(request.id, newName)
                            }
                        }
                    }
                )
            }
        }
    }
}
