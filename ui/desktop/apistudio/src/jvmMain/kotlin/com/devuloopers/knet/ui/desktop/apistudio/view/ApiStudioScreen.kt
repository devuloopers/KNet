package com.devuloopers.knet.ui.desktop.apistudio.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.divider.VerticalDivider
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.split.HorizontalSplitPane
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.editor.RequestPayloadEditor
import com.devuloopers.knet.ui.desktop.apistudio.editor.RequestTabBar
import com.devuloopers.knet.ui.desktop.apistudio.editor.RequestUrlBar
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestTab
import com.devuloopers.knet.ui.desktop.apistudio.response.ResponseInspectorView
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.CollectionsSidebar
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarRequestItem
import com.devuloopers.knet.ui.desktop.apistudio.viewmodel.ApiStudioViewModel

private val mockUnsavedRequests = listOf(
    SidebarRequestItem("u1", "Draft Ping Check", "GET", "https://api.knet.dev/v1/ping"),
    SidebarRequestItem("u2", "Quick Auth Scratch", "POST", "https://auth.knet.dev/login")
)

private val mockCollections = listOf(
    SidebarFolderItem(
        id = "f1",
        name = "Auth API",
        requests = listOf(
            SidebarRequestItem("r1", "Get Token", "POST", "https://api.knet.dev/v1/auth/token"),
            SidebarRequestItem("r2", "Refresh Token", "POST", "https://api.knet.dev/v1/auth/refresh"),
            SidebarRequestItem("r3", "Logout User", "DELETE", "https://api.knet.dev/v1/auth/logout")
        )
    ),
    SidebarFolderItem(
        id = "f2",
        name = "User Management",
        isExpanded = true,
        requests = listOf(
            SidebarRequestItem("r4", "Get User Profile", "GET", "https://api.knet.dev/v1/users/me"),
            SidebarRequestItem("r5", "Create User Account", "POST", "https://api.knet.dev/v1/users/create"),
            SidebarRequestItem("r6", "Update User Settings", "PUT", "https://api.knet.dev/v1/users/settings")
        )
    ),
    SidebarFolderItem(
        id = "f3",
        name = "Payments",
        requests = listOf(
            SidebarRequestItem("r7", "Process Checkout", "POST", "https://api.knet.dev/v1/checkout"),
            SidebarRequestItem("r8", "Get Payment History", "GET", "https://api.knet.dev/v1/payments")
        )
    )
)

/**
 * Top-level API Studio Screen composable hosting Collections Sidebar, Request Editor, and Response Inspector.
 *
 * @param viewModel Optional ViewModel managing state.
 * @param modifier Layout modifier.
 */
@Composable
public fun ApiStudioScreen(
    viewModel: ApiStudioViewModel? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    var selectedEnvironment by remember { mutableStateOf("Production") }
    var selectedRequestId by remember { mutableStateOf<String?>("r5") }
    var tabs by remember {
        mutableStateOf(
            listOf(
                RequestTab("tab_1", "Create User Account", method = "POST", isDirty = true),
                RequestTab("tab_2", "Get User Profile", method = "GET")
            )
        )
    }
    var activeTabId by remember { mutableStateOf("tab_1") }
    var currentMethod by remember { mutableStateOf("POST") }
    var currentUrl by remember { mutableStateOf("https://api.knet.dev/v1/users/create") }
    var bodyPayload by remember { mutableStateOf("") }

    Row(modifier = modifier.fillMaxSize().background(themeColors.surface)) {
        // Left Pane: Collections Sidebar
        CollectionsSidebar(
            unsavedRequests = mockUnsavedRequests,
            collections = mockCollections,
            selectedRequestId = selectedRequestId,
            onRequestSelected = { item ->
                selectedRequestId = item.id
                currentMethod = item.method
                currentUrl = item.url
            }
        )

        VerticalDivider(color = themeColors.border)

        // Center & Right Split Panes
        HorizontalSplitPane(
            firstPane = { paneModifier ->
                // Center Pane: Request Editor
                Column(
                    modifier = paneModifier
                        .fillMaxSize()
                        .background(themeColors.surface)
                ) {
                    RequestUrlBar(
                        method = currentMethod,
                        url = currentUrl,
                        onMethodChanged = { currentMethod = it },
                        onUrlChanged = { currentUrl = it },
                        onSendClicked = {
                            viewModel?.executeRequest()
                        }
                    )

                    HorizontalDivider(color = themeColors.border)

                    RequestPayloadEditor(
                        bodyPayload = bodyPayload,
                        onBodyPayloadChanged = { bodyPayload = it }
                    )
                }
            },
            secondPane = { paneModifier ->
                // Right Pane: Response Inspector
                ResponseInspectorView(
                    statusCode = 200,
                    statusText = "OK",
                    durationMs = 124,
                    sizeBytes = 4966,
                    modifier = paneModifier
                )
            },
            initialSplitRatio = 0.5f
        )
    }
}
