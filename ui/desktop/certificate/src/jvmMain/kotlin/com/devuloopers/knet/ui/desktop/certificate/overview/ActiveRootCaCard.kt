package com.devuloopers.knet.ui.desktop.certificate.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
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
import com.devuloopers.knet.ui.core.foundation.time.KNetDateTime
import com.devuloopers.knet.application.port.certificate.CertificateAuthoritySummary
import com.devuloopers.knet.application.port.certificate.CertificateAuthorityStatus
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState
import kotlin.time.Instant

@Composable
fun ActiveRootCaCard(
    caDetails: CertificateAuthoritySummary,
    caStatus: CertificateAuthorityStatus,
    trustState: TrustInstallationState = TrustInstallationState.IDLE,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    val isAvailable = caStatus == CertificateAuthorityStatus.AVAILABLE
    val statusText = when (caStatus) {
        CertificateAuthorityStatus.AVAILABLE -> "ACTIVE"
        CertificateAuthorityStatus.MISSING -> "MISSING"
        CertificateAuthorityStatus.EXPIRED -> "EXPIRED"
        CertificateAuthorityStatus.INVALID -> "INVALID"
    }
    val statusBg = if (isAvailable) themeColors.semantic.success.copy(alpha = 0.15f) else themeColors.semantic.error.copy(alpha = 0.15f)
    val statusColor = if (isAvailable) themeColors.semantic.success else themeColors.semantic.error

    val formattedSerial = caDetails.serialNumber.ifBlank { "Not available" }
    val formattedExpires = caDetails.validUntil.toHumanReadableCertificateDate()

    KNetSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = themeColors.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, themeColors.border),
        shape = shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shield Icon Container
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(statusColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Shield",
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Title & Active Badge
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "KNet Root CA",
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = themeColors.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    KNetBadge(
                        text = statusText,
                        containerColor = statusBg,
                        contentColor = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Serial Number
            Text(
                text = "SN: $formattedSerial",
                style = typography.labelSmall,
                color = themeColors.textSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Expiry
            Text(
                text = "Expires: $formattedExpires",
                style = typography.labelSmall,
                color = themeColors.textSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            if (trustState == TrustInstallationState.INSTALLED) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Trusted",
                        tint = themeColors.semantic.success,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Trusted by this system",
                        style = typography.labelSmall,
                        color = themeColors.semantic.success,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun String.toHumanReadableCertificateDate(): String {
    if (isBlank()) return "Not available"
    return runCatching {
        KNetDateTime.humanDate(Instant.parse(this).toEpochMilliseconds())
    }.getOrDefault(this)
}
