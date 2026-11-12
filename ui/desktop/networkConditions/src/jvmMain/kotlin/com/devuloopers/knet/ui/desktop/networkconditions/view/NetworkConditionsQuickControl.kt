package com.devuloopers.knet.ui.desktop.networkconditions.view

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProfileId
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.dialog.AlertDialog
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdownSize
import com.devuloopers.knet.ui.core.components.surface.KNetSurface
import com.devuloopers.knet.ui.core.components.switch.KNetSwitch
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.networkconditions.model.noThrottlingProfileName
import com.devuloopers.knet.ui.desktop.networkconditions.model.globalProfileOptions
import com.devuloopers.knet.ui.desktop.networkconditions.model.selectedGlobalProfile

/** Global settings shortcut hosted in Traffic without giving Traffic a second policy owner or ViewModel. */
@Composable
fun NetworkConditionsQuickControl(
    configuration: NetworkConditionConfiguration,
    onEnabledChange: (Boolean) -> Unit,
    onActivateProfile: (NetworkConditionProfileId?) -> Unit,
    onOpenNetworkConditions: () -> Unit,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = configuration.selectedGlobalProfile
    val label = if (!configuration.enabled) "Conditions: Off"
        else "Global: ${selected?.name ?: noThrottlingProfileName}"
    val options = remember(configuration.profiles) { configuration.globalProfileOptions }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textStyle = LocalTextStyle.current.merge(KNetTheme.typography.labelMedium)
    val labelWidth = remember(label, textStyle, density) {
        with(density) { textMeasurer.measure(label, style = textStyle, maxLines = 1, softWrap = false).size.width.toDp() }
    }
    val contentWidth = labelWidth + KNetTheme.spacing.md * 2 +
        KNetTheme.dimensions.iconSizeSmall * 2 + KNetTheme.spacing.xs * 2
    val motion = KNetTheme.motion
    val gapPx = with(density) { KNetTheme.spacing.xs.roundToPx() }
    val positionProvider = remember(gapPx) { QuickControlPopupPositionProvider(gapPx) }

    BoxWithConstraints(modifier) {
        // Measure only the displayed label; the toolbar supplies the available-width bound.
        // Explicit width keeps both animation directions end-aligned without a second wrap-content animation.
        val animatedWidth by animateDpAsState(
            targetValue = contentWidth.coerceIn(minWidth, maxWidth),
            animationSpec = if (motion.animationsEnabled) {
                tween(durationMillis = motion.durationNormal, easing = motion.easingStandard)
            } else {
                snap()
            },
            label = "networkConditionsSelectedWidth",
        )
        KNetButton(
            onClick = { expanded = !expanded },
            variant = ButtonVariant.Secondary,
            size = ButtonSize.Compact,
            modifier = Modifier.width(animatedWidth).semantics {
                contentDescription = "Network Conditions quick control. $label"
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
                Icon(KNetIcons.Speed, null, Modifier.size(KNetTheme.dimensions.iconSizeSmall),
                    tint = if (configuration.enabled) KNetTheme.colors.accent else KNetTheme.colors.textSecondary)
                Text(label, Modifier.weight(1f), style = textStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Icon(KNetIcons.ChevronDown, null, Modifier.size(KNetTheme.dimensions.iconSizeSmall))
            }
        }
        if (expanded) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                KNetSurface(
                    modifier = Modifier.width(320.dp),
                    color = KNetTheme.colors.surfaceVariant,
                    border = BorderStroke(1.dp, KNetTheme.colors.border),
                    shape = KNetTheme.shapes.medium,
                ) {
                    Column(Modifier.fillMaxWidth().padding(KNetTheme.spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md)) {
                        Text("Network Conditions", style = KNetTheme.typography.titleMedium.copy(color = KNetTheme.colors.textPrimary))
                        KNetSwitch(configuration.enabled, onEnabledChange, label = "Enable Network Conditions")
                        Text("Global profile", style = KNetTheme.typography.labelMedium.copy(color = KNetTheme.colors.textPrimary))
                        KNetDropdown(
                            selectedItem = selected,
                            items = options,
                            onItemSelected = { profile ->
                                expanded = false
                                onActivateProfile(profile?.id)
                            },
                            defaultItem = null,
                            itemText = { it?.name ?: noThrottlingProfileName },
                            modifier = Modifier.fillMaxWidth(),
                            size = KNetDropdownSize.Compact,
                        )
                        Text("Selecting a profile enables conditions. Matching rules override the global profile.",
                            style = KNetTheme.typography.bodySmall.copy(color = KNetTheme.colors.textSecondary))
                        Text("Off bypasses all conditions. $noThrottlingProfileName leaves matching rules active.",
                            style = KNetTheme.typography.caption.copy(color = KNetTheme.colors.textSecondary))
                        HorizontalDivider()
                        KNetButton(
                            onClick = { expanded = false; onOpenNetworkConditions() },
                            variant = ButtonVariant.Secondary,
                            size = ButtonSize.Compact,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Open Network Conditions") }
                    }
                }
            }
        }
    }
    errorMessage?.let { message ->
        AlertDialog("Network Conditions", message, onDismissError)
    }
}

/** End-aligns the settings popover and keeps it reachable at the edges of a small desktop window. */
internal class QuickControlPopupPositionProvider(private val gapPx: Int = 4) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maximumX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val maximumY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val desiredX = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width
            else anchorBounds.left
        val below = anchorBounds.bottom + gapPx
        val desiredY = if (below + popupContentSize.height <= windowSize.height) below
            else anchorBounds.top - popupContentSize.height - gapPx
        return IntOffset(desiredX.coerceIn(0, maximumX), desiredY.coerceIn(0, maximumY))
    }
}
