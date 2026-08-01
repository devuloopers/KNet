package com.devuloopers.knet.ui.core.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.devuloopers.knet.ui.core.icon.KNetIcons
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Reusable dark single-select Dropdown component for KNet UI forms and dialogs.
 *
 * @param items List of selectable items.
 * @param selectedItem Currently selected item.
 * @param itemLabel Lambda returning display string for an item.
 * @param onItemSelected Callback when an item is chosen.
 * @param modifier Layout parameters.
 * @param placeholder Hint text when no item is selected.
 * @param height Height of the dropdown field.
 */
@Composable
public fun <T> KNetDropdown(
    items: List<T>,
    selectedItem: T?,
    itemLabel: (T) -> String,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select option",
    height: Dp = 28.dp
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(KNetColors.FieldDark, KNetShapes.Medium)
                .border(1.dp, KNetColors.BorderDark, KNetShapes.Medium)
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = selectedItem?.let(itemLabel) ?: placeholder,
                color = if (selectedItem != null) KNetColors.TextPrimary else KNetColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )
            Icon(
                imageVector = if (expanded) KNetIcons.ChevronUp else KNetIcons.ChevronDown,
                contentDescription = "Expand dropdown",
                tint = KNetColors.TextSecondary,
                modifier = Modifier.size(12.dp)
            )
        }

        if (expanded) {
            Popup(
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KNetColors.SurfaceDark, KNetShapes.Medium)
                        .border(1.dp, KNetColors.BorderDark, KNetShapes.Medium)
                        .padding(vertical = 4.dp)
                ) {
                    items.forEach { item ->
                        val isSelected = item == selectedItem
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onItemSelected(item)
                                    expanded = false
                                }
                                .background(if (isSelected) KNetColors.SelectedRowHighlight else KNetColors.SurfaceDark)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = itemLabel(item),
                                color = if (isSelected) KNetColors.ActiveBlue else KNetColors.TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = KNetIcons.Checkmark,
                                    contentDescription = "Selected",
                                    tint = KNetColors.ActiveBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
