package com.devuloopers.knet.ui.core.components.button

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Visual emphasis variants supported by [KNetButton]. */
enum class ButtonVariant {
    Primary,
    Secondary,
    Tertiary,
    Ghost,
    Danger
}

/** Density presets supported by [KNetButton]. */
enum class ButtonSize {
    Compact,
    Standard,
    Large
}

/**
 * Resolved colors for every enabled and disabled button surface state.
 *
 * @property containerColor Background used while the button is enabled.
 * @property contentColor Foreground used while the button is enabled.
 * @property borderColor Optional enabled border color.
 * @property disabledContainerColor Background used while the button is disabled.
 * @property disabledContentColor Foreground used while the button is disabled.
 */
@Immutable
data class ButtonColors(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color = Color.Transparent,
    val disabledContainerColor: Color,
    val disabledContentColor: Color
)

/** Theme-aware sizing and color defaults for [KNetButton]. */
object ButtonDefaults {
    /** Resolves a complete color set for [variant]. */
    @Composable
    fun colors(variant: ButtonVariant = ButtonVariant.Primary): ButtonColors {
        val themeColors = KNetTheme.colors
        return when (variant) {
            ButtonVariant.Primary -> ButtonColors(
                containerColor = themeColors.accent,
                contentColor = Color.White,
                disabledContainerColor = themeColors.surfaceVariant,
                disabledContentColor = themeColors.textMuted
            )
            ButtonVariant.Secondary -> ButtonColors(
                containerColor = themeColors.surfaceVariant,
                contentColor = themeColors.textPrimary,
                borderColor = themeColors.border,
                disabledContainerColor = themeColors.surface,
                disabledContentColor = themeColors.textMuted
            )
            ButtonVariant.Tertiary -> ButtonColors(
                containerColor = themeColors.surface,
                contentColor = themeColors.textSecondary,
                borderColor = themeColors.border,
                disabledContainerColor = themeColors.surface,
                disabledContentColor = themeColors.textMuted
            )
            ButtonVariant.Ghost -> ButtonColors(
                containerColor = Color.Transparent,
                contentColor = themeColors.textPrimary,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = themeColors.textMuted
            )
            ButtonVariant.Danger -> ButtonColors(
                containerColor = themeColors.semantic.error,
                contentColor = Color.White,
                disabledContainerColor = themeColors.surfaceVariant,
                disabledContentColor = themeColors.textMuted
            )
        }
    }

    /** Resolves the current theme height for [size]. */
    @Composable
    fun height(size: ButtonSize): Dp {
        val dims = KNetTheme.dimensions
        return when (size) {
            ButtonSize.Compact -> dims.buttonHeightCompact
            ButtonSize.Standard -> dims.buttonHeightStandard
            ButtonSize.Large -> dims.buttonHeightLarge
        }
    }
}
