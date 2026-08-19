package com.devuloopers.knet.ui.core.components.button

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Compact single-selection control for a small set of peer options. */
@Composable
fun KNetSegmentedButton(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes

    Row(
        modifier = modifier
            .selectableGroup()
            .clip(shapes.small)
            .background(themeColors.surfaceVariant)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            val backgroundColor = if (isSelected) themeColors.surface else themeColors.surfaceVariant
            val textColor = if (isSelected) themeColors.textPrimary else themeColors.textSecondary

            Text(
                text = option,
                style = typography.labelSmall.copy(color = textColor),
                modifier = Modifier
                    .clip(shapes.small)
                    .background(backgroundColor)
                    .heightIn(min = 24.dp)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onOptionSelected(index) }
                    )
                    .handCursor()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}
