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

/**
 * ImportCertificateDialog prompts user to select a PFX/PKCS12 keystore file and input password parameters.
 */
@Composable
public fun ImportCertificateDialog(
    onDismiss: () -> Unit,
    onImport: (path: String, alias: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var filePath by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark)
            .padding(16.dp)
    ) {
        Text(text = "Import Client Keystore (PKCS12)", color = KNetColors.TextPrimary, fontSize = 13.sp)
        TextField(
            value = filePath,
            onValueChange = { filePath = it },
            label = { Text("Local absolute path to PFX/PKCS12 file") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        TextField(
            value = alias,
            onValueChange = { alias = it },
            label = { Text("Certificate Alias identity") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = { onImport(filePath, alias) },
                enabled = filePath.isNotEmpty() && alias.isNotEmpty()
            ) {
                Text("Import")
            }
        }
    }
}
