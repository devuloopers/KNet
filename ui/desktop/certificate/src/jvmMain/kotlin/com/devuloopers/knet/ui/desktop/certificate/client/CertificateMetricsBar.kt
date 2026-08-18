package com.devuloopers.knet.ui.desktop.certificate.client

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.application.port.certificate.ClientCertificateSummary
import com.devuloopers.knet.application.port.certificate.MtlsRuleSpec

/**
 * 4-card horizontal metrics bar displaying summary PKI statistics matching the design system.
 */
@Composable
fun CertificateMetricsBar(
    certificates: List<ClientCertificateSummary>,
    mtlsRules: List<MtlsRuleSpec>,
    modifier: Modifier = Modifier
) {
    val totalCerts = certificates.size
    val totalDomains = mtlsRules.map { it.hostPattern }.distinct().size
    val expiringSoon = certificates.count { it.daysUntilExpiration <= 30 }
    val activeCount = certificates.count { it.enabled }
    val metricsScrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(metricsScrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricCard(
            value = totalCerts.toString(),
            title = "Certificates",
            subtitle = "Total imported certificates",
            icon = Icons.Default.WorkspacePremium,
            iconBgColor = Color(0xFF2563EB).copy(alpha = 0.2f),
            iconTintColor = Color(0xFF60A5FA),
            modifier = Modifier.widthIn(min = 160.dp)
        )

        MetricCard(
            value = totalDomains.toString(),
            title = "Domains",
            subtitle = "Domains using mTLS",
            icon = Icons.Default.Language,
            iconBgColor = Color(0xFF7C3AED).copy(alpha = 0.2f),
            iconTintColor = Color(0xFFA78BFA),
            modifier = Modifier.widthIn(min = 160.dp)
        )

        MetricCard(
            value = expiringSoon.toString(),
            title = "Expiring Soon",
            subtitle = "Within 30 days",
            icon = Icons.Default.Event,
            iconBgColor = Color(0xFFD97706).copy(alpha = 0.2f),
            iconTintColor = Color(0xFFFBBF24),
            modifier = Modifier.widthIn(min = 160.dp)
        )

        MetricCard(
            value = activeCount.toString(),
            title = "Active",
            subtitle = "Currently in use",
            icon = Icons.Default.VerifiedUser,
            iconBgColor = Color(0xFF059669).copy(alpha = 0.2f),
            iconTintColor = Color(0xFF34D399),
            modifier = Modifier.widthIn(min = 160.dp)
        )
    }
}

@Composable
private fun MetricCard(
    value: String,
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconTintColor: Color,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    KNetSurface(
        modifier = modifier,
        color = themeColors.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, themeColors.border),
        shape = shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(iconBgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTintColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = themeColors.textPrimary,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = themeColors.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = typography.labelSmall,
                    color = themeColors.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
