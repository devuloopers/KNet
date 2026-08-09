package com.devuloopers.knet.ui.desktop.certificate.client

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            modifier = Modifier.width(320.dp)
        )

        if (activeTab == CertificateTab.CLIENT_CERTS) {
            KNetButton(
                onClick = onImportClick,
                variant = ButtonVariant.Primary
            ) {
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
