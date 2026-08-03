package com.devuloopers.knet.ui.core.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.badge.KNetBadge
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.input.KNetInputField
import com.devuloopers.knet.ui.core.components.input.KNetSearchField
import com.devuloopers.knet.ui.core.components.progress.LinearProgress
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun ComponentCatalog() {
    val typography = KNetTheme.typography
    val colors = KNetTheme.colors
    var textValue by remember { mutableStateOf("") }
    var searchValue by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Buttons", style = typography.titleMedium, color = colors.textPrimary)
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            KNetButton(onClick = {}, variant = ButtonVariant.Primary, modifier = Modifier.padding(end = 8.dp)) {
                Text("Primary")
            }
            KNetButton(onClick = {}, variant = ButtonVariant.Secondary, modifier = Modifier.padding(end = 8.dp)) {
                Text("Secondary")
            }
            KNetButton(onClick = {}, variant = ButtonVariant.Ghost, modifier = Modifier.padding(end = 8.dp)) {
                Text("Ghost")
            }
            KNetButton(onClick = {}, variant = ButtonVariant.Danger) {
                Text("Danger")
            }
        }

        Text("Inputs & Search", style = typography.titleMedium, color = colors.textPrimary, modifier = Modifier.padding(top = 16.dp))
        KNetInputField(
            value = textValue,
            onValueChange = { textValue = it },
            placeholder = "Enter input...",
            modifier = Modifier.padding(vertical = 4.dp)
        )
        KNetSearchField(
            query = searchValue,
            onQueryChange = { searchValue = it },
            placeholder = "Search catalog...",
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Text("Badges", style = typography.titleMedium, color = colors.textPrimary, modifier = Modifier.padding(top = 16.dp))
        Row(modifier = Modifier.padding(vertical = 8.dp)) {
            KNetBadge(text = "DEFAULT", modifier = Modifier.padding(end = 8.dp))
            KNetBadge(
                text = "ACTIVE",
                containerColor = colors.semantic.successContainer,
                contentColor = colors.semantic.success
            )
        }

        Text("Progress Indicator", style = typography.titleMedium, color = colors.textPrimary, modifier = Modifier.padding(top = 16.dp))
        LinearProgress(modifier = Modifier.padding(vertical = 8.dp))
    }
}
