package com.devuloopers.knet.ui.core.components.dropdown

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.checkbox.KNetCheckboxIndicator
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Anchored KNet dropdown for toggling multiple values without dismissing the popup after each change.
 *
 * The component shares its anchor, popup, dimensions, motion, and dismissal behavior with [KNetDropdown]. Each
 * option row owns one checkbox action, preventing nested checkbox and row clicks from toggling a value twice.
 *
 * @param label Stable text rendered in the anchor.
 * @param items Toggleable values in visual order.
 * @param isItemSelected Returns whether a value is currently selected.
 * @param onItemToggle Invoked once when a value row is activated. The popup remains open.
 * @param modifier Modifier applied to the anchored field; sizing modifiers override the default width.
 * @param enabled Whether the field accepts pointer and keyboard input.
 * @param size Fixed anchor density.
 * @param itemText Converts a value into visible option text.
 */
@Composable
fun <T> KNetMultiSelectDropdown(
    label: String,
    items: List<T>,
    isItemSelected: (T) -> Boolean,
    onItemToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: KNetDropdownSize = KNetDropdownSize.Standard,
    itemText: (T) -> String = { it.toString() }
) {
    val colors = KNetTheme.colors
    val expansionState = rememberDropdownExpansionState()
    val expanded = expansionState.expanded
    var anchorSizePx by remember { mutableStateOf(IntSize.Zero) }
    val canExpand = enabled && items.isNotEmpty()

    Box(
        modifier = modifier
            .width(KNetDropdownDefaults.MultiSelectWidth)
            .onSizeChanged { anchorSizePx = it }
    ) {
        KNetDropdownAnchor(
            text = label,
            expanded = expanded,
            enabled = canExpand,
            size = size,
            textColor = if (canExpand) colors.textSecondary else colors.textMuted,
            fontWeight = FontWeight.Medium,
            onToggle = { expansionState.toggle() },
            onOpen = expansionState::open,
            onClose = expansionState::close,
        )

        KNetDropdownPopup(
            expanded = expanded,
            onDismissRequest = expansionState::dismissFromPopup,
            anchorSizePx = anchorSizePx,
        ) {
            val menuScrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = KNetDropdownDefaults.MaxMenuHeight)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = KNetDropdownDefaults.MaxMenuHeight)
                        .verticalScroll(menuScrollState)
                ) {
                    items.forEach { item ->
                        KNetMultiSelectDropdownItem(
                            text = itemText(item),
                            selected = isItemSelected(item),
                            onToggle = { onItemToggle(item) }
                        )
                    }
                }
                KNetVerticalScrollbar(
                    scrollState = menuScrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

/** Selection row used by [KNetMultiSelectDropdown]. */
@Composable
private fun KNetMultiSelectDropdownItem(
    text: String,
    selected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    val motion = KNetTheme.motion
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val duration = if (motion.animationsEnabled) motion.durationFast else motion.durationInstant
    val rowColor by animateColorAsState(
        targetValue = when {
            selected -> colors.interaction.selectedOverlay.compositeOver(colors.surfaceVariant)
            hovered -> colors.interaction.hoverOverlay.compositeOver(colors.surfaceVariant)
            else -> colors.surfaceVariant
        },
        animationSpec = tween(duration),
        label = "multiSelectDropdownItem"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(KNetDropdownDefaults.ItemHeight)
            .background(rowColor)
            .hoverable(interactionSource)
            .toggleable(
                value = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .handCursor()
            .padding(horizontal = KNetDropdownDefaults.HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KNetCheckboxIndicator(checked = selected, enabled = true)
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = if (selected) colors.textPrimary else colors.textSecondary,
            style = typography.bodySmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
