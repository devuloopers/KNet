package com.devuloopers.knet.ui.apistudio.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.devuloopers.knet.widgets.WidgetSearchBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.controller.ProxyStateController
import com.devuloopers.knet.domain.apistudio.model.CollectionFolder
import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.model.isUrlValid
import com.devuloopers.knet.domain.apistudio.model.TestAssertionResult
import com.devuloopers.knet.theme.KNetColors
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.TextStyle
import com.devuloopers.knet.ui.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult
import com.devuloopers.knet.ui.apistudio.view.dialogs.CreateItemDialog
import com.devuloopers.knet.ui.apistudio.view.dialogs.CollectionRunnerModal
import com.devuloopers.knet.widgets.TableCellTextField
import com.devuloopers.knet.widgets.KNetInputField

/**
 * 3-Column API Studio & Test Runner Screen for KNet.
 */
@Composable
fun ApiStudioScreen(
    controller: ProxyStateController,
    viewModel: ApiStudioViewModel = remember { ApiStudioViewModel(proxyPort = controller.proxyPort) },
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val folders = remember(uiState.collections) { uiState.collections.flatMap { it.folders } }
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
        // ── Left Column: Collections Tree Sidebar (25% width) ────────────────
        CollectionsTreeSidebar(
            folders = folders,
            selectedRequestId = selectedRequest.id,
            searchQuery = uiState.searchQuery,
            onSearchChange = { viewModel.updateSearchQuery(it) },
            onSelectRequest = { req -> viewModel.selectRequest(req) },
            onDeleteRequest = { reqId ->
                val colId = uiState.collections.firstOrNull()?.id ?: ""
                viewModel.deleteRequest(colId, reqId)
            },
            onRunCollection = { viewModel.runCollectionSuite() },
            onCreateCollection = { showCreateCollectionDialog = true },
            onImportCollection = { showImportDialog = true },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        // ── Middle Column: Request Details & Payload Builder (45% width) ─────
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

        // ── Right Column: Response & Test Assertions Panel (30% width) ──────
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

// ─────────────────────────────────────────────────────────────────────────────
// Left Column — Collections Tree Sidebar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CollectionsTreeSidebar(
    folders: List<CollectionFolder>,
    selectedRequestId: String,
    searchQuery: String = "",
    onSearchChange: (String) -> Unit = {},
    onSelectRequest: (SavedApiRequest) -> Unit,
    onDeleteRequest: (String) -> Unit = {},
    onDeleteCollection: (String) -> Unit = {},
    onRunCollection: () -> Unit,
    onCreateCollection: () -> Unit,
    onImportCollection: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var folderStateMap by remember { mutableStateOf(folders.associate { it.id to it.isExpanded }) }

    val filteredFolders = remember(folders, searchQuery) {
        if (searchQuery.isBlank()) folders else {
            folders.mapNotNull { folder ->
                val matchingRequests = folder.requests.filter {
                    it.name.contains(searchQuery, ignoreCase = true) || it.url.contains(searchQuery, ignoreCase = true)
                }
                if (folder.name.contains(searchQuery, ignoreCase = true) || matchingRequests.isNotEmpty()) {
                    folder.copy(isExpanded = true, requests = if (matchingRequests.isNotEmpty()) matchingRequests else folder.requests)
                } else null
            }
        }
    }

    Box(
        modifier = modifier
            .background(KNetColors.SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search & Filter Input Bar (Reusing Live Traffic's WidgetSearchBar)
            WidgetSearchBar(
                query = searchQuery,
                onQueryChange = onSearchChange,
                placeholder = "Search collections..."
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action buttons row: + New Collection | Import
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .clickable { onCreateCollection() }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ New Collection", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .clickable { onImportCollection() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Import", color = KNetColors.TextSecondary, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Expandable Tree View (Scrollable / List)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                folders.forEach { folder ->
                    val isExpanded = folderStateMap[folder.id] == true
                    // Folder item row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                folderStateMap = folderStateMap.toMutableMap().apply {
                                    put(folder.id, !isExpanded)
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                            contentDescription = null,
                            tint = KNetColors.TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${folder.name} (${folder.requests.size})",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Request children inside folder
                    if (isExpanded) {
                        Column(
                            modifier = Modifier.padding(start = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            folder.requests.forEach { req ->
                                val isSelected = req.id == selectedRequestId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isSelected) KNetColors.ActiveBlue.copy(alpha = 0.2f) else Color.Transparent,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable { onSelectRequest(req) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            // Method Badge
                                            Text(
                                                text = req.method.name,
                                                color = Color(req.method.badgeColorHex),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.width(34.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = req.name,
                                                color = if (isSelected) Color.White else KNetColors.TextSecondary,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        // Delete Request Icon
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable { onDeleteRequest(req.id) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("✕", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                        }
                                    }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Primary Action: ▶ Run Collection
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KNetColors.ActiveBlue, RoundedCornerShape(6.dp))
                    .clickable { onRunCollection() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Collection", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Middle Column — Request Details & Payload Builder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RequestBuilderPanel(
    request: SavedApiRequest,
    uiState: com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState,
    activeTab: String,
    isExecuting: Boolean = false,
    onTabSelected: (String) -> Unit,
    onUrlChange: (String) -> Unit = {},
    onToggleHeader: (String) -> Unit = {},
    onUpdateHeaderKey: (String, String) -> Unit = { _, _ -> },
    onUpdateHeaderValue: (String, String) -> Unit = { _, _ -> },
    onAddHeader: () -> Unit = {},
    onRemoveHeader: (String) -> Unit = {},
    onRestoreDefaultHeaders: () -> Unit = {},
    onAuthTypeChange: (String) -> Unit = {},
    onAuthTokenChange: (String) -> Unit = {},
    onAuthUsernameChange: (String) -> Unit = {},
    onAuthPasswordChange: (String) -> Unit = {},
    onApiKeyNameChange: (String) -> Unit = {},
    onApiKeyValueChange: (String) -> Unit = {},
    onApiKeyLocationChange: (String) -> Unit = {},
    onOauthHeaderPrefixChange: (String) -> Unit = {},
    onAwsAccessKeyChange: (String) -> Unit = {},
    onAwsSecretKeyChange: (String) -> Unit = {},
    onAwsRegionChange: (String) -> Unit = {},
    onAwsServiceChange: (String) -> Unit = {},
    onScriptLanguageChange: (com.devuloopers.knet.scriptengine.api.ScriptLanguage) -> Unit = {},
    onPreRequestScriptChange: (String) -> Unit = {},

    onTestScriptChange: (String) -> Unit = {},
    onBodyChange: (String) -> Unit = {},
    onBodyTypeChange: (String) -> Unit = {},
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reqTabs = listOf("Body", "Params", "Authorization", "Headers", "Pre-request Script", "Tests")
    val isUrlValid = request.isUrlValid

    Box(
        modifier = modifier
            .background(KNetColors.SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // URL Bar: Method Dropdown + URL Field + Send Button + Save Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var methodDropdownExpanded by remember { mutableStateOf(false) }
                var selectedMethod by remember(request) { mutableStateOf(request.method) }
                var customMethodText by remember(request) { mutableStateOf(request.customMethod ?: "CUSTOM") }

                // Method Badge Dropdown / Editable Badge
                Box {
                    Box(
                        modifier = Modifier
                            .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                            .clickable { methodDropdownExpanded = !methodDropdownExpanded }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (selectedMethod == HttpMethod.CUSTOM) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = customMethodText,
                                    onValueChange = { customMethodText = it.uppercase() },
                                    singleLine = true,
                                    cursorBrush = SolidColor(Color(selectedMethod.badgeColorHex)),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color(selectedMethod.badgeColorHex),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    modifier = Modifier.width(intrinsicSize = androidx.compose.foundation.layout.IntrinsicSize.Min)
                                )
                            } else {
                                Text(
                                    text = selectedMethod.name,
                                    color = Color(selectedMethod.badgeColorHex),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = KNetColors.TextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }

                    androidx.compose.material3.DropdownMenu(
                        expanded = methodDropdownExpanded,
                        onDismissRequest = { methodDropdownExpanded = false },
                        modifier = Modifier.background(KNetColors.SurfaceDark).border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                    ) {
                        HttpMethod.entries.forEach { method ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = {
                                    Text(
                                        text = method.name,
                                        color = Color(method.badgeColorHex),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                },
                                onClick = {
                                    selectedMethod = method
                                    methodDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // URL Text Input Field
                KNetInputField(
                    value = request.url,
                    onValueChange = onUrlChange,
                    placeholder = "https://api.example.com/v1/resource",
                    fontSize = 12.sp,
                    height = 36.dp,
                    cornerRadius = 6.dp,
                    modifier = Modifier.weight(1f)
                )

                // Send Request Primary Button
                Box(
                    modifier = Modifier
                        .background(
                            if (!isUrlValid) KNetColors.ActiveBlue.copy(alpha = 0.4f)
                            else if (isExecuting) KNetColors.ActiveBlue.copy(alpha = 0.5f)
                            else KNetColors.ActiveBlue,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable(enabled = isUrlValid && !isExecuting) { onSend() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isExecuting) {
                            androidx.compose.material3.CircularProgressIndicator(color = Color.White, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (isExecuting) "Sending..." else "Send Request", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Save Icon Button
                Box(
                    modifier = Modifier
                        .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                        .clickable { }
                        .padding(8.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-tabs Row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = KNetColors.BorderDark, shape = RoundedCornerShape(0.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reqTabs.forEach { tabName ->
                        val isTabActive = tabName == activeTab && isUrlValid
                        Column(
                            modifier = Modifier
                                .clickable(enabled = isUrlValid) { onTabSelected(tabName) }
                                .padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tabName,
                                color = if (isTabActive) Color.White else if (!isUrlValid) KNetColors.TextSecondary.copy(alpha = 0.35f) else KNetColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(if (isTabActive) KNetColors.ActiveBlue else Color.Transparent)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-tab Content Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(KNetColors.BackgroundDark, RoundedCornerShape(6.dp))
                    .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                if (!isUrlValid) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🌐", fontSize = 24.sp)
                            Text(
                                text = "Enter a valid URL above to configure headers, body, and request parameters",
                                color = KNetColors.TextSecondary.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    when {
                    activeTab.startsWith("Body") -> {
                        val bodyModes = listOf("none", "json", "form-data", "x-www-form-urlencoded", "raw", "graphql")
                        val currentBodyType = request.bodyType.ifBlank { "json" }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                            // Body Mode Selector Pills Row
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Payload Mode:", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
                                bodyModes.forEach { mode ->
                                    val isSelected = currentBodyType.equals(mode, ignoreCase = true) || (mode == "raw" && currentBodyType.startsWith("raw"))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (isSelected) KNetColors.ActiveBlue else KNetColors.FieldDark,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) KNetColors.ActiveBlue else KNetColors.BorderDark,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                val newType = if (mode == "raw") "raw-text" else mode
                                                onBodyTypeChange(newType)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = mode,
                                            color = if (isSelected) Color.White else KNetColors.TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(thickness = 0.5.dp, color = KNetColors.BorderDark.copy(alpha = 0.5f))

                            // Body Payload Content Area
                            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                                when {
                                    currentBodyType.equals("none", ignoreCase = true) -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("This request does not have a body payload.", color = KNetColors.TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp)
                                        }
                                    }
                                    currentBodyType.equals("json", ignoreCase = true) -> {
                                        CodeEditorWidget(
                                            code = request.bodyPayload,
                                            onCodeChange = onBodyChange,
                                            placeholder = "// Enter raw JSON payload content...\n{\n  \"key\": \"value\"\n}",
                                            textColor = Color(0xFFA855F7),
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    currentBodyType.startsWith("raw", ignoreCase = true) -> {
                                        val rawSubFormats = listOf("text", "json", "xml", "html", "javascript")
                                        val currentSubFormat = if (currentBodyType.contains("-")) currentBodyType.substringAfter("-") else "text"
                                        var rawFormatDropdownExpanded by remember { mutableStateOf(false) }
                                        var selectedSubFormat by remember(currentSubFormat) {
                                            mutableStateOf(if (currentBodyType.contains("-")) currentBodyType.substringAfter("-") else "text")
                                        }

                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Format: ", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Box {
                                                    Box(
                                                        modifier = Modifier
                                                            .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                                                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                                                            .clickable { rawFormatDropdownExpanded = !rawFormatDropdownExpanded }
                                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(selectedSubFormat.uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = KNetColors.TextSecondary, modifier = Modifier.size(12.dp))
                                                        }
                                                    }
                                                    androidx.compose.material3.DropdownMenu(
                                                        expanded = rawFormatDropdownExpanded,
                                                        onDismissRequest = { rawFormatDropdownExpanded = false },
                                                        modifier = Modifier.background(KNetColors.SurfaceDark).border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                                                    ) {
                                                        rawSubFormats.forEach { fmt ->
                                                            androidx.compose.material3.DropdownMenuItem(
                                                                text = { Text(fmt.uppercase(), color = Color.White, fontSize = 10.sp) },
                                                                onClick = {
                                                                    selectedSubFormat = fmt
                                                                    rawFormatDropdownExpanded = false
                                                                    onBodyTypeChange("raw-$fmt")
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            val rawSubFormat = if (currentBodyType.contains("-")) currentBodyType.substringAfter("-") else "text"
                                            val accentColor = when (rawSubFormat.lowercase()) {
                                                "xml" -> Color(0xFF06B6D4)
                                                "html" -> Color(0xFF0284C7)
                                                else -> Color(0xFFF8FAFC)
                                            }
                                            CodeEditorWidget(
                                                code = request.bodyPayload,
                                                onCodeChange = onBodyChange,
                                                placeholder = "// Enter raw $rawSubFormat payload content...",
                                                textColor = accentColor,
                                                modifier = Modifier.fillMaxWidth().weight(1f)
                                            )
                                        }
                                    }
                                    currentBodyType.equals("graphql", ignoreCase = true) -> {
                                        CodeEditorWidget(
                                            code = request.bodyPayload,
                                            onCodeChange = onBodyChange,
                                            placeholder = "# Enter GraphQL Query / Mutation...\nquery GetUser {\n  user(id: 1) {\n    name\n  }\n}",
                                            textColor = Color(0xFFA855F7),
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    else -> {
                                        // Form Data / URL Encoded Key-Value Form
                                        CodeEditorWidget(
                                            code = request.bodyPayload,
                                            onCodeChange = onBodyChange,
                                            placeholder = "// Enter key=value form payload data (one per line or standard form string)...",
                                            textColor = Color(0xFFF59E0B),
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    activeTab == "Params" -> {
                        val baseUrl = request.url.substringBefore("?")
                        val queryString = if (request.url.contains("?")) request.url.substringAfter("?").substringBefore("#") else ""
                        
                        var paramList by remember(request.id) {
                            mutableStateOf(
                                if (queryString.isNotBlank()) {
                                    queryString.split("&").mapNotNull { pair ->
                                        val parts = pair.split("=")
                                        if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                                            parts[0] to (if (parts.size > 1) parts[1] else "")
                                        } else null
                                    }.toMutableList()
                                } else mutableListOf()
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Query Parameters (${paramList.size})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .background(KNetColors.ActiveBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, KNetColors.ActiveBlue, RoundedCornerShape(4.dp))
                                        .clickable {
                                            val updated = paramList + ("" to "")
                                            paramList = updated.toMutableList()
                                            val newQuery = updated.filter { it.first.isNotBlank() }.joinToString("&") { "${it.first}=${it.second}" }
                                            onUrlChange(if (newQuery.isNotBlank()) "$baseUrl?$newQuery" else baseUrl)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text("+ Add Parameter", color = KNetColors.ActiveBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Relaxed Single Outer Table Container
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, KNetColors.BorderDark.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                Column {
                                    // Grid Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(KNetColors.FieldDark.copy(alpha = 0.6f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Key", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text("Value", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                                        Box(modifier = Modifier.width(20.dp))
                                    }

                                    HorizontalDivider(thickness = 1.dp, color = KNetColors.BorderDark.copy(alpha = 0.5f))

                                    if (paramList.isEmpty()) {
                                        Text("No query parameters. Click '+ Add Parameter' or type ?key=value in URL.", color = KNetColors.TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                                    } else {
                                        paramList.forEachIndexed { index, (key, value) ->
                                            if (index > 0) {
                                                HorizontalDivider(thickness = 0.5.dp, color = KNetColors.BorderDark.copy(alpha = 0.3f))
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(32.dp)
                                                    .padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Editable Key
                                                var keyTf by remember(key) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(key, selection = androidx.compose.ui.text.TextRange(key.length))) }
                                                TableCellTextField(
                                                    value = keyTf,
                                                    onValueChange = { newKey ->
                                                        keyTf = newKey
                                                        val updated = paramList.toMutableList().apply { this[index] = newKey.text to value }
                                                        paramList = updated
                                                        val newQuery = updated.filter { it.first.isNotBlank() }.joinToString("&") { "${it.first}=${it.second}" }
                                                        onUrlChange(if (newQuery.isNotBlank()) "$baseUrl?$newQuery" else baseUrl)
                                                    },
                                                    placeholder = "Key",
                                                    textColor = Color.White,
                                                    modifier = Modifier.weight(1f)
                                                )

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // Editable Value
                                                var valTf by remember(value) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value, selection = androidx.compose.ui.text.TextRange(value.length))) }
                                                TableCellTextField(
                                                    value = valTf,
                                                    onValueChange = { newVal ->
                                                        valTf = newVal
                                                        val updated = paramList.toMutableList().apply { this[index] = key to newVal.text }
                                                        paramList = updated
                                                        val newQuery = updated.filter { it.first.isNotBlank() }.joinToString("&") { "${it.first}=${it.second}" }
                                                        onUrlChange(if (newQuery.isNotBlank()) "$baseUrl?$newQuery" else baseUrl)
                                                    },
                                                    placeholder = "Value",
                                                    textColor = KNetColors.ActiveBlue,
                                                    modifier = Modifier.weight(1.5f)
                                                )

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // Delete Icon
                                                Box(
                                                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)).clickable {
                                                        val updated = paramList.toMutableList().apply { removeAt(index) }
                                                        paramList = updated
                                                        val newQuery = if (updated.isNotEmpty()) "?${updated.joinToString("&") { "${it.first}=${it.second}" }}" else ""
                                                        onUrlChange("$baseUrl$newQuery")
                                                    },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Remove", tint = KNetColors.TextSecondary, modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    activeTab == "Authorization" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                            Text("Authentication Configuration", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Type: ", color = KNetColors.TextSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                listOf("Inherit Auth", "No Auth", "Bearer Token", "API Key", "Basic Auth", "OAuth 2.0", "AWS Signature").forEach { type ->
                                    val isSelected = type == uiState.authType
                                    Box(
                                        modifier = Modifier
                                            .background(if (isSelected) KNetColors.ActiveBlue else KNetColors.FieldDark, RoundedCornerShape(4.dp))
                                            .border(1.dp, if (isSelected) KNetColors.ActiveBlue else KNetColors.BorderDark, RoundedCornerShape(4.dp))
                                            .clickable { onAuthTypeChange(type) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(type, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                            }

                            when (uiState.authType) {
                                "Inherit Auth" -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
                                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            "This request inherits authentication from its parent Collection or Folder.",
                                            color = KNetColors.TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                "Bearer Token" -> {
                                    Column {
                                        Text("Bearer Token:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .background(KNetColors.ActiveBlue.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                    .border(1.dp, KNetColors.ActiveBlue, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 10.dp, vertical = 7.dp)
                                            ) {
                                                Text(
                                                    "Bearer",
                                                    color = KNetColors.ActiveBlue,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            KNetInputField(
                                                value = uiState.authToken,
                                                onValueChange = onAuthTokenChange,
                                                placeholder = "Paste your token here...",
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Note: The 'Bearer ' prefix is automatically added to the Authorization header.",
                                            color = KNetColors.TextSecondary,
                                            fontSize = 9.sp
                                        )
                                    }
                                }


                                "API Key" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Key Name:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                KNetInputField(
                                                    value = uiState.apiKeyName,
                                                    onValueChange = onApiKeyNameChange,
                                                    placeholder = "e.g. X-API-Key or api_key",
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Key Value:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                KNetInputField(
                                                    value = uiState.apiKeyValue,
                                                    onValueChange = onApiKeyValueChange,
                                                    placeholder = "e.g. secret_live_abcdef123",
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Add To: ", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            listOf("Header", "Query Params").forEach { loc ->
                                                val isLocSelected = loc.equals(uiState.apiKeyLocation, ignoreCase = true)
                                                Box(
                                                    modifier = Modifier
                                                        .background(if (isLocSelected) KNetColors.ActiveBlue else KNetColors.FieldDark, RoundedCornerShape(4.dp))
                                                        .border(1.dp, if (isLocSelected) KNetColors.ActiveBlue else KNetColors.BorderDark, RoundedCornerShape(4.dp))
                                                        .clickable { onApiKeyLocationChange(loc) }
                                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text(loc, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                        }
                                    }
                                }
                                "Basic Auth" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Column {
                                            Text("Username:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            KNetInputField(
                                                value = uiState.authUsername,
                                                onValueChange = onAuthUsernameChange,
                                                placeholder = "Enter username...",
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        Column {
                                            Text("Password:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            KNetInputField(
                                                value = uiState.authPassword,
                                                onValueChange = onAuthPasswordChange,
                                                placeholder = "Enter password...",
                                                isPassword = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                                "OAuth 2.0" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Header Prefix:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                KNetInputField(
                                                    value = uiState.oauthHeaderPrefix,
                                                    onValueChange = onOauthHeaderPrefixChange,
                                                    placeholder = "Bearer",
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            Column(modifier = Modifier.weight(2f)) {
                                                Text("Access Token:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                KNetInputField(
                                                    value = uiState.authToken,
                                                    onValueChange = onAuthTokenChange,
                                                    placeholder = "Paste OAuth 2.0 Access Token...",
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                                "AWS Signature" -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Access Key ID:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                KNetInputField(
                                                    value = uiState.awsAccessKey,
                                                    onValueChange = onAwsAccessKeyChange,
                                                    placeholder = "AKIAIOSFODNN7EXAMPLE",
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Secret Access Key:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                KNetInputField(
                                                    value = uiState.awsSecretKey,
                                                    onValueChange = onAwsSecretKeyChange,
                                                    placeholder = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY",
                                                    isPassword = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("AWS Region:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                KNetInputField(
                                                    value = uiState.awsRegion,
                                                    onValueChange = onAwsRegionChange,
                                                    placeholder = "us-east-1",
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Service Name:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                KNetInputField(
                                                    value = uiState.awsService,
                                                    onValueChange = onAwsServiceChange,
                                                    placeholder = "s3",
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                                else -> {
                                    Text("This request does not use authentication.", color = KNetColors.TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    activeTab.startsWith("Headers") -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            val enabledCount = request.headers.count { it.isEnabled }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("HTTP Request Headers ($enabledCount active)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                                            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                                            .clickable { onRestoreDefaultHeaders() }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text("↺ Restore Auto Headers", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(KNetColors.ActiveBlue.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .border(1.dp, KNetColors.ActiveBlue, RoundedCornerShape(4.dp))
                                            .clickable { onAddHeader() }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text("+ Add Header", color = KNetColors.ActiveBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Relaxed Single Outer Table Container with Vertical Scroll
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .border(1.dp, KNetColors.BorderDark.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                                    // Grid Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(KNetColors.FieldDark.copy(alpha = 0.6f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.width(22.dp))
                                        Text("Key", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                        Text("Value", color = KNetColors.TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                                        Box(modifier = Modifier.width(22.dp))
                                    }

                                    HorizontalDivider(thickness = 1.dp, color = KNetColors.BorderDark.copy(alpha = 0.5f))

                                    if (request.headers.isEmpty()) {
                                        Text("No headers. Click '+ Add Header' or '↺ Restore Auto Headers'.", color = KNetColors.TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                                    } else {
                                        request.headers.forEachIndexed { index, header ->
                                            if (index > 0) {
                                                HorizontalDivider(thickness = 0.5.dp, color = KNetColors.BorderDark.copy(alpha = 0.3f))
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(32.dp)
                                                    .padding(horizontal = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Borderless Checkbox Icon Toggle
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { onToggleHeader(header.key) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Toggle",
                                                        tint = if (header.isEnabled) KNetColors.ActiveBlue else KNetColors.TextSecondary.copy(alpha = 0.3f),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(6.dp))

                                                // Editable Key Field
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    var keyTf by remember(header.key) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(header.key, selection = androidx.compose.ui.text.TextRange(header.key.length))) }
                                                    TableCellTextField(
                                                        value = keyTf,
                                                        onValueChange = { newKey ->
                                                            keyTf = newKey
                                                            onUpdateHeaderKey(header.key, newKey.text)
                                                        },
                                                        placeholder = "Key",
                                                        textColor = if (header.isEnabled) Color.White else KNetColors.TextSecondary,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (header.isAuto) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(KNetColors.ActiveBlue.copy(alpha = 0.12f), RoundedCornerShape(3.dp))
                                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                                        ) {
                                                            Text("AUTO", color = KNetColors.ActiveBlue, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // Editable Value Field with Placeholder Hint
                                                var valTf by remember(header.key, header.value) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(header.value, selection = androidx.compose.ui.text.TextRange(header.value.length))) }
                                                TableCellTextField(
                                                    value = valTf,
                                                    onValueChange = { newValue ->
                                                        valTf = newValue
                                                        onUpdateHeaderValue(header.key, newValue.text)
                                                    },
                                                    placeholder = if (header.isAuto) "<auto>" else "Value",
                                                    textColor = KNetColors.ActiveBlue,
                                                    modifier = Modifier.weight(1.5f)
                                                )

                                                Spacer(modifier = Modifier.width(8.dp))

                                                // Remove Icon Button
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .clickable { onRemoveHeader(header.key) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Clear, contentDescription = "Remove", tint = KNetColors.TextSecondary, modifier = Modifier.size(12.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }


                    activeTab.contains("Pre-request") -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Language: ", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                    listOf("JavaScript", "Kotlin").forEach { lang ->
                                        val isSelected = (lang == "JavaScript" && uiState.scriptLanguage == com.devuloopers.knet.scriptengine.api.ScriptLanguage.JAVASCRIPT) ||
                                                (lang == "Kotlin" && uiState.scriptLanguage == com.devuloopers.knet.scriptengine.api.ScriptLanguage.KOTLIN)
                                        Box(
                                            modifier = Modifier
                                                .background(if (isSelected) KNetColors.ActiveBlue else KNetColors.FieldDark, RoundedCornerShape(3.dp))
                                                .border(1.dp, if (isSelected) KNetColors.ActiveBlue else KNetColors.BorderDark, RoundedCornerShape(3.dp))
                                                .clickable {
                                                    onScriptLanguageChange(if (lang == "JavaScript") com.devuloopers.knet.scriptengine.api.ScriptLanguage.JAVASCRIPT else com.devuloopers.knet.scriptengine.api.ScriptLanguage.KOTLIN)
                                                }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text(lang, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    com.devuloopers.knet.scriptengine.snippets.SnippetRegistry.SNIPPETS.forEach { snip ->
                                        Box(
                                            modifier = Modifier
                                                .background(KNetColors.FieldDark, RoundedCornerShape(3.dp))
                                                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(3.dp))
                                                .clickable {
                                                    val codeToInsert = com.devuloopers.knet.scriptengine.snippets.SnippetRegistry.getCode(snip, uiState.scriptLanguage)
                                                    val newCode = if (uiState.preRequestScript.isBlank()) codeToInsert else "${uiState.preRequestScript}\n\n$codeToInsert"
                                                    onPreRequestScriptChange(newCode)
                                                }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text("+ ${snip.title}", color = KNetColors.ActiveBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            CodeEditorWidget(
                                code = uiState.preRequestScript,
                                onCodeChange = onPreRequestScriptChange,
                                placeholder = "// Enter pre-request script (e.g. env[\"timestamp\"] = ...)...",
                                textColor = Color(0xFFA855F7),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )

                            // Live Pre-execution Security Diagnostic Banner
                            val preSanitization = remember(uiState.preRequestScript) { com.devuloopers.knet.scriptengine.sandbox.ScriptSanitizer.validate(uiState.preRequestScript) }
                            if (!preSanitization.isValid && preSanitization.errorMessage != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "🔴 Line ${preSanitization.line ?: 1}: ${preSanitization.errorMessage}",
                                        color = Color(0xFFEF4444),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    activeTab == "Tests" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Language: ", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                    listOf("JavaScript", "Kotlin").forEach { lang ->
                                        val isSelected = (lang == "JavaScript" && uiState.scriptLanguage == com.devuloopers.knet.scriptengine.api.ScriptLanguage.JAVASCRIPT) ||
                                                (lang == "Kotlin" && uiState.scriptLanguage == com.devuloopers.knet.scriptengine.api.ScriptLanguage.KOTLIN)
                                        Box(
                                            modifier = Modifier
                                                .background(if (isSelected) KNetColors.ActiveBlue else KNetColors.FieldDark, RoundedCornerShape(3.dp))
                                                .border(1.dp, if (isSelected) KNetColors.ActiveBlue else KNetColors.BorderDark, RoundedCornerShape(3.dp))
                                                .clickable {
                                                    onScriptLanguageChange(if (lang == "JavaScript") com.devuloopers.knet.scriptengine.api.ScriptLanguage.JAVASCRIPT else com.devuloopers.knet.scriptengine.api.ScriptLanguage.KOTLIN)
                                                }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text(lang, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    com.devuloopers.knet.scriptengine.snippets.SnippetRegistry.SNIPPETS.forEach { snip ->
                                        Box(
                                            modifier = Modifier
                                                .background(KNetColors.FieldDark, RoundedCornerShape(3.dp))
                                                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(3.dp))
                                                .clickable {
                                                    val codeToInsert = com.devuloopers.knet.scriptengine.snippets.SnippetRegistry.getCode(snip, uiState.scriptLanguage)
                                                    val newCode = if (uiState.testScript.isBlank()) codeToInsert else "${uiState.testScript}\n\n$codeToInsert"
                                                    onTestScriptChange(newCode)
                                                }
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text("+ ${snip.title}", color = KNetColors.ActiveBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            CodeEditorWidget(
                                code = uiState.testScript,
                                onCodeChange = onTestScriptChange,
                                placeholder = "// Enter test script assertions...",
                                textColor = Color(0xFFF59E0B),
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            )

                            // Live Pre-execution Security Diagnostic Banner for Test Scripts
                            val testSanitization = remember(uiState.testScript) { com.devuloopers.knet.scriptengine.sandbox.ScriptSanitizer.validate(uiState.testScript) }
                            if (!testSanitization.isValid && testSanitization.errorMessage != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFEF4444).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "🔴 Line ${testSanitization.line ?: 1}: ${testSanitization.errorMessage}",
                                        color = Color(0xFFEF4444),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}

// ─────────────────────────────────────────────────────────────────────────────
// Right Column — Response & Test Assertions Panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResponseTestPanel(
    request: SavedApiRequest,
    activeTab: String,
    latestResult: ExecutionResult? = null,
    testResults: List<com.devuloopers.knet.domain.apistudio.model.TestAssertionResult> = emptyList(),
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val respTabs = listOf("Body", "Headers", "Cookies", "Tests")

    Box(
        modifier = modifier
            .background(KNetColors.SurfaceDark, RoundedCornerShape(8.dp))
            .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Status Code Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (latestResult != null) {
                        val statusText = "${latestResult.statusCode} ${latestResult.statusText}"
                        val statusColor = when {
                            latestResult.isSuccess -> KNetColors.SuccessGreen
                            latestResult.statusCode in 400..499 -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                        val latencyText = "${latestResult.latencyMs} ms"
                        val sizeText = "${latestResult.responseSizeBytes} B"

                        Box(
                            modifier = Modifier
                                .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .border(1.dp, statusColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(statusText, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("•  $latencyText  •  $sizeText", color = KNetColors.TextSecondary, fontSize = 11.sp)
                    } else {
                        Text("No response yet", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = KNetColors.TextSecondary, modifier = Modifier.size(14.dp).clickable { })
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-tabs (Body | Headers | Cookies | Tests)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                respTabs.forEach { tabName ->
                    val isTabActive = tabName == activeTab
                    Box(
                        modifier = Modifier
                            .background(
                                if (isTabActive) KNetColors.FieldDark else Color.Transparent,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onTabSelected(tabName) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tabName,
                                color = if (isTabActive) Color.White else KNetColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Medium
                            )
                            val listToCount = testResults.ifEmpty { request.testResults }
                            if (tabName == "Tests" && listToCount.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(modifier = Modifier.size(6.dp).background(KNetColors.SuccessGreen, CircleShape))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Dynamic Response Viewer (Body / Headers / Cookies / Tests)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f)
                    .background(KNetColors.BackgroundDark, RoundedCornerShape(6.dp))
                    .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                when (activeTab) {
                    "Headers" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val headers = latestResult?.headers ?: emptyMap()
                            if (headers.isEmpty()) {
                                Text("No response headers received yet", color = KNetColors.TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
                            } else {
                                headers.forEach { (k, v) ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(k, color = KNetColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Text(v, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                    "Tests" -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        ) {
                            val activeTestResults = testResults.ifEmpty { request.testResults }
                            if (activeTestResults.isEmpty()) {
                                Text("No test assertions evaluated yet. Click 'Send Request' to run test scripts.", color = KNetColors.TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
                            } else {
                                val passCount = activeTestResults.count { it.passed }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "TEST ASSERTION RESULTS ($passCount/${activeTestResults.size} PASSED)",
                                        color = if (passCount == activeTestResults.size) KNetColors.SuccessGreen else Color(0xFFF59E0B),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                activeTestResults.forEach { test ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (test.passed) KNetColors.SuccessGreen.copy(alpha = 0.1f) else Color(0xFFEF4444).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .border(1.dp, if (test.passed) KNetColors.SuccessGreen.copy(alpha = 0.3f) else Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (test.passed) "✔" else "✖",
                                                color = if (test.passed) KNetColors.SuccessGreen else Color(0xFFEF4444),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = test.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Text(
                                            text = if (test.passed) "PASS" else "FAIL",
                                            color = if (test.passed) KNetColors.SuccessGreen else Color(0xFFEF4444),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val bodyText = when {
                            latestResult?.errorMessage != null -> "Error: ${latestResult.errorMessage}"
                            latestResult?.responseBody?.isNotBlank() == true -> latestResult.responseBody
                            else -> "No response payload. Enter a URL and click 'Send Request'."
                        }
                        Text(
                            text = bodyText,
                            color = if (latestResult?.isSuccess == false) Color(0xFFEF4444) else if (latestResult == null) KNetColors.TextSecondary.copy(alpha = 0.6f) else Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Test Assertion Results Section
            if (request.testResults.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val passCount = request.testResults.count { it.passed }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TEST RESULTS ($passCount/${request.testResults.size} PASSED)",
                            color = KNetColors.TextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = KNetColors.SuccessGreen, modifier = Modifier.size(14.dp))
                    }

                    request.testResults.forEach { test ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(KNetColors.BackgroundDark, RoundedCornerShape(4.dp))
                                .border(1.dp, if (test.passed) KNetColors.SuccessGreen.copy(alpha = 0.3f) else Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(if (test.passed) KNetColors.SuccessGreen.copy(alpha = 0.2f) else Color(0xFFEF4444).copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(if (test.passed) "PASS" else "FAIL", color = if (test.passed) KNetColors.SuccessGreen else Color(0xFFEF4444), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(test.name, color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Action: Export Collection Dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = KNetColors.TextSecondary, modifier = Modifier.size(14.dp).clickable { })
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = KNetColors.TextSecondary, modifier = Modifier.size(14.dp).clickable { })
                }

                Box(
                    modifier = Modifier
                        .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .clickable { }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Export Collection", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = KNetColors.TextSecondary, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Compatibility Extensions & Code Editor Bridge Component
// ─────────────────────────────────────────────────────────────────────────────

private val com.devuloopers.knet.domain.apistudio.model.SavedApiRequest.bodyType: String
    get() = body.type

private val com.devuloopers.knet.domain.apistudio.model.SavedApiRequest.bodyPayload: String
    get() = body.content

private val com.devuloopers.knet.domain.apistudio.model.SavedApiRequest.preRequestScript: String
    get() = scripts.preRequest

private val com.devuloopers.knet.domain.apistudio.model.SavedApiRequest.testScript: String
    get() = scripts.test

private val com.devuloopers.knet.domain.apistudio.model.SavedApiRequest.scriptLanguage: com.devuloopers.knet.scriptengine.api.ScriptLanguage
    get() = scripts.language

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.authType: String
    get() = (selectedRequest ?: draftRequest).auth.type

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.authToken: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth.Bearer -> a.token
        is com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth.OAuth2 -> a.token
        else -> ""
    }

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.authUsername: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth.Basic -> a.username
        else -> ""
    }

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.authPassword: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth.Basic -> a.password
        else -> ""
    }

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.apiKeyName: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth.ApiKey -> a.name
        else -> "X-API-Key"
    }

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.apiKeyValue: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth.ApiKey -> a.value
        else -> ""
    }

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.apiKeyLocation: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth.ApiKey -> a.location
        else -> "Header"
    }

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.oauthHeaderPrefix: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth.OAuth2 -> a.headerPrefix
        else -> "Bearer"
    }

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.awsAccessKey: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth.AwsSignature -> a.accessKey
        else -> ""
    }

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.awsSecretKey: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth.AwsSignature -> a.secretKey
        else -> ""
    }

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.awsRegion: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth.AwsSignature -> a.region
        else -> "us-east-1"
    }

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.awsService: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth.AwsSignature -> a.service
        else -> "s3"
    }

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.scriptLanguage: com.devuloopers.knet.scriptengine.api.ScriptLanguage
    get() = (selectedRequest ?: draftRequest).scripts.language

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.preRequestScript: String
    get() = (selectedRequest ?: draftRequest).scripts.preRequest

private val com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState.testScript: String
    get() = (selectedRequest ?: draftRequest).scripts.test

@Composable
private fun CodeEditorWidget(
    code: String,
    onCodeChange: (String) -> Unit = {},
    placeholder: String = "",
    textColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    com.devuloopers.knet.editor.KNetCodeEditor(
        code = code,
        mode = com.devuloopers.knet.editor.model.EditorMode.Editable(
            onCodeChange = onCodeChange,
            placeholder = placeholder,
            textColor = textColor
        ),
        modifier = modifier
    )
}

