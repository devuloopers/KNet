package com.devuloopers.knet.ui.desktop.certificate.mtls

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
import com.devuloopers.knet.ui.desktop.certificate.model.MtlsRule

/**
 * MtlsRuleDialog provides interactive forms to add/modify client identity mapping filters.
 */
@Composable
public fun MtlsRuleDialog(
    onDismiss: () -> Unit,
    onSave: (MtlsRule) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var certAlias by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark)
            .padding(16.dp)
    ) {
        Text(text = "Add Mutual TLS Routing Rule", color = KNetColors.TextPrimary, fontSize = 13.sp)
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Rule name (e.g. Stage API)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        TextField(
            value = pattern,
            onValueChange = { pattern = it },
            label = { Text("Host Pattern (e.g. stage.domain.com)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        TextField(
            value = certAlias,
            onValueChange = { certAlias = it },
            label = { Text("Client certificate alias reference") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = {
                    onSave(MtlsRule(ruleName = name, hostPattern = pattern, certificateAlias = certAlias))
                },
                enabled = name.isNotEmpty() && pattern.isNotEmpty() && certAlias.isNotEmpty()
            ) {
                Text("Add Rule")
            }
        }
    }
}
