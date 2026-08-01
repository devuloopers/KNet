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
import com.devuloopers.knet.ui.desktop.scripting.model.ScriptSnippet

/**
 * Favorites panel displaying starred snippets.
 */
@Composable
fun Favorites(
    favoriteSnippets: List<ScriptSnippet>,
    onSnippetSelect: (ScriptSnippet) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(8.dp)) {
        Text(
            text = "Favorite Snippets",
            color = KNetColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        LazyColumn {
            items(favoriteSnippets) { snippet ->
                Text(
                    text = "★ ${snippet.title}",
                    color = KNetColors.ActiveBlue,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSnippetSelect(snippet) }
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}
