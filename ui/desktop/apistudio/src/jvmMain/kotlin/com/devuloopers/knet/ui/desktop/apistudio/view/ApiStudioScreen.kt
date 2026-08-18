package com.devuloopers.knet.ui.desktop.apistudio.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.components.split.HorizontalSplitPane
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.dialog.CreateCollectionDialog
import com.devuloopers.knet.ui.desktop.apistudio.dialog.RenameCollectionDialog
import com.devuloopers.knet.ui.desktop.apistudio.dialog.SaveRequestDialog
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanel
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanelActions
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.PayloadInspectionSpec
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState
import com.devuloopers.knet.ui.desktop.apistudio.editor.RequestUrlBar
import com.devuloopers.knet.ui.desktop.apistudio.model.ApiStudioState
import com.devuloopers.knet.ui.desktop.apistudio.model.CollectionsState
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseInspectorActions
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponseInspectorState
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseInspectorView
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.CollectionsSidebar
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.CollectionsViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Top-level API Studio Screen composable hosting Collections Sidebar, Request Editor, and Response Inspector.
 *
 * Fully integrated with decoupled ViewModels:
 * - [ApiStudioViewModel]: Request authoring, execution, response inspection.
 * - [CollectionsViewModel]: Persistent Room DB collections, sidebar tree, and Save Request dialog.
 *
 * @param viewModel Optional ApiStudioViewModel managing editor state & HTTP execution.
 * @param collectionsViewModel Optional CollectionsViewModel managing collections & unsaved sessions.
 * @param modifier Layout modifier.
 */
@Composable
fun ApiStudioScreen(
    viewModel: ApiStudioViewModel? = null,
    collectionsViewModel: CollectionsViewModel? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors

    val uiState by (viewModel?.uiState ?: MutableStateFlow(ApiStudioState())).collectAsState()
    val collectionsState by (collectionsViewModel?.uiState ?: MutableStateFlow(CollectionsState())).collectAsState()

    var selectedRequestId by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(uiState.editorState.linkedUnsavedId) {
        val linkedId = uiState.editorState.linkedUnsavedId
        if (!linkedId.isNullOrBlank() && uiState.editorState.sessionType == com.devuloopers.knet.ui.desktop.apistudio.model.SessionType.UNSAVED_DRAFT) {
            selectedRequestId = linkedId
            viewModel?.setUnsavedDraftSession(linkedId)
        }
    }

    LaunchedEffect(uiState.sessionContext) {
        val context = uiState.sessionContext
        val targetSessionId = when (context) {
            is SessionContext.UnsavedDraft -> context.sessionId
            is SessionContext.SavedRequest -> context.requestId
            SessionContext.None -> ""
        }
        if (targetSessionId.isNotBlank() && selectedRequestId != targetSessionId) {
            val unsavedMatch = collectionsState.unsavedRequests.find { it.id == targetSessionId }
            if (unsavedMatch != null) {
                selectedRequestId = unsavedMatch.id
                viewModel?.updateLinkedUnsavedId(unsavedMatch.id, unsavedMatch.name)
                viewModel?.updateMethod(unsavedMatch.method)
                viewModel?.updateUrl(unsavedMatch.url)
                viewModel?.updateHeaders(unsavedMatch.headers)
                viewModel?.updateBodyState(RequestBodyState.from(PayloadInspectionSpec.fromPayload(unsavedMatch.headers, unsavedMatch.bodyPayload)))
                viewModel?.updateScripts(unsavedMatch.preRequestScript, unsavedMatch.testScript)
            } else {
                fun findInFolder(folders: List<com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem>): com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem? {
                    for (folder in folders) {
                        val match = folder.requests.find { it.id == targetSessionId }
                        if (match != null) return match
                    }
                    return null
                }
                val savedMatch = findInFolder(collectionsState.collections)
                if (savedMatch != null) {
                    selectedRequestId = savedMatch.id
                    viewModel?.updateLinkedUnsavedId(null, savedMatch.name)
                    viewModel?.updateMethod(savedMatch.method)
                    viewModel?.updateUrl(savedMatch.url)
                    viewModel?.updateHeaders(savedMatch.headers)
                    viewModel?.updateBodyState(RequestBodyState.from(PayloadInspectionSpec.fromPayload(savedMatch.headers, savedMatch.bodyPayload)))
                    viewModel?.updateScripts(savedMatch.preRequestScript, savedMatch.testScript)
                }
            }
        }
    }

    LaunchedEffect(collectionsState.unsavedRequests, collectionsState.collections) {
        val hasUnsaved = collectionsState.unsavedRequests.isNotEmpty()
        val hasCollections = collectionsState.collections.any { it.requests.isNotEmpty() }
        if (!hasUnsaved && !hasCollections) {
            selectedRequestId = null
            viewModel?.clearSession()
        }
    }

    val currentMethod = uiState.editorState.method
    val currentUrl = uiState.editorState.url
    val activeSubTabEnum = uiState.editorState.activeSubTab

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().background(themeColors.surface)) {
            // Left Pane: Collections Sidebar connected to CollectionsViewModel state
            CollectionsSidebar(
                unsavedRequests = collectionsState.unsavedRequests,
                collections = collectionsState.collections,
                selectedRequestId = selectedRequestId,
                onRequestSelected = { item ->
                    selectedRequestId = item.id
                    // Route session context: saved collection vs unsaved draft
                    val collId = item.collectionId
                    val fldId = item.folderId
                    if (collId != null && fldId != null) {
                        // Item is from a saved collection — set SavedRequest context
                        viewModel?.setSavedRequestSession(
                            requestId = item.id,
                            collectionId = collId,
                            folderId = fldId
                        )
                        // Clear linkedUnsavedId so unsaved auto-save is not triggered
                        viewModel?.updateLinkedUnsavedId(null, item.name)
                    } else {
                        // Item is an unsaved draft — set UnsavedDraft context
                        viewModel?.setUnsavedDraftSession(item.id)
                        viewModel?.updateLinkedUnsavedId(item.id, item.name)
                    }
                    viewModel?.updateMethod(item.method)
                    viewModel?.updateUrl(item.url)
                    viewModel?.updateHeaders(item.headers)
                    viewModel?.updateBodyState(RequestBodyState.from(PayloadInspectionSpec.fromPayload(item.headers, item.bodyPayload)))
                    viewModel?.updateScripts(item.preRequestScript, item.testScript)
                    viewModel?.updateAuthState(item.authState)
                },
                onSaveUnsavedRequest = { item ->
                    selectedRequestId = item.id
                    viewModel?.setUnsavedDraftSession(item.id)
                    viewModel?.updateLinkedUnsavedId(item.id, item.name)
                    viewModel?.updateMethod(item.method)
                    viewModel?.updateUrl(item.url)
                    viewModel?.updateHeaders(item.headers)
                    viewModel?.updateBodyState(RequestBodyState.from(PayloadInspectionSpec.fromPayload(item.headers, item.bodyPayload)))
                    viewModel?.updateScripts(item.preRequestScript, item.testScript)
                    viewModel?.updateAuthState(item.authState)
                    collectionsViewModel?.openSaveDialog()
                },
                onDeleteUnsavedRequest = { item ->
                    collectionsViewModel?.deleteUnsavedRequest(item.id)
                    viewModel?.closeTab(item.id)
                    if (selectedRequestId == item.id) {
                        selectedRequestId = null
                    }
                },
                onNewUnsavedSessionClicked = {
                    collectionsViewModel?.createEmptyUnsavedSession { newId, sessionTitle ->
                        selectedRequestId = newId
                        viewModel?.setUnsavedDraftSession(newId)
                        viewModel?.updateLinkedUnsavedId(newId, sessionTitle)
                        viewModel?.updateMethod("GET")
                        viewModel?.updateUrl("")
                        viewModel?.updateHeaders(emptyList())
                        viewModel?.updateBodyState(RequestBodyState(mode = RequestBodyMode.NONE))
                        viewModel?.updateScripts("", "")
                    }
                },
                onRenameCollection = { item ->
                    collectionsViewModel?.openRenameDialog(item.collectionId, item.name)
                },
                onDeleteCollection = { item ->
                    collectionsViewModel?.deleteCollection(item.collectionId)
                },
                onRenameSavedRequest = { item ->
                    collectionsViewModel?.openRenameRequestDialog(item)
                },
                onDeleteSavedRequest = { item ->
                    collectionsViewModel?.deleteSavedRequest(item.id)
                    viewModel?.closeTab(item.id)
                    if (selectedRequestId == item.id) {
                        selectedRequestId = null
                    }
                },
                onNewCollectionClicked = {
                    collectionsViewModel?.openCreateCollectionDialog()
                }
            )

            VerticalDivider(color = themeColors.border)

            HorizontalSplitPane(
                firstPane = { paneModifier ->
                    Column(
                        modifier = paneModifier
                            .fillMaxSize()
                            .background(themeColors.surface)
                    ) {
                        RequestUrlBar(
                            method = currentMethod,
                            url = currentUrl,
                            onMethodChanged = { newMethod ->
                                viewModel?.updateMethod(newMethod)
                                when (val context = uiState.sessionContext) {
                                    is SessionContext.SavedRequest -> collectionsViewModel?.triggerSavedRequestAutoSave(
                                        requestId = context.requestId, collectionId = context.collectionId, folderId = context.folderId,
                                        editorState = uiState.editorState.copy(method = newMethod)
                                    )
                                    else -> collectionsViewModel?.triggerUnsavedAutoSave(
                                        editorState = uiState.editorState.copy(method = newMethod),
                                        onLinkedIdAssigned = { id, title -> viewModel?.updateLinkedUnsavedId(id, title) }
                                    )
                                }
                            },
                            onUrlChanged = { newUrl ->
                                viewModel?.updateUrl(newUrl)
                                when (val context = uiState.sessionContext) {
                                    is SessionContext.SavedRequest -> collectionsViewModel?.triggerSavedRequestAutoSave(
                                        requestId = context.requestId, collectionId = context.collectionId, folderId = context.folderId,
                                        editorState = uiState.editorState.copy(url = newUrl)
                                    )
                                    else -> collectionsViewModel?.triggerUnsavedAutoSave(
                                        editorState = uiState.editorState.copy(url = newUrl),
                                        onLinkedIdAssigned = { id, title -> viewModel?.updateLinkedUnsavedId(id, title) }
                                    )
                                }
                            },
                            onSendClicked = {
                                viewModel?.executeRequest()
                            },
                            onCancelClicked = {
                                viewModel?.cancelExecution()
                            },
                            onSaveClicked = {
                                collectionsViewModel?.openSaveDialog()
                            },
                            isExecuting = uiState.executionState == ExecutionState.EXECUTING
                        )

                        HorizontalDivider(color = themeColors.border)

                        RequestEditorPanel(
                            bodyState = uiState.editorState.bodyState,
                            queryParams = uiState.editorState.queryParams,
                            headers = uiState.editorState.headers,
                            cookies = uiState.editorState.cookies,
                            authState = uiState.editorState.authState,
                            preRequestScript = uiState.editorState.preRequestScript,
                            testScript = uiState.editorState.testScript,
                            activeSubTab = activeSubTabEnum,
                            activeScriptPhase = uiState.editorState.activeScriptPhase,
                            scriptLanguage = uiState.editorState.scriptLanguage,
                            actions = RequestEditorPanelActions(
                                onBodyStateChanged = { viewModel?.updateBodyState(it) },
                                onGraphQlStateChanged = { viewModel?.updateGraphQlState(it) },
                                onBodyPayloadChanged = { viewModel?.updateBodyPayload(it) },
                                onQueryParamsChanged = { viewModel?.updateQueryParams(it) },
                                onHeadersChanged = { viewModel?.updateHeaders(it) },
                                onCookiesChanged = { viewModel?.updateCookies(it) },
                                onAuthStateChanged = { viewModel?.updateAuthState(it) },
                                onPreRequestScriptChanged = { viewModel?.updatePreRequestScript(it) },
                                onTestScriptChanged = { viewModel?.updateTestScript(it) },
                                onSubTabSelected = { viewModel?.updateActiveSubTab(it) },
                                onScriptPhaseSelected = { viewModel?.updateActiveScriptPhase(it) },
                                onScriptLanguageChanged = { viewModel?.updateScriptLanguage(it) }
                            )
                        )
                    }
                },
                secondPane = { paneModifier ->
                    // Right Pane: Response Inspector
                    val inspectorState = uiState.responseInspection?.copy(
                        executionState = uiState.executionState,
                        errorMessage = uiState.errorMessage,
                    ) ?: ResponseInspectorState(
                        executionState = uiState.executionState,
                        errorMessage = uiState.errorMessage,
                    )
                    val activeResponseSubTabEnum = uiState.editorState.activeResponseSubTab
                    ResponseInspectorView(
                        state = inspectorState,
                        actions = ResponseInspectorActions(
                            onClearResponse = { viewModel?.clearResponse() }
                        ),
                        activeSubTab = activeResponseSubTabEnum,
                        onSubTabSelected = { viewModel?.updateActiveResponseSubTab(it) },
                        modifier = paneModifier
                    )
                },
                initialSplitRatio = 0.5f
            )
        }

        // Render Save Request Dialog modal when active
        if (collectionsState.isSaveDialogOpen) {
            val defaultReqTitle = uiState.tabs.find { it.id == uiState.activeTabId }?.title ?: "New Request"
            SaveRequestDialog(
                defaultName = defaultReqTitle,
                existingCollections = collectionsState.collections,
                onDismiss = { collectionsViewModel?.closeSaveDialog() },
                onConfirm = { reqName, saveMode, selectedColId, newColName ->
                    collectionsViewModel?.saveRequestToCollection(
                        requestName = reqName,
                        mode = saveMode,
                        selectedCollectionId = selectedColId,
                        newCollectionName = newColName,
                        currentEditor = uiState.editorState,
                        onSaved = { savedId ->
                            viewModel?.updateLinkedUnsavedId(savedId, reqName)
                        }
                    )
                }
            )
        }

        // Render CreateCollectionDialog modal when active
        if (collectionsState.isCreateCollectionDialogOpen) {
            CreateCollectionDialog(
                onDismiss = { collectionsViewModel?.closeCreateCollectionDialog() },
                onConfirm = { name ->
                    collectionsViewModel?.createCollection(name)
                }
            )
        }

        // Render RenameCollectionDialog modal when active
        if (collectionsState.isRenameDialogOpen) {
            val collectionId = collectionsState.renamingCollectionId
            val currentName = collectionsState.renamingCollectionName
            if (collectionId != null) {
                RenameCollectionDialog(
                    currentName = currentName,
                    onDismiss = { collectionsViewModel?.closeRenameDialog() },
                    onConfirm = { newName ->
                        collectionsViewModel?.renameCollection(collectionId, newName)
                    }
                )
            }
        }

        // Render RenameRequestDialog modal when active
        if (collectionsState.isRenameRequestDialogOpen) {
            val reqItem = collectionsState.renamingRequestItem
            if (reqItem != null) {
                com.devuloopers.knet.ui.desktop.apistudio.dialog.RenameRequestDialog(
                    currentName = reqItem.name,
                    onDismiss = { collectionsViewModel?.closeRenameRequestDialog() },
                    onConfirm = { newName ->
                        collectionsViewModel?.renameSavedRequest(reqItem, newName)
                        if (selectedRequestId == reqItem.id) {
                            viewModel?.updateLinkedUnsavedId(null, newName)
                        }
                    }
                )
            }
        }
    }
}
