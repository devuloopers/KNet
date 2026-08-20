package com.devuloopers.knet.ui.desktop.certificate.client

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.dialog.KNetDialog
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.InputFieldState
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.components.input.KNetPasswordField
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Modal dialog composable for importing client certificates (PKCS#12 or PEM) with native file browsing support.
 *
 * @param onDismiss Execution callback when the user cancels or closes the modal dialog.
 * @param onImport Execution callback passing the verified alias name, file path, and optional passphrase for engine ingestion.
 * @param errorMessage Optional error message text to display if certificate import or parsing failed.
 * @param modifier Layout modifier for custom sizing or positioning.
 */
@Composable
fun ImportClientCertificateDialog(
    onDismiss: () -> Unit,
    onImport: (alias: String, path: String, passphrase: String) -> Unit,
    errorMessage: String? = null,
    isImporting: Boolean = false,
    filePicker: ClientIdentityFilePicker = NativeClientIdentityFilePicker,
    modifier: Modifier = Modifier
) {
    var alias by remember { mutableStateOf("") }
    var path by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var filePickerError by remember { mutableStateOf<String?>(null) }

    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    fun triggerFilePicker() {
        filePicker.chooseIdentity { result ->
            result.onSuccess { selectedPath ->
                if (selectedPath != null) {
                    path = selectedPath
                    if (alias.isBlank()) {
                        alias = java.io.File(selectedPath).nameWithoutExtension
                    }
                    filePickerError = null
                }
            }.onFailure { error ->
                filePickerError = error.message ?: "The file picker could not be opened. Enter the path manually."
            }
        }
    }

    KNetDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.widthIn(max = 480.dp),
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
                config = InputFieldConfig(placeholder = "e.g. banking-api-cert"),
                state = InputFieldState(enabled = !isImporting),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Identity File (${ClientIdentityImportCapabilities.label})",
                style = typography.labelSmall,
                color = themeColors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KNetTextField(
                    value = path,
                    onValueChange = { path = it },
                    modifier = Modifier.weight(1f),
                    config = InputFieldConfig(placeholder = "C:/certs/client-identity.p12"),
                    state = InputFieldState(enabled = !isImporting),
                )
                Spacer(modifier = Modifier.width(8.dp))
                KNetButton(
                    onClick = { triggerFilePicker() },
                    variant = ButtonVariant.Secondary,
                    size = ButtonSize.Standard,
                    enabled = !isImporting,
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Browse",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Browse...")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Passphrase / Password (optional for encrypted .p12/.pfx)",
                style = typography.labelSmall,
                color = themeColors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            KNetPasswordField(
                value = passphrase,
                onValueChange = { passphrase = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Enter the source identity passphrase",
                enabled = !isImporting,
            )

            val visibleError = errorMessage ?: filePickerError
            if (!visibleError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = visibleError,
                    style = typography.bodySmall,
                    color = themeColors.semantic.error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                KNetButton(
                    onClick = onDismiss,
                    variant = ButtonVariant.Ghost,
                    enabled = !isImporting,
                ) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                KNetButton(
                    onClick = {
                        if (alias.isNotBlank() && path.isNotBlank()) {
                            onImport(alias.trim(), path.trim(), passphrase)
                        }
                    },
                    enabled = alias.isNotBlank() && path.isNotBlank(),
                    loading = isImporting,
                    variant = ButtonVariant.Primary
                ) {
                    Text("Import Certificate")
                }
            }
        }
    }
}
