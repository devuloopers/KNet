package com.devuloopers.knet.ui.desktop.certificate.client

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateFileFormat

/**
 * Hero empty state component matching the exact visual design of the mockup.
 */
@Composable
fun CertificateEmptyState(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    KNetSurface(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        color = themeColors.surfaceVariant.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, themeColors.border.copy(alpha = 0.6f)),
        shape = shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Shield Illustration Container with (+) overlay
            Box(
                modifier = Modifier.size(96.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer circle background
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(themeColors.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = themeColors.textSecondary.copy(alpha = 0.8f),
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Plus badge overlay at bottom-right
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 8.dp)
                        .size(24.dp)
                        .background(Color(0xFF2563EB), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "No Client Certificates Yet",
                style = typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = themeColors.textPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = CertificateFileFormat.defaultDescription,
                style = typography.bodySmall,
                color = themeColors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            KNetButton(
                onClick = onImportClick,
                variant = ButtonVariant.Primary,
                size = ButtonSize.Standard
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Your First Certificate")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = CertificateFileFormat.supportsFooterLabel,
                style = typography.labelSmall,
                color = themeColors.textSecondary.copy(alpha = 0.7f)
            )
        }
    }
}
