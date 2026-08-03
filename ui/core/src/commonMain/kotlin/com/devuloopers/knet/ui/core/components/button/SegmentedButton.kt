package com.devuloopers.knet.ui.core.components.button

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

@Composable
public fun KNetSegmentedButton(
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
                    .clickable { onOptionSelected(index) }
                    .handCursor()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
public fun SegmentedButton(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    KNetSegmentedButton(
        options = options,
        selectedIndex = selectedIndex,
        onOptionSelected = onOptionSelected,
        modifier = modifier
    )
}
