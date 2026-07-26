package com.devuloopers.knet.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors

/**
 * Node helper to render tree parameters with depth indentation.
 *
 * Meticulously matches the tree structures of the HTML mockup.
 *
 * @param depth Indentation offset multiplier.
 * @param name Parameter key name.
 * @param value Optional parameter string value.
 */
@Composable
fun ParameterNode(
    depth: Int,
    name: String,
    value: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Checkbox Icon
            Icon(
                imageVector = Icons.Default.CheckBox,
                contentDescription = "Selected",
                tint = KNetColors.ActiveBlue,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))

            // Arrow for parents
            if (value == null) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand",
                    tint = KNetColors.TextSecondary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
            } else {
                Spacer(modifier = Modifier.width(14.dp))
            }

            // Key Name
            Text(
                text = name,
                color = if (value == null) Color.White else KNetColors.ActiveBlue,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = if (value == null) FontWeight.SemiBold else FontWeight.Normal
            )

            // Value assignment
            if (value != null) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = value,
                    color = KNetColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }

        // Edit/Delete tools on right hover simulation (Material 3 Icons)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit",
                tint = KNetColors.TextSecondary,
                modifier = Modifier.size(12.dp).clickable { }
            )
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = KNetColors.TextSecondary,
                modifier = Modifier.size(12.dp).clickable { }
            )
        }
    }
}
