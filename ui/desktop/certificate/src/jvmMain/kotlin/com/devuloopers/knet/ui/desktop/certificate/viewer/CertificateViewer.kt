package com.devuloopers.knet.ui.desktop.certificate.viewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.components.inspector.KNetInspectorRow
import com.devuloopers.knet.ui.core.components.panel.PanelHeader
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.application.contract.certificate.ClientCertificateSummary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CertificateViewer(
    certificate: ClientCertificateSummary?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val scrollState = rememberScrollState()

    if (certificate == null) return

    KNetSurface(
        modifier = modifier
            .fillMaxHeight()
            .widthIn(min = 280.dp, max = 360.dp),
        color = themeColors.surfaceVariant,
        border = BorderStroke(1.dp, themeColors.border)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Panel Header
            PanelHeader(
                title = "X.509 Certificate Details",
                actions = {
                    KNetIconButton(
                        onClick = onClose,
                        icon = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = themeColors.textSecondary,
                        size = 30.dp,
                        iconSize = 17.dp,
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
                    .padding(end = 4.dp)
            ) {
                // Header Card
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = certificate.alias,
                            style = typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = themeColors.textPrimary,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        KNetBadge(
                            text = if (certificate.enabled) "ACTIVE" else "DISABLED",
                            containerColor = if (certificate.enabled) themeColors.semantic.success.copy(alpha = 0.15f) else themeColors.surface,
                            contentColor = if (certificate.enabled) themeColors.semantic.success else themeColors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Inspector Rows using KNetInspectorRow
                KNetInspectorRow(label = "Subject DN") {
                    Text(
                        text = certificate.subjectDn.ifBlank { certificate.subject },
                        style = typography.bodySmall,
                        color = themeColors.textPrimary,
                    )
                }

                KNetInspectorRow(label = "Issuer DN") {
                    Text(
                        text = certificate.issuerDn.ifBlank { "Not available" },
                        style = typography.bodySmall,
                        color = themeColors.textPrimary,
                    )
                }

                KNetInspectorRow(label = "Serial Number") {
                    Text(
                        text = certificate.serialNumber.ifBlank { "N/A" },
                        style = typography.bodySmall,
                        color = themeColors.textPrimary,
                        softWrap = true,
                    )
                }

                KNetInspectorRow(label = "Expiration") {
                    Text(
                        text = "${certificate.expiration} (${certificate.daysUntilExpiration} days remaining)",
                        style = typography.bodySmall,
                        color = themeColors.textPrimary,
                        softWrap = true,
                    )
                }

                if (certificate.sanList.isNotEmpty()) {
                    KNetInspectorRow(label = "SANs") {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            certificate.sanList.forEach { san ->
                                KNetBadge(text = san)
                            }
                        }
                    }
                }

                KNetInspectorRow(label = "Public Key") {
                    Text(
                        text = certificate.publicKeyAlgorithm,
                        style = typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = themeColors.textPrimary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (certificate.sha256Fingerprint.isNotBlank()) {
                    KNetInspectorRow(label = "SHA-256") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = certificate.sha256Fingerprint,
                                style = typography.caption,
                                color = themeColors.textSecondary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            KNetCopyButton(textToCopy = certificate.sha256Fingerprint)
                        }
                    }
                }
            }
            KNetVerticalScrollbar(
                scrollState = scrollState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
            }
        }
    }
}
