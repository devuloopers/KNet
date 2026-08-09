package com.devuloopers.knet.ui.desktop.certificate.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.certificate.model.CaDetails
import com.devuloopers.knet.ui.desktop.certificate.model.CaStatus

@Composable
fun ActiveRootCaCard(
    caDetails: CaDetails,
    caStatus: CaStatus = CaStatus.AVAILABLE,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    val isAvailable = caStatus == CaStatus.AVAILABLE
    val statusText = if (isAvailable) "ACTIVE" else "MISSING"
    val statusBg = if (isAvailable) themeColors.semantic.success.copy(alpha = 0.15f) else themeColors.semantic.error.copy(alpha = 0.15f)
    val statusColor = if (isAvailable) themeColors.semantic.success else themeColors.semantic.error

    KNetSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = themeColors.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, themeColors.border),
        shape = shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Container
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(themeColors.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = "Certificate",
                    tint = themeColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Details
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (caDetails.subject.isNotBlank()) caDetails.subject else "KNet Local Proxy CA",
                        style = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = themeColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    KNetBadge(
                        text = statusText,
                        containerColor = statusBg,
                        contentColor = statusColor
                    )
                }

                if (caDetails.serialNumber.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SN: ${caDetails.serialNumber}",
                        style = typography.labelSmall,
                        color = themeColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
