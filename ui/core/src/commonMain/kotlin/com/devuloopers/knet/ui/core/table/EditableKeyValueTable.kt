package com.devuloopers.knet.ui.core.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.icon.KNetIcons
import com.devuloopers.knet.ui.core.input.TableCellTextField
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetShapes

/**
 * Key-value pair data model for generic editable tables.
 */
public data class KeyValuePair(
    val key: String,
    val value: String,
    val enabled: Boolean = true
)

/**
 * Generic reusable editable key-value table component.
 *
 * Decoupled from HTTP-specific header or query parameter models.
 *
 * @param pairs List of key-value pairs.
 * @param onPairChange Callback triggered when a pair key or value is edited.
 * @param onPairDelete Callback triggered when a pair is removed.
 * @param onAddPair Callback triggered when a new empty pair is added.
 * @param modifier Layout modifier.
 */
@Composable
public fun EditableKeyValueTable(
    pairs: List<KeyValuePair>,
    onPairChange: (index: Int, key: String, value: String) -> Unit,
    onPairDelete: (index: Int) -> Unit,
    onAddPair: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.SurfaceDark, KNetShapes.Medium)
            .border(1.dp, KNetColors.BorderDark, KNetShapes.Medium)
            .padding(6.dp)
    ) {
        // Table Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Key / Name",
                color = KNetColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Value",
                color = KNetColors.TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "",
                modifier = Modifier.size(16.dp)
            )
        }

        // Table Rows
        pairs.forEachIndexed { index, pair ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TableCellTextField(
                    value = pair.key,
                    onValueChange = { newKey -> onPairChange(index, newKey, pair.value) },
                    placeholder = "Header-Name",
                    modifier = Modifier.weight(1f)
                )
                TableCellTextField(
                    value = pair.value,
                    onValueChange = { newValue -> onPairChange(index, pair.key, newValue) },
                    placeholder = "Header-Value",
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = KNetIcons.ClearIcon,
                    contentDescription = "Delete row",
                    tint = KNetColors.ErrorRed,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onPairDelete(index) }
                )
            }
        }

        // Add Row Button
        Text(
            text = "+ Add Row",
            color = KNetColors.ActiveBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { onAddPair() }
                .padding(top = 6.dp)
        )
    }
}
