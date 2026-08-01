package com.devuloopers.knet.ui.desktop.scripting.snippets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptTemplate

/**
 * TemplateLibrary side panel showing a searchable list of script templates.
 */
@Composable
public fun TemplateLibrary(
    templates: List<ScriptTemplate>,
    onTemplateSelect: (ScriptTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "Template Library",
            color = KNetColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn {
            items(templates) { template ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTemplateSelect(template) }
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = template.name,
                        color = KNetColors.TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = template.description,
                        color = KNetColors.TextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
