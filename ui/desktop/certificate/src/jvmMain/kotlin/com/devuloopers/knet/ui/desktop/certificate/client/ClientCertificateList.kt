package com.devuloopers.knet.ui.desktop.certificate.client

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
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.empty.EmptyState
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.application.port.certificate.ClientCertificateSummary

@Composable
fun ClientCertificateList(
    certificates: List<ClientCertificateSummary>,
    selectedCertificateAlias: String?,
    onSelect: (ClientCertificateSummary) -> Unit,
    onToggleEnabled: (String, Boolean) -> Unit,
    onDelete: (ClientCertificateSummary) -> Unit,
    actionsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (certificates.isEmpty()) {
        EmptyState(
            message = "No Client Certificates Registered. Import a PKCS#12 or PEM certificate to authenticate mTLS connections.",
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
            items(certificates, key = ClientCertificateSummary::alias) { cert ->
                ClientCertificateCard(
                    cert = cert,
                    isSelected = cert.alias == selectedCertificateAlias,
                    onClick = { onSelect(cert) },
                    onToggleEnabled = { onToggleEnabled(cert.alias, !cert.enabled) },
                    onDelete = { onDelete(cert) },
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
private fun ClientCertificateCard(
    cert: ClientCertificateSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit,
    actionsEnabled: Boolean,
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val expirationText = when {
        cert.daysUntilExpiration < 0 -> "Expired ${-cert.daysUntilExpiration} days ago"
        cert.daysUntilExpiration == 0 -> "Expires today"
        else -> "Expires in ${cert.daysUntilExpiration} days"
    }

    val borderColor = if (isSelected) themeColors.accent else themeColors.border
    val backgroundColor = if (isSelected) themeColors.surfaceVariant.copy(alpha = 0.5f) else themeColors.surfaceVariant
    val expiryColor = when {
        cert.daysUntilExpiration > 30 -> themeColors.semantic.success
        cert.daysUntilExpiration > 7 -> themeColors.semantic.warning
        else -> themeColors.semantic.error
    }

    KNetSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
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
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = themeColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = cert.alias,
                        style = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) themeColors.accent else themeColors.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    KNetBadge(
                        text = cert.format.name,
                        containerColor = themeColors.semantic.infoContainer,
                        contentColor = themeColors.semantic.info,
                    )
                }
                
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
                        text = cert.host,
                        style = typography.bodySmall,
                        color = themeColors.textSecondary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = expirationText,
                    style = typography.labelSmall,
                    color = expiryColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                KNetSwitch(
                    checked = cert.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    enabled = actionsEnabled,
                )
                Spacer(modifier = Modifier.width(12.dp))
                KNetIconButton(
                    onClick = onDelete,
                    icon = Icons.Default.Delete,
                    contentDescription = "Delete ${cert.alias}",
                    tint = themeColors.semantic.error,
                    size = 32.dp,
                    iconSize = 18.dp,
                    enabled = actionsEnabled,
                )
            }
        }
    }
}
