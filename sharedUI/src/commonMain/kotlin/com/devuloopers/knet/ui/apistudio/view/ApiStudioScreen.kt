package com.devuloopers.knet.ui.apistudio.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
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
import com.devuloopers.knet.domain.apistudio.model.TestAssertionResult
import com.devuloopers.knet.theme.KNetColors
import androidx.compose.runtime.collectAsState
import com.devuloopers.knet.ui.apistudio.viewmodel.ApiStudioViewModel
import com.devuloopers.knet.domain.apistudio.usecase.ExecutionResult
import com.devuloopers.knet.ui.apistudio.view.dialogs.CreateItemDialog
import com.devuloopers.knet.ui.apistudio.view.dialogs.CollectionRunnerModal

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
    val sampleFolders = uiState.collections.firstOrNull()?.folders ?: emptyList()
    val selectedRequest = uiState.selectedRequest ?: (sampleFolders.firstOrNull()?.requests?.firstOrNull() ?: SavedApiRequest("r-0", "Default", HttpMethod.GET, url = "https://httpbin.org/get"))

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
            folders = sampleFolders,
            selectedRequestId = selectedRequest.id,
            searchQuery = uiState.searchQuery,
            onSearchChange = { viewModel.updateSearchQuery(it) },
            onSelectRequest = { req -> viewModel.selectRequest(req) },
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
            activeTab = uiState.activeReqTab,
            isExecuting = uiState.isExecuting,
            onTabSelected = { viewModel.selectReqTab(it) },
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
            // Search & Filter Input Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
                    .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = KNetColors.TextSecondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    if (searchQuery.isEmpty()) {
                        Text("Search collections...", color = KNetColors.TextSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp)
                    )
                }
            }

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
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
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
    activeTab: String,
    isExecuting: Boolean = false,
    onTabSelected: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reqTabs = listOf("Body (JSON)", "Params", "Authorization", "Headers", "Pre-request Script", "Tests")

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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = request.url,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Send Request Primary Button
                Box(
                    modifier = Modifier
                        .background(if (isExecuting) KNetColors.ActiveBlue.copy(alpha = 0.5f) else KNetColors.ActiveBlue, RoundedCornerShape(6.dp))
                        .clickable(enabled = !isExecuting) { onSend() }
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

            // Sub-tabs Row (Body, Params, Auth, Headers, Pre-script, Tests)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 1.dp, color = KNetColors.BorderDark, shape = RoundedCornerShape(0.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reqTabs.forEach { tabName ->
                        val isTabActive = tabName == activeTab
                        Column(
                            modifier = Modifier
                                .clickable { onTabSelected(tabName) }
                                .padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tabName,
                                color = if (isTabActive) Color.White else KNetColors.TextSecondary,
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

            // Dynamic Sub-tab Panel Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(KNetColors.BackgroundDark, RoundedCornerShape(6.dp))
                    .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                when {
                    activeTab.startsWith("Body") -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.padding(end = 12.dp)) {
                                (1..5).forEach { num ->
                                    Text(
                                        text = "$num",
                                        color = KNetColors.TextSecondary.copy(alpha = 0.5f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = request.body.ifBlank { "{\n  \"username\": \"developer@knet.dev\",\n  \"auth_type\": \"bearer\",\n  \"client_id\": \"knet_desktop_v2\"\n}" },
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    activeTab == "Params" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Query Parameters", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().background(KNetColors.FieldDark, RoundedCornerShape(4.dp)).padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Key", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Value", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text("Description", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("page", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                Text("1", color = KNetColors.ActiveBlue, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                Text("Page offset", color = KNetColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("limit", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                Text("20", color = KNetColors.ActiveBlue, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                Text("Max items per page", color = KNetColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    activeTab == "Authorization" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Authentication Configuration", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Type: ", color = KNetColors.TextSecondary, fontSize = 11.sp)
                                Box(
                                    modifier = Modifier
                                        .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Bearer Token", color = KNetColors.ActiveBlue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Column {
                                Text("Token / Secret:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(KNetColors.FieldDark, RoundedCornerShape(4.dp))
                                        .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(4.dp))
                                        .padding(8.dp)
                                ) {
                                    Text("eyJhbGciOiJKV1QiLCJhbGciOi...", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }

                    activeTab.startsWith("Headers") -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("HTTP Request Headers (${request.headers.size.coerceAtLeast(4)})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val headersList = request.headers.ifEmpty {
                                mapOf(
                                    "Content-Type" to "application/json",
                                    "Accept" to "application/json",
                                    "User-Agent" to "KNet-Desktop/2.4.0",
                                    "Cache-Control" to "no-cache"
                                )
                            }
                            headersList.forEach { (key, value) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(key, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                    Text(value, color = KNetColors.ActiveBlue, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1.5f))
                                }
                            }
                        }
                    }

                    activeTab.contains("Pre-request") -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Pre-request Script (Runs before execution)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "// Execute setup logic or set environment variables\npm.environment.set(\"timestamp\", Date.now());\npm.request.headers.add({key: \"X-Timestamp\", value: Date.now()});",
                                color = Color(0xFFA855F7),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    activeTab == "Tests" -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Test Scripts & Assertions", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "pm.test(\"Status code is 200\", function () {\n    pm.response.to.have.status(200);\n});\n\npm.test(\"Response contains access_token\", function () {\n    var jsonData = pm.response.json();\n    pm.expect(jsonData.access_token).to.exist;\n});",
                                color = Color(0xFFF59E0B),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
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
            // Status Code Header Row (200 OK • 124 ms • 1.4 KB)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusText = if (latestResult != null) "${latestResult.statusCode} ${latestResult.statusText}" else "200 OK"
                    val statusColor = when {
                        latestResult == null || latestResult.isSuccess -> KNetColors.SuccessGreen
                        latestResult.statusCode in 400..499 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                    val latencyText = if (latestResult != null) "${latestResult.latencyMs} ms" else "124 ms"
                    val sizeText = if (latestResult != null) "${latestResult.responseSizeBytes} B" else "1.4 KB"

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
                            if (tabName == "Tests") {
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
                            val headers = latestResult?.headers ?: mapOf("Content-Type" to "application/json", "Server" to "KNet-Engine/2.4.0")
                            headers.forEach { (k, v) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(k, color = KNetColors.TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    Text(v, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                    else -> {
                        val bodyText = when {
                            latestResult?.errorMessage != null -> "Error: ${latestResult.errorMessage}"
                            latestResult?.responseBody?.isNotBlank() == true -> latestResult.responseBody
                            else -> "{\n  \"status\": \"success\",\n  \"token_type\": \"Bearer\",\n  \"access_token\": \"ey3JhbGciOiJKV1...\",\n  \"expires_in\": 3600,\n  \"user\": {\n    \"id\": 10294,\n    \"role\": \"admin\"\n  }\n}"
                        }
                        Text(
                            text = bodyText,
                            color = if (latestResult?.isSuccess == false) Color(0xFFEF4444) else Color(0xFF10B981),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Test Assertion Results Section (PASS cards)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TEST RESULTS (2/2 PASSED)",
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
                            .border(1.dp, KNetColors.SuccessGreen.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .background(KNetColors.SuccessGreen.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("PASS", color = KNetColors.SuccessGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(test.name, color = Color.White, fontSize = 11.sp)
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
