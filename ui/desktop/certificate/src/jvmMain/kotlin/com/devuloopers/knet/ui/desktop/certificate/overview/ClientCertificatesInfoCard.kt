package com.devuloopers.knet.ui.desktop.certificate.overview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateFileFormat

/**
 * Sidebar info card explaining client certificate usage matching the mockup design.
 */
@Composable
fun ClientCertificatesInfoCard(
    modifier: Modifier = Modifier,
    onLearnMoreClick: () -> Unit = {}
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    KNetSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        color = themeColors.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, themeColors.border.copy(alpha = 0.5f)),
        shape = shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = themeColors.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "About Client Certificates",
                    style = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = themeColors.textPrimary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = CertificateFileFormat.defaultDescription,
                style = typography.labelSmall,
                color = themeColors.textSecondary,
                maxLines = 2,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .clickable(onClick = onLearnMoreClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Learn more",
                    style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = themeColors.accent
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "External Link",
                    tint = themeColors.accent,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
