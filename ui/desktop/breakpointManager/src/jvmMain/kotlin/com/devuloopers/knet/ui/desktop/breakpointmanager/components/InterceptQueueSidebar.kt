package com.devuloopers.knet.ui.desktop.breakpointmanager.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.application.port.breakpoint.PendingBreakpoint
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.traffic.model.absoluteUrl
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.time.KNetDateTime

/**
 * Sidebar component rendered on the left side of the Live Intercept Drawer when multiple transactions are queued.
 * Displays vertically scrollable queue cards, selection state, individual drop buttons, and a bulk "Drop All" action.
 *
 * @param events The full list of active in-flight suspended transactions.
 * @param selectedEventId The unique ID of the currently focused transaction in the editor.
 * @param onSelectEvent Callback invoked when the user clicks a transaction card to focus it.
 * @param onDropItem Callback invoked when the user clicks the individual drop button on a transaction card.
 * @param onDropAll Callback invoked when the user clicks the bulk "Drop All" button.
 * @param modifier Optional layout modifier.
 */
@Composable
fun InterceptQueueSidebar(
    events: List<PendingBreakpoint>,
    selectedEventId: String?,
    onSelectEvent: (String) -> Unit,
    onDropItem: (String) -> Unit,
    onDropAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(260.dp)
            .background(themeColors.surface)
            .border(width = 1.dp, color = themeColors.border)
    ) {
        // Queue Header Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeColors.surfaceVariant)
                .border(width = 1.dp, color = themeColors.border)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "QUEUE",
                    style = typography.caption.copy(
                        color = themeColors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )

                Box(
                    modifier = Modifier
                        .background(themeColors.semantic.warning.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .border(1.dp, themeColors.semantic.warning, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${events.size}",
                        style = typography.codeSmall.copy(
                            color = themeColors.semantic.warning,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            KNetButton(
                onClick = onDropAll,
                variant = ButtonVariant.Secondary,
                modifier = Modifier.height(26.dp)
            ) {
                Text(
                    text = "DROP ALL",
                    style = typography.caption.copy(
                        color = themeColors.semantic.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp
                    )
                )
            }
        }

        // Scrollable Queue Cards
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items = events,
                key = { it.id }
            ) { item ->
                InterceptQueueItemCard(
                    item = item,
                    isSelected = item.id == selectedEventId,
                    onSelect = { onSelectEvent(item.id) },
                    onDrop = { onDropItem(item.id) },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

/**
 * Individual queue item card representing a single in-flight suspended transaction.
 */
@Composable
private fun InterceptQueueItemCard(
    item: PendingBreakpoint,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onDrop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    val isRequestPhase = item.candidate.phase == BreakpointPhase.REQUEST
    val phaseColor = if (isRequestPhase) themeColors.semantic.info else themeColors.semantic.success
    val phaseLabel = if (isRequestPhase) "REQ" else "RESP"

    val backgroundColor = if (isSelected) {
        themeColors.surfaceVariant
    } else {
        themeColors.surface
    }

    val borderColor = if (isSelected) {
        themeColors.semantic.warning
    } else {
        themeColors.border
    }

    // Determine specialized protocol/content badge
    val contentTypeHeader = item.candidate.response?.head?.headers
        ?.firstOrNull { it.name.value.equals("Content-Type", ignoreCase = true) }?.value
        ?: item.candidate.request.head.headers
            .firstOrNull { it.name.value.equals("Content-Type", ignoreCase = true) }?.value

    val (protocolBadge, protocolColor) = when {
        contentTypeHeader?.contains("json", ignoreCase = true) == true -> "JSON" to themeColors.semantic.info
        contentTypeHeader?.contains("xml", ignoreCase = true) == true -> "XML" to themeColors.semantic.warning
        contentTypeHeader?.contains("form", ignoreCase = true) == true -> "FORM" to Color(0xFFFAB387)
        else -> null to themeColors.textSecondary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .border(width = if (isSelected) 1.5.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(6.dp))
            .clickable(onClick = onSelect)
            .handCursor()
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Row 1: Phase Badge, Method, Protocol Badge, and Drop [x] Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .background(phaseColor.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
                            .border(1.dp, phaseColor, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = phaseLabel,
                            style = typography.codeSmall.copy(
                                color = phaseColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Text(
                        text = item.candidate.request.head.method.token,
                        style = typography.codeSmall.copy(
                            color = themeColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        softWrap = false
                    )

                    if (protocolBadge != null) {
                        Box(
                            modifier = Modifier
                                .background(protocolColor.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
                                .border(1.dp, protocolColor.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = protocolBadge,
                                style = typography.codeSmall.copy(
                                    color = protocolColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onDrop,
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Drop Transaction",
                        tint = themeColors.textSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Row 2: Target Path & Operation (Clean URI without query parameters)
            Text(
                text = extractDisplayPath(item),
                style = typography.codeSmall.copy(
                    color = if (isSelected) themeColors.textPrimary else themeColors.textSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            // Row 3: Timestamp
            Text(
                text = formatTimestamp(item.candidate.startedAtEpochMillis),
                style = typography.caption.copy(
                    color = themeColors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
            )
        }
    }
}

/**
 * Extracts display path/URL: formats GraphQL operation names, and shows full URL for standard HTTP requests.
 */
private fun extractDisplayPath(item: PendingBreakpoint): String = item.candidate.request.absoluteUrl()

/**
 * Formats epoch millisecond timestamp to standard human-readable time string.
 */
private fun formatTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "Just now"
    return KNetDateTime.time(timestamp)
}
