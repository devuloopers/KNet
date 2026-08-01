package com.devuloopers.knet.ui.desktop.inspector.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.theme.KNetColors

/**
 * Inspector actions toolbar hosting body mode selector, search bar, and copy buttons.
 */
@Composable
public fun InspectorToolbar(
    searchQuery: String,
    bodyMode: String,
    onSearchChanged: (String) -> Unit,
    onBodyModeSelected: (String) -> Unit,
    onCopyContent: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.BackgroundDark)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyModeSelector(selectedMode = bodyMode, onModeSelected = onBodyModeSelected)
        SearchBar(query = searchQuery, onQueryChanged = onSearchChanged)
        CopyActions(onCopy = { onCopyContent("Payload") })
    }
}
