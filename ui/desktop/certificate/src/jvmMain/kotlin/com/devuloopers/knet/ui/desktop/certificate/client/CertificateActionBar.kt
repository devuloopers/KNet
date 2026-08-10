package com.devuloopers.knet.ui.desktop.certificate.client

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
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
    onRefreshClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Search bar
        KNetSearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            placeholder = "Filter by alias or domain...",
            modifier = Modifier.width(360.dp)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            KNetButton(
                onClick = onRefreshClick,
                variant = ButtonVariant.Secondary,
                size = ButtonSize.Standard
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
                    variant = ButtonVariant.Primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Client Cert")
                }
            } else {
                KNetButton(
                    onClick = onAddRuleClick,
                    variant = ButtonVariant.Primary
                ) {
                    Text("Add mTLS Rule")
                }
            }
        }
    }
}
