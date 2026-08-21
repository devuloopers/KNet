package com.devuloopers.knet.ui.desktop.apistudio.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.progress.CircularProgress
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.components.split.HorizontalSplitPane
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.dialog.CreateCollectionDialog
import com.devuloopers.knet.ui.desktop.apistudio.dialog.RenameCollectionDialog
import com.devuloopers.knet.ui.desktop.apistudio.dialog.RenameRequestDialog
import com.devuloopers.knet.ui.desktop.apistudio.dialog.SaveRequestDialog
import com.devuloopers.knet.ui.desktop.apistudio.editor.RequestUrlBar
import com.devuloopers.knet.ui.desktop.apistudio.model.ExecutionState
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponseInspectorState
import com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseInspectorActions
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseInspectorView
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.CollectionsSidebar
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.CollectionsSidebarActions
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.CollectionsViewModel
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanel
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanelActions

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
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    var responseSplitRatio by remember { mutableStateOf(0.5f) }
    val uiState by viewModel.uiState.collectAsState()
    val collectionsState by collectionsViewModel.uiState.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().background(themeColors.surface)) {
            CollectionsSidebar(
                state = collectionsState,
                selectedRequestId = uiState.selectedRequestId,
                canSaveActiveRequest = uiState.sessionContext is SessionContext.UnsavedDraft,
                actions = CollectionsSidebarActions(
                    onRequestSelected = viewModel::openRequest,
                    onSaveUnsavedRequest = { item ->
                        viewModel.openRequest(item)
                        viewModel.openSaveDialog()
                    },
                    onDeleteUnsavedRequest = { item ->
                        collectionsViewModel.deleteUnsavedRequest(item.id) {
                            viewModel.closeTab(item.id)
                        }
                    },
                    onNewUnsavedSessionClicked = viewModel::createNewDraft,
                    onRenameCollection = { item ->
                        collectionsViewModel.openRenameDialog(item.collectionId, item.name)
                    },
                    onDeleteCollection = { item ->
                        collectionsViewModel.deleteCollection(item.collectionId) {
                            if (item.requests.any { it.id == uiState.selectedRequestId }) {
                                uiState.selectedRequestId?.let(viewModel::closeTab)
                            }
                        }
                    },
                    onRenameSavedRequest = collectionsViewModel::openRenameRequestDialog,
                    onDeleteSavedRequest = { item ->
                        collectionsViewModel.deleteSavedRequest(item.id) {
                            viewModel.closeTab(item.id)
                        }
                    },
                    onSaveActiveRequest = viewModel::openSaveDialog,
                    onNewCollectionClicked = collectionsViewModel::openCreateCollectionDialog
                )
            )

            VerticalDivider(color = themeColors.border)

            HorizontalSplitPane(
                splitRatio = responseSplitRatio,
                onSplitRatioChange = { responseSplitRatio = it },
                firstPane = { paneModifier ->
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
                                    .padding(horizontal = KNetTheme.spacing.md, vertical = KNetTheme.spacing.xs),
                                style = KNetTheme.typography.caption.copy(color = themeColors.semantic.error)
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
                secondPane = { paneModifier ->
                    val inspectorState = uiState.responseInspection?.copy(
                        executionState = uiState.executionState,
                        errorMessage = uiState.errorMessage
                    ) ?: ResponseInspectorState(
                        executionState = uiState.executionState,
                        errorMessage = uiState.errorMessage
                    )
                    ResponseInspectorView(
                        state = inspectorState,
                        actions = ResponseInspectorActions(onClearResponse = viewModel::clearResponse),
                        activeSubTab = uiState.editorState.activeResponseSubTab,
                        onSubTabSelected = viewModel::updateActiveResponseSubTab,
                        modifier = paneModifier
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (uiState.isSaveDialogOpen) {
            SaveRequestDialog(
                defaultName = uiState.activeDocumentTitle,
                existingCollections = collectionsState.collections,
                onDismiss = viewModel::closeSaveDialog,
                onConfirm = viewModel::saveRequestToCollection
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
                            viewModel.renameActiveDocument(request.id, newName)
                        }
                    }
                )
            }
        }

        if (uiState.isRestoring) {
            Box(
                modifier = Modifier.fillMaxSize().background(themeColors.surface),
                contentAlignment = Alignment.Center
            ) {
                CircularProgress(modifier = Modifier.size(28.dp))
            }
        }
    }
}
