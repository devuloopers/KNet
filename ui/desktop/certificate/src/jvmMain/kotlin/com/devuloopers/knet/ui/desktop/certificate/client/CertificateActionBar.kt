package com.devuloopers.knet.ui.desktop.certificate.client

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.input.KNetSearchField
import com.devuloopers.knet.ui.desktop.certificate.model.CertificateTab

@Composable
fun CertificateActionBar(
    activeTab: CertificateTab,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onImportClick: () -> Unit,
    onAddRuleClick: () -> Unit,
    onRefreshClick: () -> Unit,
    showTrustAction: Boolean = false,
    onTrustClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val showButtonLabels = maxWidth >= 720.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KNetSearchField(
                query = searchQuery,
                onQueryChange = onSearchChange,
                placeholder = if (activeTab == CertificateTab.CLIENT_CERTS) {
                    "Filter certificates..."
                } else {
                    "Filter mTLS rules..."
                },
                modifier = Modifier
                    .widthIn(min = 160.dp, max = 360.dp)
                    .weight(1f, fill = false),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showTrustAction) {
                    KNetButton(
                        onClick = onTrustClick,
                        variant = ButtonVariant.Secondary,
                        size = ButtonSize.Standard,
                        enabled = enabled,
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = "Trust and certificate authority",
                            modifier = Modifier.size(18.dp),
                        )
                        if (showButtonLabels) {
                            Spacer(Modifier.width(6.dp))
                            Text("Trust & CA", maxLines = 1, softWrap = false)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                KNetButton(
                    onClick = onRefreshClick,
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Standard,
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (activeTab == CertificateTab.CLIENT_CERTS) {
                    KNetButton(
                        onClick = onImportClick,
                        variant = ButtonVariant.Primary,
                        enabled = enabled,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Import client certificate",
                            modifier = Modifier.size(16.dp)
                        )
                        if (showButtonLabels) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Import Client Cert",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                } else {
                    KNetButton(
                        onClick = onAddRuleClick,
                        variant = ButtonVariant.Primary,
                        enabled = enabled,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add mTLS rule", modifier = Modifier.size(18.dp))
                        if (showButtonLabels) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add mTLS Rule",
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
