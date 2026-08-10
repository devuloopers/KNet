package com.devuloopers.knet.ui.desktop.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.components.dropdown.KNetSearchableDropdown
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.settings.model.SettingsIntent
import com.devuloopers.knet.ui.desktop.settings.model.SettingsState

/**
 * Tab content view rendering Color Theme segmented controls and Default Scripting Language settings.
 */
@Composable
fun AppearanceTab(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column {
            Text(
                text = "Appearance",
                style = typography.titleLarge.copy(color = themeColors.textPrimary)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Customize application color themes and default workspace preferences.",
                style = typography.bodyMedium.copy(color = themeColors.textSecondary)
            )
        }

        // Card 1: Color Theme (Disabled with Upcoming Feature Tag)
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Color Theme",
                            style = typography.titleSmall.copy(color = themeColors.textPrimary)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        UpcomingFeatureBadge()
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Select application visual theme mode (Dark theme is currently active).",
                        style = typography.bodySmall.copy(color = themeColors.textSecondary)
                    )
                }

                Row(
                    modifier = Modifier
                        .background(themeColors.background.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .border(1.dp, themeColors.border.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(4.dp)
                ) {
                    DisabledSegmentedOption("Dark", isSelected = true)
                    DisabledSegmentedOption("Light", isSelected = false)
                    DisabledSegmentedOption("System", isSelected = false)
                }
            }
        }

        // Card 2: Default Scripting Language
        SettingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Default Scripting Language",
                        style = typography.titleSmall.copy(color = themeColors.textPrimary)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Default language used for pre-request and post-response scripts.",
                        style = typography.bodySmall.copy(color = themeColors.textSecondary)
                    )
                }

                Box(modifier = Modifier.width(160.dp)) {
                    KNetSearchableDropdown(
                        items = listOf("JavaScript", "Kotlin"),
                        selectedItem = if (state.scriptLanguage == "KOTLIN") "Kotlin" else "JavaScript",
                        onItemSelected = { selected ->
                            onIntent(SettingsIntent.SetScriptLanguage(selected.uppercase()))
                        },
                        itemText = { it }
                    )
                }
            }
        }
    }
}

@Composable
private fun UpcomingFeatureBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFF8B5CF6).copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "• UPCOMING FEATURE",
            style = KNetTheme.typography.labelSmall.copy(
                color = Color(0xFFA78BFA),
                fontSize = 10.sp
            )
        )
    }
}

@Composable
private fun DisabledSegmentedOption(
    label: String,
    isSelected: Boolean
) {
    val themeColors = KNetTheme.colors
    val bg = if (isSelected) themeColors.surface.copy(alpha = 0.5f) else Color.Transparent
    val textCol = if (isSelected) themeColors.textPrimary.copy(alpha = 0.6f) else themeColors.textMuted.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = KNetTheme.typography.labelMedium.copy(color = textCol)
        )
    }
}
