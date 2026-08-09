package com.devuloopers.knet.ui.desktop.certificate.client

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.dialog.KNetDialog
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
fun ImportClientCertificateDialog(
    onDismiss: () -> Unit,
    onImport: (alias: String, path: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var alias by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }

    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    KNetDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.width(440.dp),
        title = "Import Client Certificate"
    ) {
        Column {
            Text(
                text = "Alias Name",
                style = typography.labelSmall,
                color = themeColors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            KNetTextField(
                value = alias,
                onValueChange = { alias = it },
                modifier = Modifier.fillMaxWidth(),
                config = InputFieldConfig(placeholder = "e.g. banking-api-cert")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "File Path (.p12 / .pfx / .pem / .crt)",
                style = typography.labelSmall,
                color = themeColors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            KNetTextField(
                value = path,
                onValueChange = { path = it },
                modifier = Modifier.fillMaxWidth(),
                config = InputFieldConfig(placeholder = "C:/certs/client-identity.p12")
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                KNetButton(
                    onClick = onDismiss,
                    variant = ButtonVariant.Ghost
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                KNetButton(
                    onClick = {
                        if (alias.isNotBlank()) {
                            onImport(alias, path)
                        }
                    },
                    enabled = alias.isNotBlank(),
                    variant = ButtonVariant.Primary
                ) {
                    Text("Import Certificate")
                }
            }
        }
    }
}
