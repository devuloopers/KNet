package com.devuloopers.knet.ui.core.components.placeholder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Standardized empty state placeholder composable for empty tabs, lists, and tables across KNet.
 *
 * @param title Primary heading text describing the empty state.
 * @param subtitle Secondary explanatory text giving context (e.g., HTTP status code or tab context).
 * @param modifier Composable layout modifier.
 * @param icon Vector icon vector (default: KNetIcons.Info).
 */
@Composable
public fun KNetEmptyStatePlaceholder(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = KNetIcons.Info
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeColors.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Empty State",
                modifier = Modifier.size(32.dp),
                tint = themeColors.textMuted
            )
            Text(
                text = title,
                style = typography.titleSmall.copy(
                    color = themeColors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                ),
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = typography.bodySmall.copy(color = themeColors.textMuted),
                textAlign = TextAlign.Center
            )
        }
    }
}
