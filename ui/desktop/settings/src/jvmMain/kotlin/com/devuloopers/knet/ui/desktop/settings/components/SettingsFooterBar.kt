package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.settings.model.SettingsField
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsNoticeTone
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState

/** Displays typed operation feedback and the guarded reset action. */
@Composable
fun SettingsFooterBar(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    val noticeColor = when (state.notice?.tone) {
        SettingsNoticeTone.SUCCESS -> colors.semantic.success
        SettingsNoticeTone.INFO -> colors.semantic.info
        SettingsNoticeTone.WARNING -> colors.semantic.warning
        SettingsNoticeTone.ERROR -> colors.semantic.error
        null -> colors.textSecondary
    }

    KNetSurface(
        modifier = modifier.fillMaxWidth().height(56.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(Modifier.weight(1f)) {
                state.notice?.let { notice ->
                    Text(
                        text = notice.summary,
                        style = KNetTheme.typography.bodySmall.copy(color = noticeColor),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            KNetButton(
                onClick = { onIntent(SettingsIntent.RequestResetDefaults) },
                enabled = !state.isLoading,
                loading = SettingsField.RESET_DEFAULTS in state.savingFields,
                variant = ButtonVariant.Ghost,
            ) {
                Text("Reset Defaults", maxLines = 1, softWrap = false)
            }
        }
    }
}
