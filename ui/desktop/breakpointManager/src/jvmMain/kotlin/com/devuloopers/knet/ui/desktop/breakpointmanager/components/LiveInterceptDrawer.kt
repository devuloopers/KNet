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
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.InterceptedTransaction
import com.devuloopers.knet.domain.util.decodeBodyToText
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanel
import com.devuloopers.knet.ui.desktop.httppanel.editor.RequestEditorPanelActions
import com.devuloopers.knet.ui.desktop.httppanel.editor.ResponseEditorPanel
import com.devuloopers.knet.ui.desktop.httppanel.editor.ResponseEditorPanelActions
import com.devuloopers.knet.ui.desktop.httppanel.model.InspectorSubTab
import com.devuloopers.knet.ui.desktop.httppanel.model.RequestBodyState
import com.devuloopers.knet.ui.desktop.httppanel.model.ResponseBodyState

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

        var editedReqHeaders by remember(event.id) {
            mutableStateOf(event.request.headers)
        }
        var reqBodyState by remember(event.id) {
            val rawBodyText = decodeBodyToText(event.request.body, event.request.headers)
            mutableStateOf(RequestBodyState.fromPayload(event.request.headers, rawBodyText))
        }
        var activeReqSubTab by remember(event.id) {
            mutableStateOf(InspectorSubTab.BODY)
        }

        var editedStatusCode by remember(event.id) {
            mutableStateOf(event.response?.statusCode ?: 200)
        }
        var editedStatusText by remember(event.id) {
            mutableStateOf(event.response?.statusText ?: "OK")
        }
        var editedRespHeaders by remember(event.id) {
            mutableStateOf(event.response?.headers ?: emptyList())
        }
        var respBodyState by remember(event.id) {
            val rawBodyText = decodeBodyToText(event.response?.body, event.response?.headers ?: emptyList())
            mutableStateOf(ResponseBodyState.fromPayload(event.response?.headers ?: emptyList(), rawBodyText))
        }
        var activeRespSubTab by remember(event.id) {
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

                        // Intercept Phase Badge
                        val isRequestPhase = event.phase == BreakpointPhase.REQUEST
                        val phaseColor = if (isRequestPhase) themeColors.semantic.info else themeColors.semantic.success
                        val phaseLabel = if (isRequestPhase) "REQUEST INTERCEPT" else "RESPONSE INTERCEPT"

                        Box(
                            modifier = Modifier
                                .background(phaseColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .border(1.dp, phaseColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = phaseLabel,
                                style = typography.codeSmall.copy(
                                    color = phaseColor,
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
                    // Dynamic Forward Button
                    val isRequestPhase = event.phase == BreakpointPhase.REQUEST
                    val forwardLabel = if (isRequestPhase) "FORWARD REQUEST" else "FORWARD RESPONSE"

                    KNetButton(
                        onClick = {
                            if (isRequestPhase) {
                                val headersToForward = editedReqHeaders.filterNot {
                                    it.first.equals("Content-Encoding", ignoreCase = true)
                                }
                                val modifiedReq = HttpRequest(
                                    id = event.request.id,
                                    method = event.request.method,
                                    url = event.request.url,
                                    protocol = event.request.protocol,
                                    headers = headersToForward,
                                    body = reqBodyState.payloadText.encodeToByteArray(),
                                    timestamp = event.request.timestamp,
                                    isIntercepted = true,
                                    matchedRuleId = event.request.matchedRuleId
                                )
                                onForwardRequest(modifiedReq)
                            } else {
                                val headersToForward = editedRespHeaders.filterNot {
                                    it.first.equals("Content-Encoding", ignoreCase = true)
                                }
                                val modifiedResp = HttpResponse(
                                    statusCode = editedStatusCode,
                                    statusText = editedStatusText,
                                    headers = headersToForward,
                                    body = respBodyState.payloadText.encodeToByteArray(),
                                    timestamp = event.response?.timestamp ?: System.currentTimeMillis()
                                )
                                onForwardResponse(modifiedResp)
                            }
                        },
                        variant = ButtonVariant.Primary
                    ) {
                        Text(text = forwardLabel, style = typography.caption.copy(fontWeight = FontWeight.Bold))
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
                            bodyState = reqBodyState,
                            headers = editedReqHeaders,
                            activeSubTab = activeReqSubTab,
                            actions = RequestEditorPanelActions(
                                onBodyStateChanged = { reqBodyState = it },
                                onHeadersChanged = { pairs ->
                                    editedReqHeaders = pairs
                                },
                                onSubTabSelected = { activeReqSubTab = it }
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ResponseEditorPanel(
                            statusCode = editedStatusCode,
                            statusText = editedStatusText,
                            bodyState = respBodyState,
                            headers = editedRespHeaders,
                            activeSubTab = activeRespSubTab,
                            actions = ResponseEditorPanelActions(
                                onStatusCodeChanged = { editedStatusCode = it },
                                onStatusTextChanged = { editedStatusText = it },
                                onBodyStateChanged = { respBodyState = it },
                                onHeadersChanged = { pairs ->
                                    editedRespHeaders = pairs
                                },
                                onSubTabSelected = { activeRespSubTab = it }
                            ),
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
