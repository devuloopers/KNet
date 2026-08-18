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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptTemplate

/**
 * TemplateLibrary side panel showing a searchable list of script templates.
 */
@Composable
fun TemplateLibrary(
    templates: List<ScriptTemplate>,
    onTemplateSelect: (ScriptTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "Template Library",
            style = typography.caption.copy(
                color = themeColors.textPrimary,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn {
            items(templates) { template ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .handCursor()
                        .clickable { onTemplateSelect(template) }
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = template.name,
                        style = typography.caption.copy(
                            color = themeColors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = template.description,
                        style = typography.caption.copy(color = themeColors.textSecondary),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

