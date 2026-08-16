package com.devuloopers.knet.ui.desktop.traffic.banner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Dismissible error notification banner displayed when the proxy engine encounters an error.
 *
 * @param errorMessage The detailed error message to present to the user, or null if no error is present.
 * @param onDismiss Callback invoked when the user clicks the dismiss button.
 * @param modifier Optional modifier to apply to the container layout.
 */
@Composable
public fun TrafficErrorBanner(
    errorMessage: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val spacing = KNetTheme.spacing
    val dimensions = KNetTheme.dimensions

    AnimatedVisibility(
        visible = errorMessage != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        if (errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeColors.semantic.errorContainer)
                    .border(width = 1.dp, color = themeColors.semantic.error.copy(alpha = 0.4f))
                    .padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Icon(
                    imageVector = KNetIcons.Warning,
                    contentDescription = "Engine Error",
                    tint = themeColors.semantic.error,
                    modifier = Modifier.size(dimensions.iconSizeSmall)
                )

                Text(
                    text = errorMessage,
                    style = typography.bodySmall.copy(
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(shapes.small)
                        .clickable { onDismiss() }
                        .padding(spacing.xs)
                        .handCursor(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = KNetIcons.Close,
                        contentDescription = "Dismiss Error",
                        tint = themeColors.textSecondary,
                        modifier = Modifier.size(dimensions.iconSizeSmall)
                    )
                }
            }
        }
    }
}
