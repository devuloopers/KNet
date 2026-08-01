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
 * ExportCertificateDialog wizard prompting for absolute target path to write public PEM/DER blocks.
 */
@Composable
public fun ExportCertificateDialog(
    alias: String,
    onDismiss: () -> Unit,
    onExport: (alias: String, path: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var destinationPath by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark)
            .padding(16.dp)
    ) {
        Text(text = "Export Certificate '$alias' to PEM", color = KNetColors.TextPrimary, fontSize = 13.sp)
        TextField(
            value = destinationPath,
            onValueChange = { destinationPath = it },
            label = { Text("Absolute destination file path (e.g. C:/Certs/ca.pem)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = { onExport(alias, destinationPath) },
                enabled = destinationPath.isNotEmpty()
            ) {
                Text("Export")
            }
        }
    }
}
