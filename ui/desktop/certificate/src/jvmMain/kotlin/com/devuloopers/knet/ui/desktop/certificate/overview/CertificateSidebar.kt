package com.devuloopers.knet.ui.desktop.certificate.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.application.port.certificate.CertificateAuthoritySummary
import com.devuloopers.knet.application.port.certificate.CertificateAuthorityStatus
import com.devuloopers.knet.ui.core.components.button.KNetIconButton
import com.devuloopers.knet.ui.core.components.dialog.AlertDialog
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.desktop.certificate.model.HostPlatform
import com.devuloopers.knet.ui.desktop.certificate.model.TrustInstallationState

/**
 * Sidebar dashboard column displaying Root CA status and system trust installer.
 */
@Composable
fun CertificateSidebar(
    caDetails: CertificateAuthoritySummary,
    caStatus: CertificateAuthorityStatus,
    trustState: TrustInstallationState,
    platform: HostPlatform = HostPlatform.current(),
    onInstallTrustClick: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val scrollState = rememberScrollState()
    var showClientIdentityHelp by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(themeColors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(12.dp)
                .padding(end = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SystemTrustHeader(modifier = Modifier.weight(1f))
                onClose?.let { close ->
                    KNetIconButton(
                        onClick = close,
                        icon = Icons.Default.Close,
                        contentDescription = "Close trust panel",
                        size = 30.dp,
                        iconSize = 17.dp,
                        tint = themeColors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = themeColors.border,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = "TRUST STATUS",
                        style = typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = themeColors.textSecondary,
                        maxLines = 1,
                        softWrap = false
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ActiveRootCaCard(
                        caDetails = caDetails,
                        caStatus = caStatus,
                        trustState = trustState
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SystemTrustStatusRow(
                        platform = platform,
                        trustState = trustState,
                        onInstallClicked = onInstallTrustClick
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ClientCertificatesInfoCard(
                        onLearnMoreClick = { showClientIdentityHelp = true },
                    )
                }
            }
        }
        KNetVerticalScrollbar(
            scrollState = scrollState,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }

    if (showClientIdentityHelp) {
        AlertDialog(
            title = "About client identities",
            message = "Client identities are used only for upstream services that require mutual TLS. " +
                "Import an identity containing both its private key and certificate chain, then map it to " +
                "an exact host or wildcard such as *.api.example.com. KNet keeps imported private-key " +
                "material in its owner-only application directory and selects it only for enabled matching rules.",
            onDismissRequest = { showClientIdentityHelp = false },
        )
    }
}
