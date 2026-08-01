package com.devuloopers.knet.ui.desktop.certificate.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.certificate.model.ClientCertificate

/**
 * ClientCertificateDialog provides interactive forms to add/modify client identity records.
 */
@Composable
public fun ClientCertificateDialog(
    onDismiss: () -> Unit,
    onSave: (ClientCertificate) -> Unit,
    modifier: Modifier = Modifier
) {
    var alias by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark)
            .padding(16.dp)
    ) {
        Text(text = "Add Client mTLS Certificate Identity", color = KNetColors.TextPrimary, fontSize = 13.sp)
        TextField(
            value = alias,
            onValueChange = { alias = it },
            label = { Text("Certificate Alias (e.g. github-client)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        TextField(
            value = host,
            onValueChange = { host = it },
            label = { Text("Matching hostname (e.g. *.github.com)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    onSave(ClientCertificate(alias = alias, subject = "CN=$alias", host = host, expiration = "2029-12-31"))
                },
                enabled = alias.isNotEmpty() && host.isNotEmpty()
            ) {
                Text("Add Identity")
            }
        }
    }
}
