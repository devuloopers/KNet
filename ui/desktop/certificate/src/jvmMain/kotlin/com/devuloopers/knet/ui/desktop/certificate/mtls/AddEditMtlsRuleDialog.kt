package com.devuloopers.knet.ui.desktop.certificate.mtls

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
import com.devuloopers.knet.ui.core.components.chip.KNetTag
import com.devuloopers.knet.ui.core.components.dropdown.KNetSearchableDropdown
import com.devuloopers.knet.ui.desktop.certificate.model.ClientCertificate
import com.devuloopers.knet.ui.desktop.certificate.model.MtlsRule

@Composable
fun AddEditMtlsRuleDialog(
    availableCertificates: List<ClientCertificate>,
    onDismiss: () -> Unit,
    onSave: (MtlsRule) -> Unit,
    initialRule: MtlsRule? = null,
    modifier: Modifier = Modifier
) {
    var ruleName by remember { mutableStateOf(initialRule?.ruleName ?: "") }
    var hostPattern by remember { mutableStateOf(initialRule?.hostPattern ?: "") }
    var certAlias by remember { mutableStateOf(initialRule?.certificateAlias ?: "") }

    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    KNetDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.width(440.dp),
        title = if (initialRule == null) "Add mTLS Host Rule" else "Edit mTLS Host Rule"
    ) {
        Column {
            Text(
                text = "Rule Name",
                style = typography.labelSmall,
                color = themeColors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            KNetTextField(
                value = ruleName,
                onValueChange = { ruleName = it },
                modifier = Modifier.fillMaxWidth(),
                config = InputFieldConfig(placeholder = "e.g. Bank API Rule")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Host Matching Pattern",
                style = typography.labelSmall,
                color = themeColors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            KNetTextField(
                value = hostPattern,
                onValueChange = { hostPattern = it },
                modifier = Modifier.fillMaxWidth(),
                config = InputFieldConfig(placeholder = "*.api.internal.bank.com")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Mapped Client Certificate Alias",
                style = typography.labelSmall,
                color = themeColors.textSecondary
            )
            Spacer(modifier = Modifier.height(4.dp))
            KNetSearchableDropdown(
                selectedItem = availableCertificates.firstOrNull { it.alias == certAlias },
                items = availableCertificates,
                onItemSelected = { selectedCert ->
                    certAlias = selectedCert.alias
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Select Certificate...",
                itemText = { it.alias },
                itemContent = { cert ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = cert.alias,
                            style = typography.bodyMedium,
                            color = themeColors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        KNetTag(text = cert.format.name)
                    }
                }
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
                        if (ruleName.isNotBlank() && hostPattern.isNotBlank() && certAlias.isNotBlank()) {
                            onSave(
                                MtlsRule(
                                    ruleName = ruleName,
                                    hostPattern = hostPattern,
                                    certificateAlias = certAlias,
                                    enabled = initialRule?.enabled ?: true
                                )
                            )
                        }
                    },
                    enabled = ruleName.isNotBlank() && hostPattern.isNotBlank() && certAlias.isNotBlank(),
                    variant = ButtonVariant.Primary
                ) {
                    Text("Save Rule")
                }
            }
        }
    }
}
