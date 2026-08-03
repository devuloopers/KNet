package com.devuloopers.knet.ui.core.components.propertygrid

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

public data class PropertyItem(
    val label: String,
    val value: String,
    val category: String? = null
)

/**
 * Domain-agnostic generic property grid component.
 */
@Composable
public fun KNetPropertyGrid(
    properties: List<PropertyItem>,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        properties.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label,
                    style = typography.bodySmall.copy(color = themeColors.textSecondary),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = item.value,
                    style = typography.codeSmall.copy(color = themeColors.textPrimary),
                    modifier = Modifier.weight(1.5f)
                )
            }
            HorizontalDivider()
        }
    }
}
