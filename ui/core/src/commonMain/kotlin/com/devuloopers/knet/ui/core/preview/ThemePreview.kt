package com.devuloopers.knet.ui.core.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.core.theme.KNetTheme
import com.devuloopers.knet.ui.core.theme.KNetTypography

/**
 * Preview composable showcasing KNet Theme colors and typography hierarchy.
 */
@Composable
public fun ThemePreview() {
    KNetTheme {
        Column(
            modifier = Modifier
                .background(KNetColors.BackgroundDark)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = "KNet Title Text", style = KNetTypography.Title)
            Text(text = "KNet Subtitle Text", style = KNetTypography.Subtitle)
            Text(text = "KNet Body Text", style = KNetTypography.Body)
            Text(text = "KNet Monospace Code", style = KNetTypography.MonospaceCode)
        }
    }
}
