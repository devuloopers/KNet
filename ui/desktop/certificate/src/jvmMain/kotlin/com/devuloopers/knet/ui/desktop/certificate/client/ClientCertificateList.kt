package com.devuloopers.knet.ui.desktop.certificate.client

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.certificate.model.ClientCertificate

/**
 * ClientCertificateList displays the imported client certificate keys.
 */
@Composable
public fun ClientCertificateList(
    certificates: List<ClientCertificate>,
    selectedCertificate: ClientCertificate?,
    onSelect: (ClientCertificate) -> Unit,
    onDelete: (ClientCertificate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(12.dp)) {
        Text(
            text = "Client mTLS Certificates (${certificates.size})",
            color = KNetColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn {
            items(certificates) { cert ->
                val isSelected = cert.alias == selectedCertificate?.alias
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(cert) }
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = cert.alias,
                        color = if (isSelected) KNetColors.ActiveBlue else KNetColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Expires: ${cert.expiration}",
                        color = KNetColors.TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = KNetColors.ErrorRed,
                        modifier = Modifier.clickable { onDelete(cert) }
                    )
                }
            }
        }
    }
}
