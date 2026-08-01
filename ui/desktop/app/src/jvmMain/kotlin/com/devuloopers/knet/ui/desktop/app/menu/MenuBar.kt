package com.devuloopers.knet.ui.desktop.app.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Top application menu bar composable (File, Edit, View, Tools, Help).
 *
 * @param menus Map of menu category titles to their list of [MenuItem] actions.
 * @param modifier Layout modifier.
 */
@Composable
public fun MenuBar(
    menus: Map<String, List<MenuItem>> = emptyMap(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(KNetColors.BackgroundDark)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val defaultCategories = if (menus.isEmpty()) {
            listOf("File", "Edit", "View", "Tools", "Help")
        } else {
            menus.keys.toList()
        }

        defaultCategories.forEach { category ->
            Text(
                text = category,
                color = KNetColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .clickable { /* Popup category menu */ }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}
