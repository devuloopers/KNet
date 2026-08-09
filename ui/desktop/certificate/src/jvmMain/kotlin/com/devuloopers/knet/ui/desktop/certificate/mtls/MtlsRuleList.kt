package com.devuloopers.knet.ui.desktop.certificate.mtls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.empty.EmptyState
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.certificate.model.MtlsRule

@Composable
fun MtlsRuleList(
    rules: List<MtlsRule>,
    onRemoveRule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rules.isEmpty()) {
        EmptyState(
            message = "No mTLS Domain Rules Configured. Add wildcard domain rules (e.g. *.api.internal) to automatically select client certs.",
            modifier = modifier.fillMaxSize()
        )
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(rules) { rule ->
                MtlsRuleCard(
                    rule = rule,
                    onDelete = { onRemoveRule(rule.ruleName) }
                )
            }
        }
    }
}

@Composable
private fun MtlsRuleCard(
    rule: MtlsRule,
    onDelete: () -> Unit
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
                    color = themeColors.textPrimary
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
                        color = themeColors.textSecondary
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Mapped to Cert: ${rule.certificateAlias}",
                    style = typography.labelSmall,
                    color = themeColors.textSecondary
                )
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                KNetSwitch(
                    checked = rule.enabled,
                    onCheckedChange = { /* read-only or future toggle */ }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = themeColors.semantic.error,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onDelete)
                )
            }
        }
    }
}
