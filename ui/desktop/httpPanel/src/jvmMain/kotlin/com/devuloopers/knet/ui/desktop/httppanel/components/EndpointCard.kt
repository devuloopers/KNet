package com.devuloopers.knet.ui.desktop.httppanel.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Standardized HTTP Endpoint Card presentation component.
 * Displays vertically centered HTTP method badge, target URL endpoint, and default copy URL action button.
 *
 * Owned centrally by :ui:desktop:http for multi-module presentation reuse.
 */
@Composable
public fun EndpointCard(
    method: String,
    endpoint: String,
    modifier: Modifier = Modifier,
    methodColor: Color = KNetTheme.colors.semantic.success,
    trailingContent: (@Composable RowScope.() -> Unit)? = {
        KNetCopyButton(textToCopy = endpoint)
    }
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.medium)
            .background(themeColors.surfaceVariant)
            .border(1.dp, themeColors.border, shapes.medium)
            .padding(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(
                text = method,
                style = typography.codeSmall.copy(
                    color = methodColor,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = endpoint,
                style = typography.codeSmall.copy(
                    color = themeColors.textPrimary
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailingContent != null) {
            trailingContent()
        }
    }
}
