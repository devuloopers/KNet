package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Responsive settings card that keeps explanatory text stable and moves controls below it in compact layouts.
 *
 * @param title Concise setting name.
 * @param description One-line behavior description.
 * @param compact Whether the design-system compact layout is active.
 * @param modifier Modifier applied to the card.
 * @param titleAccessory Optional badge displayed beside the title.
 * @param control Interactive setting control.
 */
@Composable
fun SettingsItem(
    title: String,
    description: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
    titleAccessory: (@Composable () -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    SettingsCard(modifier) {
        if (compact) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsItemText(title, description, titleAccessory)
                control()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsItemText(
                    title = title,
                    description = description,
                    titleAccessory = titleAccessory,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                control()
            }
        }
    }
}

@Composable
private fun SettingsItemText(
    title: String,
    description: String,
    titleAccessory: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = typography.titleSmall.copy(color = colors.textPrimary),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (titleAccessory != null) {
                Spacer(Modifier.width(8.dp))
                titleAccessory()
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = description,
            style = typography.bodySmall.copy(color = colors.textSecondary),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
