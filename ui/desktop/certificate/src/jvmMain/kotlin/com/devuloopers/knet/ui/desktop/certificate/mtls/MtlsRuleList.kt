package com.devuloopers.knet.ui.desktop.certificate.mtls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.empty.EmptyState
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.application.port.certificate.MtlsRuleSpec

@Composable
fun MtlsRuleList(
    rules: List<MtlsRuleSpec>,
    onRemoveRule: (String) -> Unit,
    onEditRule: (MtlsRuleSpec) -> Unit,
    onToggleEnabled: (MtlsRuleSpec, Boolean) -> Unit,
    actionsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (rules.isEmpty()) {
        EmptyState(
            message = "No mTLS Domain Rules Configured. Add wildcard domain rules (e.g. *.api.internal) to automatically select client certs.",
            modifier = modifier.fillMaxSize()
        )
    } else {
        val listState = rememberLazyListState()
        Box(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(end = 6.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
            items(rules, key = MtlsRuleSpec::ruleName) { rule ->
                MtlsRuleCard(
                    rule = rule,
                    onEdit = { onEditRule(rule) },
                    onToggleEnabled = { enabled -> onToggleEnabled(rule, enabled) },
                    onDelete = { onRemoveRule(rule.ruleName) },
                    actionsEnabled = actionsEnabled,
                )
            }
            }
            KNetVerticalScrollbar(
                lazyListState = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun MtlsRuleCard(
    rule: MtlsRuleSpec,
    onEdit: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onDelete: () -> Unit,
    actionsEnabled: Boolean,
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    KNetSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = themeColors.surfaceVariant,
        border = BorderStroke(1.dp, themeColors.border),
        shape = shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(themeColors.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = null,
                    tint = themeColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.ruleName,
                    style = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = themeColors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = themeColors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = rule.hostPattern,
                        style = typography.bodySmall,
                        color = themeColors.textSecondary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Mapped to Cert: ${rule.certificateAlias}",
                    style = typography.labelSmall,
                    color = themeColors.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                KNetSwitch(
                    checked = rule.enabled,
                    onCheckedChange = onToggleEnabled,
                    enabled = actionsEnabled,
                )
                Spacer(modifier = Modifier.width(6.dp))
                KNetIconButton(
                    onClick = onEdit,
                    icon = Icons.Default.Edit,
                    contentDescription = "Edit ${rule.ruleName}",
                    tint = themeColors.textSecondary,
                    size = 32.dp,
                    iconSize = 18.dp,
                    enabled = actionsEnabled,
                )
                KNetIconButton(
                    onClick = onDelete,
                    icon = Icons.Default.Delete,
                    contentDescription = "Delete ${rule.ruleName}",
                    tint = themeColors.semantic.error,
                    size = 32.dp,
                    iconSize = 18.dp,
                    enabled = actionsEnabled,
                )
            }
        }
    }
}
