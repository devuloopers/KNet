package com.devuloopers.knet.ui.apistudio.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.controller.ProxyStateController
import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.ui.apistudio.view.dialogs.CollectionRunnerModal
import com.devuloopers.knet.ui.apistudio.view.dialogs.CreateItemDialog
import com.devuloopers.knet.ui.apistudio.view.sidebar.CollectionsTreeSidebar
import com.devuloopers.knet.ui.apistudio.viewmodel.ApiStudioViewModel

/**
 * Root 3-Column API Studio & Test Runner Screen for KNet.
 *
 * Composes the three main panels side-by-side:
 * - **Left** (25 %): [CollectionsTreeSidebar] — navigation tree for saved & unsaved requests.
 * - **Middle** (45 %): [RequestBuilderPanel] — URL bar, method picker, request tabs (body/params/auth/headers/scripts).
 * - **Right** (30 %): [ResponseTestPanel] — response viewer, test assertion results.
 *
 * This composable is intentionally thin: it owns only the ViewModel binding, dialog
 * visibility state, and layout weights. All business logic lives in [ApiStudioViewModel];
 * all UI logic lives in the individual panel composables.
 *
 * @param controller Proxy state controller used to seed the ViewModel proxy port.
 * @param viewModel The [ApiStudioViewModel] driving this screen (defaults to a remembered instance).
 * @param modifier Layout modifier for the root container.
 */
@Composable
fun ApiStudioScreen(
    controller: ProxyStateController,
    viewModel: ApiStudioViewModel = remember { ApiStudioViewModel(proxyPort = controller.proxyPort) },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedRequest = uiState.selectedRequest ?: SavedApiRequest(
        id = "r-new",
        name = "New Request",
        method = HttpMethod.GET,
        url = ""
    )

    var showCreateCollectionDialog by remember { mutableStateOf(false) }
    if (showCreateCollectionDialog) {
        CreateItemDialog(
            title = "Create New API Collection",
            placeholder = "e.g. Authentication Service APIs",
            onConfirm = { name ->
                viewModel.createNewCollection(name)
                showCreateCollectionDialog = false
            },
            onDismiss = { showCreateCollectionDialog = false }
        )
    }

    if (uiState.isSuiteRunning || uiState.suiteRunSummary != null) {
        CollectionRunnerModal(
            collectionName = uiState.collections.firstOrNull()?.name ?: "API Collection",
            isRunning = uiState.isSuiteRunning,
            summary = uiState.suiteRunSummary,
            onDismiss = { viewModel.dismissRunnerModal() }
        )
    }

    var showImportDialog by remember { mutableStateOf(false) }
    if (showImportDialog) {
        CreateItemDialog(
            title = "Import Postman Collection JSON",
            placeholder = "Paste raw Postman v2.1 JSON here...",
            onConfirm = { json ->
                viewModel.importPostmanJson(json)
                showImportDialog = false
            },
            onDismiss = { showImportDialog = false }
        )
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Column (25 %): Sidebar navigation
        CollectionsTreeSidebar(
            collections = uiState.collections,
            unsavedRequests = uiState.unsavedRequests,
            selectedRequestId = selectedRequest.id,
            searchQuery = uiState.searchQuery,
            onSearchChange = { viewModel.updateSearchQuery(it) },
            onSelectRequest = { req -> viewModel.selectRequest(req) },
            onDeleteRequest = { reqId ->
                val colId = uiState.collections.find { col ->
                    col.folders.any { it.requests.any { r -> r.id == reqId } }
                }?.id ?: ""
                viewModel.deleteRequest(colId, reqId)
            },
            onDeleteUnsavedRequest = { reqId -> viewModel.deleteUnsavedRequest(reqId) },
            onAddNewUnsavedRequest = { viewModel.createUnsavedRequest() },
            onSaveUnsavedToCollection = { req ->
                val collectionId = uiState.collections.firstOrNull()?.id ?: return@CollectionsTreeSidebar
                val folderId =
                    uiState.collections.firstOrNull()?.folders?.firstOrNull()?.id ?: return@CollectionsTreeSidebar
                viewModel.saveUnsavedToCollection(req.id, collectionId, folderId)
            },
            onDeleteCollection = { colId -> viewModel.deleteCollection(colId) },
            onRenameCollection = { colId, name -> viewModel.renameCollection(colId, name) },
            onRenameRequest = { reqId, name -> viewModel.renameSavedRequest(reqId, name) },
            onRunCollection = { viewModel.runCollectionSuite() },
            onCreateCollection = { showCreateCollectionDialog = true },
            onImportCollection = { showImportDialog = true },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        // Middle Column (45 %): Request builder
        RequestBuilderPanel(
            request = selectedRequest,
            uiState = uiState,
            activeTab = uiState.activeReqTab,
            isExecuting = uiState.isExecuting,
            onTabSelected = { viewModel.selectReqTab(it) },
            onUrlChange = { viewModel.onUrlInputChanged(it) },
            onToggleHeader = { viewModel.toggleHeader(it) },
            onUpdateHeaderKey = { oldKey, newKey -> viewModel.updateHeaderKey(oldKey, newKey) },
            onUpdateHeaderValue = { key, value -> viewModel.updateHeaderValue(key, value) },
            onAddHeader = { viewModel.addHeader() },
            onRemoveHeader = { viewModel.removeHeader(it) },
            onRestoreDefaultHeaders = { viewModel.restoreDefaultHeaders() },
            onAuthTypeChange = { viewModel.updateAuthType(it) },
            onAuthTokenChange = { viewModel.updateAuthToken(it) },
            onAuthUsernameChange = { viewModel.updateAuthUsername(it) },
            onAuthPasswordChange = { viewModel.updateAuthPassword(it) },
            onApiKeyNameChange = { viewModel.updateApiKeyName(it) },
            onApiKeyValueChange = { viewModel.updateApiKeyValue(it) },
            onApiKeyLocationChange = { viewModel.updateApiKeyLocation(it) },
            onOauthHeaderPrefixChange = { viewModel.updateOauthHeaderPrefix(it) },
            onAwsAccessKeyChange = { viewModel.updateAwsAccessKey(it) },
            onAwsSecretKeyChange = { viewModel.updateAwsSecretKey(it) },
            onAwsRegionChange = { viewModel.updateAwsRegion(it) },
            onAwsServiceChange = { viewModel.updateAwsService(it) },
            onScriptLanguageChange = { viewModel.updateScriptLanguage(it) },
            onPreRequestScriptChange = { viewModel.updatePreRequestScript(it) },
            onTestScriptChange = { viewModel.updateTestScript(it) },
            onBodyChange = { viewModel.updateRequestBody(it) },
            onBodyTypeChange = { viewModel.updateRequestBodyType(it) },
            onSend = { viewModel.sendCurrentRequest() },
            modifier = Modifier
                .weight(1.8f)
                .fillMaxHeight()
        )

        // Right Column (30 %): Response & test results
        ResponseTestPanel(
            request = selectedRequest,
            activeTab = uiState.activeRespTab,
            latestResult = uiState.latestResult,
            testResults = uiState.testResults,
            onTabSelected = { viewModel.selectRespTab(it) },
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
        )
    }
}
