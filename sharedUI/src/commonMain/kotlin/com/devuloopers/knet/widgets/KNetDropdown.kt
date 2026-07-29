package com.devuloopers.knet.widgets

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.devuloopers.knet.theme.KNetColors

/**
 * Reusable dark single-select Dropdown component for KNet UI forms and dialogs.
 * Features full-width popup alignment, checkmark indicator, hover highlights, and premium dark styling.
 *
 * @param items List of selectable items.
 * @param selectedItem Currently selected item.
 * @param itemLabel Lambda returning display string for an item.
 * @param onItemSelected Callback when an item is chosen.
 * @param placeholder Hint text when no item is selected.
 * @param height Height of the dropdown trigger field.
 * @param cornerRadius Border corner radius.
 */
@Composable
fun <T> KNetDropdown(
    items: List<T>,
    selectedItem: T?,
    itemLabel: (T) -> String,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select an option...",
    height: Dp = 36.dp,
    cornerRadius: Dp = 6.dp
) {
    var isExpanded by remember { mutableStateOf(false) }
    var triggerWidthDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            triggerWidthDp = with(density) { coordinates.size.width.toDp() }
        }
    ) {
        // Trigger Field Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(
                    if (isExpanded) KNetColors.SurfaceDark else KNetColors.FieldDark,
                    RoundedCornerShape(cornerRadius)
                )
                .border(
                    1.dp,
                    if (isExpanded) KNetColors.ActiveBlue else KNetColors.BorderDark,
                    RoundedCornerShape(cornerRadius)
                )
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedItem?.let { itemLabel(it) } ?: placeholder,
                color = if (selectedItem != null) Color.White else KNetColors.TextSecondary.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = if (selectedItem != null) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand dropdown",
                tint = if (isExpanded) KNetColors.ActiveBlue else KNetColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }

        // Popup Container
        if (isExpanded && items.isNotEmpty()) {
            val popupOffsetY = with(density) { height.roundToPx() + 4 }
            Popup(
                onDismissRequest = { isExpanded = false },
                offset = IntOffset(0, popupOffsetY),
                properties = PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .width(if (triggerWidthDp > 0.dp) triggerWidthDp else 240.dp)
                        .shadow(12.dp, RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E2228), RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFF30363D), RoundedCornerShape(6.dp))
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        items.forEach { item ->
                            val isSelected = item == selectedItem
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) KNetColors.ActiveBlue.copy(alpha = 0.15f) else Color.Transparent
                                    )
                                    .clickable {
                                        onItemSelected(item)
                                        isExpanded = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = itemLabel(item),
                                    color = if (isSelected) KNetColors.ActiveBlue else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = KNetColors.ActiveBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
