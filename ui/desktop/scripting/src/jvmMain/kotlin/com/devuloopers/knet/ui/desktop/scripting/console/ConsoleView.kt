package com.devuloopers.knet.ui.desktop.scripting.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.scripting.model.ConsoleLogEntry
import com.devuloopers.knet.ui.desktop.scripting.model.ConsoleLogLevel

/**
 * ConsoleView displays logs with levels INFO, WARN, ERROR, DEBUG.
 */
@Composable
public fun ConsoleView(
    logs: List<ConsoleLogEntry>,
    filter: String,
    autoScroll: Boolean,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val shapes = KNetTheme.shapes
    val listState = rememberLazyListState()
    val filteredLogs = if (filter == "ALL") logs else logs.filter { it.level.name == filter }

    LaunchedEffect(filteredLogs.size, autoScroll) {
        if (autoScroll && filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.surfaceVariant, shapes.medium)
            .padding(6.dp)
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(filteredLogs) { entry ->
                val color = when (entry.level) {
                    ConsoleLogLevel.ERROR -> themeColors.semantic.error
                    ConsoleLogLevel.WARN -> themeColors.semantic.warning
                    ConsoleLogLevel.DEBUG -> themeColors.accent
                    ConsoleLogLevel.INFO -> themeColors.textPrimary
                }
                Text(
                    text = "[${entry.level.name}] ${entry.message}",
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
        }
    }
}

