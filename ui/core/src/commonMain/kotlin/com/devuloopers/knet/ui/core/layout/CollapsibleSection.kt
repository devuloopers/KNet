package com.devuloopers.knet.ui.core.layout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.icon.KNetIcons
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Expandable/collapsible container section with a header title bar.
 *
 * @param title Section header title.
 * @param initialExpanded Initial expanded state. Defaults to true.
 * @param modifier Layout modifier.
 * @param content Expanded content composable slot.
 */
@Composable
public fun CollapsibleSection(
    title: String,
    initialExpanded: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(initialExpanded) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) KNetIcons.ChevronDown else KNetIcons.ChevronUp,
                contentDescription = "Toggle section",
                tint = KNetColors.TextSecondary,
                modifier = Modifier
                    .size(12.dp)
                    .padding(end = 4.dp)
            )
            Text(
                text = title,
                color = KNetColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            ) {
                content()
            }
        }
    }
}
