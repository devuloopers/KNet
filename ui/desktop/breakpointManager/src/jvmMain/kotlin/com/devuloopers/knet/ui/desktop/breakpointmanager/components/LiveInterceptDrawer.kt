package com.devuloopers.knet.ui.desktop.breakpointmanager.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.clientNetwork.model.HttpRequest
import com.devuloopers.knet.domain.clientNetwork.model.HttpResponse
import com.devuloopers.knet.domain.network.model.NetworkResponseSpec
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.InterceptedTransaction
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanel
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanelActions
import com.devuloopers.knet.ui.desktop.httppanel.model.BodyMode
import com.devuloopers.knet.ui.desktop.httppanel.model.BodyState
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.viewpanels.ResponseViewPanel

/**
 * Slide-out desktop drawer displaying active in-flight suspended HTTP transactions.
 * Reuses `:ui:desktop:httpPanel` request/response editors and provides [FORWARD], [DROP], and [DISABLE RULE] controls.
 */
@Composable
fun LiveInterceptDrawer(
    event: InterceptedTransaction?,
    isVisible: Boolean,
    onForwardRequest: (modifiedRequest: HttpRequest) -> Unit,
    onForwardResponse: (modifiedResponse: HttpResponse) -> Unit,
    onDrop: () -> Unit,
    onDisableRule: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    AnimatedVisibility(
        visible = isVisible && event != null,
        enter = slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth }),
        exit = slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth }),
        modifier = modifier
    ) {
        if (event == null) return@AnimatedVisibility

        var editedHeaders by remember(event.id) {
            mutableStateOf(event.request.headers)
        }
        var bodyState by remember(event.id) {
            val rawBodyText = event.request.body?.decodeToString() ?: ""
            mutableStateOf(BodyState.fromPayload(event.request.headers, rawBodyText))
        }
        var activeSubTab by remember(event.id) {
            mutableStateOf(InspectorSubTab.BODY)
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(620.dp)
                .background(themeColors.surface)
                .border(width = 1.dp, color = themeColors.border)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(themeColors.surfaceVariant)
                        .border(width = 1.dp, color = themeColors.border)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Status Badge
                        Box(
                            modifier = Modifier
                                .background(themeColors.semantic.warning.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, themeColors.semantic.warning, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "PAUSED IN-FLIGHT",
                                style = typography.codeSmall.copy(
                                    color = themeColors.semantic.warning,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }

                        // Method Badge
                        Text(
                            text = event.method,
                            style = typography.codeSmall.copy(
                                color = themeColors.semantic.info,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )

                        // URL String
                        Text(
                            text = event.url,
                            style = typography.bodySmall.copy(color = themeColors.textPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Drawer",
                            tint = themeColors.textSecondary
                        )
                    }
                }

                // Action Control Toolbar ([FORWARD], [DROP], [DISABLE RULE])
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(themeColors.background)
                        .border(width = 1.dp, color = themeColors.border)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Forward Button
                    KNetButton(
                        onClick = {
                            if (event.phase == BreakpointPhase.REQUEST || event.phase == BreakpointPhase.BOTH) {
                                val modifiedReq = HttpRequest(
                                    id = event.request.id,
                                    method = event.request.method,
                                    url = event.request.url,
                                    protocol = event.request.protocol,
                                    headers = editedHeaders,
                                    body = bodyState.payloadText.encodeToByteArray(),
                                    timestamp = event.request.timestamp,
                                    isIntercepted = true,
                                    matchedRuleId = event.request.matchedRuleId
                                )
                                onForwardRequest(modifiedReq)
                            } else {
                                val currentResp = event.response ?: HttpResponse(
                                    statusCode = 200,
                                    statusText = "OK",
                                    headers = emptyList(),
                                    body = null,
                                    timestamp = System.currentTimeMillis()
                                )
                                onForwardResponse(currentResp)
                            }
                        },
                        variant = ButtonVariant.Primary
                    ) {
                        Text(text = "FORWARD", style = typography.caption.copy(fontWeight = FontWeight.Bold))
                    }

                    // Drop Button
                    KNetButton(
                        onClick = onDrop,
                        variant = ButtonVariant.Secondary
                    ) {
                        Text(text = "DROP", style = typography.caption.copy(fontWeight = FontWeight.Bold))
                    }

                    // Disable Rule Button
                    KNetButton(
                        onClick = onDisableRule,
                        variant = ButtonVariant.Secondary
                    ) {
                        Text(text = "DISABLE RULE", style = typography.caption.copy(fontWeight = FontWeight.Bold))
                    }
                }

                // Editor Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (event.phase == BreakpointPhase.REQUEST || event.phase == BreakpointPhase.BOTH) {
                        RequestEditorPanel(
                            bodyState = bodyState,
                            headers = editedHeaders,
                            activeSubTab = activeSubTab,
                            actions = RequestEditorPanelActions(
                                onBodyStateChanged = { bodyState = it },
                                onHeadersChanged = { pairs ->
                                    editedHeaders = pairs
                                },
                                onSubTabSelected = { activeSubTab = it }
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val response = event.response
                        if (response != null) {
                            val respSpec = NetworkResponseSpec(
                                statusCode = response.statusCode,
                                statusText = response.statusText,
                                durationMs = 0L,
                                sizeBytes = (response.body?.size ?: 0).toLong(),
                                responseBody = response.body?.decodeToString() ?: "",
                                headers = response.headers.map { it.first to it.second }
                            )
                            ResponseViewPanel(
                                spec = respSpec,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No Response Payload Intercepted",
                                    style = typography.bodyMedium.copy(color = themeColors.textMuted)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
