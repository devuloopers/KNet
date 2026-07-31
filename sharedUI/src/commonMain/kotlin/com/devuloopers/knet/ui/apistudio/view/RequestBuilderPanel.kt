package com.devuloopers.knet.ui.apistudio.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.model.isUrlValid
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState
import com.devuloopers.knet.ui.apistudio.view.tabs.AuthorizationTab
import com.devuloopers.knet.ui.apistudio.view.tabs.BodyTab
import com.devuloopers.knet.ui.apistudio.view.tabs.HeadersTab
import com.devuloopers.knet.ui.apistudio.view.tabs.ParamsTab
import com.devuloopers.knet.ui.apistudio.view.tabs.PreRequestScriptTab
import com.devuloopers.knet.ui.apistudio.view.tabs.TestScriptTab
import com.devuloopers.knet.widgets.KNetInputField

/**
 * Middle column of the API Studio screen.
 *
 * Contains the URL bar (method dropdown + URL field + Send button + Save icon),
 * the horizontal tab bar (Body / Params / Authorization / Headers / Pre-request Script / Tests),
 * and the tab content area which delegates to focused single-responsibility composables.
 *
 * All user interactions are surfaced as callbacks so this composable remains
 * stateless with respect to business logic.
 *
 * @param request The currently selected or draft [SavedApiRequest].
 * @param uiState The current [ApiStudioUiState] for reading auth/script values.
 * @param activeTab The currently selected tab label string.
 * @param isExecuting Whether a request is currently being sent (shows spinner).
 * @param onTabSelected Callback when a tab pill is clicked.
 * @param onUrlChange Callback when the URL field text changes.
 * @param onToggleHeader Callback to enable/disable a header row.
 * @param onUpdateHeaderKey Callback to rename a header key.
 * @param onUpdateHeaderValue Callback to change a header value.
 * @param onAddHeader Callback to append a new empty header row.
 * @param onRemoveHeader Callback to remove a header row by key.
 * @param onRestoreDefaultHeaders Callback to restore auto-generated headers.
 * @param onAuthTypeChange Callback when the auth type selection changes.
 * @param onAuthTokenChange Callback when the bearer / OAuth2 token changes.
 * @param onAuthUsernameChange Callback when the Basic Auth username changes.
 * @param onAuthPasswordChange Callback when the Basic Auth password changes.
 * @param onApiKeyNameChange Callback when the API Key header name changes.
 * @param onApiKeyValueChange Callback when the API Key value changes.
 * @param onApiKeyLocationChange Callback when the API Key location changes.
 * @param onOauthHeaderPrefixChange Callback when the OAuth2 header prefix changes.
 * @param onAwsAccessKeyChange Callback when the AWS access key ID changes.
 * @param onAwsSecretKeyChange Callback when the AWS secret key changes.
 * @param onAwsRegionChange Callback when the AWS region changes.
 * @param onAwsServiceChange Callback when the AWS service name changes.
 * @param onScriptLanguageChange Callback when the script language is changed.
 * @param onPreRequestScriptChange Callback when the pre-request script changes.
 * @param onTestScriptChange Callback when the test script changes.
 * @param onBodyChange Callback when the request body content changes.
 * @param onBodyTypeChange Callback when the body type/mode changes.
 * @param onSend Callback to trigger request execution.
 * @param modifier Layout modifier for the panel container.
 */
@Composable
internal fun RequestBuilderPanel(
    request: SavedApiRequest,
    uiState: ApiStudioUiState,
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
    onScriptLanguageChange: (ScriptLanguage) -> Unit = {},
    onPreRequestScriptChange: (String) -> Unit = {},
    onTestScriptChange: (String) -> Unit = {},
    onApplyQuickFix: (com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptQuickFix) -> Unit = {},
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
            UrlBar(
                request = request,
                isUrlValid = isUrlValid,
                isExecuting = isExecuting,
                onUrlChange = onUrlChange,
                onSend = onSend
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-tabs Row
            RequestTabBar(reqTabs = reqTabs, activeTab = activeTab, isUrlValid = isUrlValid, onTabSelected = onTabSelected)

            Spacer(modifier = Modifier.height(12.dp))

            // Tab content area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(KNetColors.BackgroundDark, RoundedCornerShape(6.dp))
                    .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                if (!isUrlValid) {
                    UrlRequiredPlaceholder()
                } else {
                    RequestTabContent(
                        activeTab = activeTab,
                        request = request,
                        uiState = uiState,
                        onUrlChange = onUrlChange,
                        onBodyChange = onBodyChange,
                        onBodyTypeChange = onBodyTypeChange,
                        onToggleHeader = onToggleHeader,
                        onUpdateHeaderKey = onUpdateHeaderKey,
                        onUpdateHeaderValue = onUpdateHeaderValue,
                        onAddHeader = onAddHeader,
                        onRemoveHeader = onRemoveHeader,
                        onRestoreDefaultHeaders = onRestoreDefaultHeaders,
                        onAuthTypeChange = onAuthTypeChange,
                        onAuthTokenChange = onAuthTokenChange,
                        onAuthUsernameChange = onAuthUsernameChange,
                        onAuthPasswordChange = onAuthPasswordChange,
                        onApiKeyNameChange = onApiKeyNameChange,
                        onApiKeyValueChange = onApiKeyValueChange,
                        onApiKeyLocationChange = onApiKeyLocationChange,
                        onOauthHeaderPrefixChange = onOauthHeaderPrefixChange,
                        onAwsAccessKeyChange = onAwsAccessKeyChange,
                        onAwsSecretKeyChange = onAwsSecretKeyChange,
                        onAwsRegionChange = onAwsRegionChange,
                        onAwsServiceChange = onAwsServiceChange,
                        onScriptLanguageChange = onScriptLanguageChange,
                        onPreRequestScriptChange = onPreRequestScriptChange,
                        onTestScriptChange = onTestScriptChange,
                        onApplyQuickFix = onApplyQuickFix
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// URL Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UrlBar(
    request: SavedApiRequest,
    isUrlValid: Boolean,
    isExecuting: Boolean,
    onUrlChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        var methodDropdownExpanded by remember { mutableStateOf(false) }
        var selectedMethod by remember(request) { mutableStateOf(request.method) }
        var customMethodText by remember(request) { mutableStateOf(request.customMethod ?: "CUSTOM") }

        // Method dropdown
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
                        BasicTextField(
                            value = customMethodText,
                            onValueChange = { customMethodText = it.uppercase() },
                            singleLine = true,
                            cursorBrush = SolidColor(Color(selectedMethod.badgeColorHex)),
                            textStyle = TextStyle(color = Color(selectedMethod.badgeColorHex), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                            modifier = Modifier.width(androidx.compose.foundation.layout.IntrinsicSize.Min)
                        )
                    } else {
                        Text(selectedMethod.name, color = Color(selectedMethod.badgeColorHex), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = KNetColors.TextSecondary, modifier = Modifier.size(14.dp))
                }
            }
            DropdownMenu(
                expanded = methodDropdownExpanded,
                onDismissRequest = { methodDropdownExpanded = false },
                modifier = Modifier.background(KNetColors.SurfaceDark).border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp))
            ) {
                HttpMethod.entries.forEach { method ->
                    DropdownMenuItem(
                        text = { Text(method.name, color = Color(method.badgeColorHex), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace) },
                        onClick = { selectedMethod = method; methodDropdownExpanded = false }
                    )
                }
            }
        }

        // URL input
        KNetInputField(value = request.url, onValueChange = onUrlChange, placeholder = "https://api.example.com/v1/resource", fontSize = 12.sp, height = 36.dp, cornerRadius = 6.dp, modifier = Modifier.weight(1f))

        // Animated Send button
        val animatedBgColor by animateColorAsState(
            targetValue = when {
                !isUrlValid -> KNetColors.ActiveBlue.copy(alpha = 0.4f)
                isExecuting -> KNetColors.ActiveBlue.copy(alpha = 0.6f)
                else -> KNetColors.ActiveBlue
            },
            animationSpec = tween(durationMillis = 200),
            label = "SendButtonBgColor"
        )

        Box(
            modifier = Modifier
                .background(animatedBgColor, RoundedCornerShape(6.dp))
                .clickable(enabled = isUrlValid && !isExecuting) { onSend() }
                .animateContentSize(animationSpec = tween(durationMillis = 200))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isExecuting,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(150)))
                        .togetherWith(fadeOut(animationSpec = tween(150)))
                },
                label = "SendButtonContent"
            ) { executing ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (executing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Sending...",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "Send Request",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Save icon button
        Box(modifier = Modifier.background(KNetColors.FieldDark, RoundedCornerShape(6.dp)).border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp)).clickable { }.padding(8.dp)) {
            Icon(Icons.Default.Save, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RequestTabBar(
    reqTabs: List<String>,
    activeTab: String,
    isUrlValid: Boolean,
    onTabSelected: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().border(width = 1.dp, color = KNetColors.BorderDark, shape = RoundedCornerShape(0.dp))) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            reqTabs.forEach { tabName ->
                val isTabActive = tabName == activeTab && isUrlValid
                Column(modifier = Modifier.clickable(enabled = isUrlValid) { onTabSelected(tabName) }.padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = tabName,
                        color = if (isTabActive) Color.White else if (!isUrlValid) KNetColors.TextSecondary.copy(alpha = 0.35f) else KNetColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isTabActive) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(if (isTabActive) KNetColors.ActiveBlue else Color.Transparent))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// "Enter URL first" placeholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UrlRequiredPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🌐", fontSize = 24.sp)
            Text(
                text = "Enter a valid URL above to configure headers, body, and request parameters",
                color = KNetColors.TextSecondary.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tab content switcher
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RequestTabContent(
    activeTab: String,
    request: SavedApiRequest,
    uiState: ApiStudioUiState,
    onUrlChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onBodyTypeChange: (String) -> Unit,
    onToggleHeader: (String) -> Unit,
    onUpdateHeaderKey: (String, String) -> Unit,
    onUpdateHeaderValue: (String, String) -> Unit,
    onAddHeader: () -> Unit,
    onRemoveHeader: (String) -> Unit,
    onRestoreDefaultHeaders: () -> Unit,
    onAuthTypeChange: (String) -> Unit,
    onAuthTokenChange: (String) -> Unit,
    onAuthUsernameChange: (String) -> Unit,
    onAuthPasswordChange: (String) -> Unit,
    onApiKeyNameChange: (String) -> Unit,
    onApiKeyValueChange: (String) -> Unit,
    onApiKeyLocationChange: (String) -> Unit,
    onOauthHeaderPrefixChange: (String) -> Unit,
    onAwsAccessKeyChange: (String) -> Unit,
    onAwsSecretKeyChange: (String) -> Unit,
    onAwsRegionChange: (String) -> Unit,
    onAwsServiceChange: (String) -> Unit,
    onScriptLanguageChange: (ScriptLanguage) -> Unit,
    onPreRequestScriptChange: (String) -> Unit,
    onTestScriptChange: (String) -> Unit,
    onApplyQuickFix: (com.devuloopers.knet.ui.apistudio.scriptanalyzer.model.ScriptQuickFix) -> Unit = {}
) {
    when {
        activeTab.startsWith("Body") -> BodyTab(request = request, onBodyChange = onBodyChange, onBodyTypeChange = onBodyTypeChange)
        activeTab == "Params" -> ParamsTab(request = request, onUrlChange = onUrlChange)
        activeTab == "Authorization" -> AuthorizationTab(
            uiState = uiState,
            onAuthTypeChange = onAuthTypeChange,
            onAuthTokenChange = onAuthTokenChange,
            onAuthUsernameChange = onAuthUsernameChange,
            onAuthPasswordChange = onAuthPasswordChange,
            onApiKeyNameChange = onApiKeyNameChange,
            onApiKeyValueChange = onApiKeyValueChange,
            onApiKeyLocationChange = onApiKeyLocationChange,
            onOauthHeaderPrefixChange = onOauthHeaderPrefixChange,
            onAwsAccessKeyChange = onAwsAccessKeyChange,
            onAwsSecretKeyChange = onAwsSecretKeyChange,
            onAwsRegionChange = onAwsRegionChange,
            onAwsServiceChange = onAwsServiceChange
        )
        activeTab.startsWith("Headers") -> HeadersTab(
            request = request,
            onToggleHeader = onToggleHeader,
            onUpdateHeaderKey = onUpdateHeaderKey,
            onUpdateHeaderValue = onUpdateHeaderValue,
            onAddHeader = onAddHeader,
            onRemoveHeader = onRemoveHeader,
            onRestoreDefaultHeaders = onRestoreDefaultHeaders
        )
        activeTab.contains("Pre-request") -> PreRequestScriptTab(
            uiState = uiState,
            onScriptLanguageChange = onScriptLanguageChange,
            onPreRequestScriptChange = onPreRequestScriptChange,
            onApplyQuickFix = onApplyQuickFix
        )
        activeTab == "Tests" -> TestScriptTab(uiState = uiState, onScriptLanguageChange = onScriptLanguageChange, onTestScriptChange = onTestScriptChange)
    }
}
