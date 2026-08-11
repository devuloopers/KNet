package com.devuloopers.knet.ui.core.components.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Key-value inspector row primitive for certificate details and metadata lists.
 *
 * @param label Descriptor label text.
 * @param modifier Composable layout modifier.
 * @param content Slot layout for value text.
 */
@Composable
public fun KNetInspectorRow(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = typography.caption.copy(
                color = themeColors.textMuted,
                fontWeight = FontWeight.Medium
            )
        )
        content()
    }
}
