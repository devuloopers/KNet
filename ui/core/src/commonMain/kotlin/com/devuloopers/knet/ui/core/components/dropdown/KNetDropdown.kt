package com.devuloopers.knet.ui.core.components.dropdown

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.dimensions.KNetDimensions
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Density presets shared by KNet single-select and multi-select dropdowns. */
enum class KNetDropdownSize {
    Compact,
    Standard
}

/** Shared dimensions for compact KNet dropdown fields and menus. */
object KNetDropdownDefaults {
    /** Height used when a dropdown participates in a dense toolbar. */
    val CompactFieldHeight: Dp = KNetDimensions.inputHeightCompact
    /** Height of the anchored selection field. */
    val FieldHeight: Dp = 36.dp
    /** Minimum height of each popup option. */
    val ItemHeight: Dp = 34.dp
    /** Horizontal inset shared by the field and popup items. */
    val HorizontalPadding: Dp = 10.dp
    /** Maximum popup height before its content scrolls. */
    val MaxMenuHeight: Dp = 248.dp
    /** Finite default anchor width; callers can override it through their [Modifier]. */
    val DefaultWidth: Dp = 120.dp
    /** Default width for multi-select controls whose popup rows include a selection indicator. */
    val MultiSelectWidth: Dp = 148.dp

    /** Resolves the fixed anchor height for [size]. */
    fun fieldHeight(size: KNetDropdownSize): Dp = when (size) {
        KNetDropdownSize.Compact -> CompactFieldHeight
        KNetDropdownSize.Standard -> FieldHeight
    }
}

/**
 * Compact, anchored KNet selection field.
 *
 * The popup follows the anchor width, selection is represented independently from display text, and
 * keyboard users can open or close the list with Enter, Space, the arrow keys, or Escape.
 *
 * @param selectedItem Current selection.
 * @param items Available values in visual order.
 * @param onItemSelected Invoked once when a value is chosen.
 * @param modifier Modifier applied to the anchored field; sizing modifiers override the compact default width.
 * @param placeholder Text shown for [defaultItem].
 * @param defaultItem Explicit value represented by [placeholder].
 * @param enabled Whether the field can be opened.
 * @param size Fixed anchor density. Selection text never participates in anchor measurement.
 * @param itemText Converts a value into visible text.
 * @param itemColor Optionally supplies a semantic text color for a value.
 */
@Composable
fun <T> KNetDropdown(
    selectedItem: T,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    defaultItem: T? = items.firstOrNull(),
    enabled: Boolean = true,
    size: KNetDropdownSize = KNetDropdownSize.Standard,
    itemText: (T) -> String = { it.toString() },
    itemColor: ((T) -> Color?)? = null
) {
    val colors = KNetTheme.colors
    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }

    val isDefaultSelected = selectedItem == defaultItem
    val displayText = if (isDefaultSelected && !placeholder.isNullOrBlank()) placeholder else itemText(selectedItem)
    val customSelectedColor = itemColor?.invoke(selectedItem)?.takeUnless { it == Color.Unspecified }
    val textColor = when {
        !enabled -> colors.textMuted
        isDefaultSelected -> colors.textSecondary
        customSelectedColor != null -> customSelectedColor
        else -> colors.textPrimary
    }
    val canExpand = enabled && items.isNotEmpty()

    Box(
        modifier = modifier
            .width(KNetDropdownDefaults.DefaultWidth)
            .onSizeChanged { anchorWidthPx = it.width }
    ) {
        KNetDropdownAnchor(
            text = displayText,
            expanded = expanded,
            enabled = canExpand,
            size = size,
            textColor = textColor,
            fontWeight = if (isDefaultSelected) FontWeight.Medium else FontWeight.SemiBold,
            onExpandedChange = { expanded = it }
        )

        KNetDropdownPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchorWidthPx = anchorWidthPx,
            focusable = true
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = KNetDropdownDefaults.MaxMenuHeight)
                    .verticalScroll(rememberScrollState())
            ) {
                items.forEach { item ->
                    val isSelected = item == selectedItem
                    val customColor = itemColor?.invoke(item)?.takeUnless { it == Color.Unspecified }
                    KNetDropdownMenuItem(
                        text = itemText(item),
                        selected = isSelected,
                        textColor = customColor,
                        onClick = {
                            onItemSelected(item)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Shared visual and interaction owner for anchored KNet dropdown controls.
 *
 * The anchor owns hover, focus, keyboard toggling, stable density, and chevron motion. Selection semantics and
 * popup content remain owned by the single-select or multi-select component using it.
 */
@Composable
internal fun KNetDropdownAnchor(
    text: String,
    expanded: Boolean,
    enabled: Boolean,
    size: KNetDropdownSize,
    textColor: Color,
    fontWeight: FontWeight,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    val shapes = KNetTheme.shapes
    val motion = KNetTheme.motion
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val fieldHeight = KNetDropdownDefaults.fieldHeight(size)
    val chevronWidth = if (size == KNetDropdownSize.Compact) 26.dp else 30.dp
    val active = expanded || focused
    val duration = if (motion.animationsEnabled) motion.durationFast else motion.durationInstant
    val containerColor by animateColorAsState(
        targetValue = if (hovered && enabled) {
            colors.interaction.hoverOverlay.compositeOver(colors.surfaceVariant)
        } else {
            colors.surfaceVariant
        },
        animationSpec = tween(duration),
        label = "dropdownContainer"
    )
    val borderColor by animateColorAsState(
        targetValue = if (active) colors.borderFocused else colors.border,
        animationSpec = tween(duration),
        label = "dropdownBorder"
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(duration),
        label = "dropdownChevron"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(fieldHeight)
            .clip(shapes.medium)
            .background(containerColor)
            .border(1.dp, borderColor, shapes.medium)
            .hoverable(interactionSource, enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button
            ) { onExpandedChange(!expanded) }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !enabled) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.Spacebar, Key.DirectionDown, Key.DirectionUp -> {
                        onExpandedChange(true)
                        true
                    }
                    Key.Escape -> {
                        onExpandedChange(false)
                        true
                    }
                    else -> false
                }
            }
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .handCursor(enabled),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier
                .weight(1f)
                .padding(start = KNetDropdownDefaults.HorizontalPadding),
            color = textColor,
            style = typography.labelMedium,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .height(fieldHeight)
                .width(chevronWidth)
                .background(if (active) colors.interaction.selectedOverlay else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = if (enabled) colors.textSecondary else colors.textMuted,
                modifier = Modifier.size(17.dp).rotate(chevronRotation)
            )
        }
    }
}

private class DropdownPopupPositionProvider(
    private val verticalOffsetPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val alignedX = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.left
            LayoutDirection.Rtl -> anchorBounds.right - popupContentSize.width
        }.coerceIn(0, maxX)
        val belowY = anchorBounds.bottom + verticalOffsetPx
        val aboveY = anchorBounds.top - popupContentSize.height - verticalOffsetPx
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val alignedY = if (belowY + popupContentSize.height <= windowSize.height) belowY else aboveY
        return IntOffset(alignedX, alignedY.coerceIn(0, maxY))
    }
}

/**
 * Intrinsic-measurement-free popup shell shared by regular and searchable dropdowns.
 *
 * Explicit width and height constraints allow lazy result content without nesting a `SubcomposeLayout`
 * inside Material dropdown intrinsic measurement.
 *
 * @param focusable Whether the popup owns focus and consumes outside pointer events. Ordinary selection
 * dropdowns enable this so an anchor click closes once without reaching the anchor again. Searchable
 * comboboxes keep focus in their text field and therefore disable it.
 */
@Composable
internal fun KNetDropdownPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorWidthPx: Int,
    focusable: Boolean,
    content: @Composable () -> Unit
) {
    val colors = KNetTheme.colors
    val shapes = KNetTheme.shapes
    val elevation = KNetTheme.elevation
    val motion = KNetTheme.motion
    val density = LocalDensity.current
    val visibilityState = remember { MutableTransitionState(false) }.apply {
        targetState = expanded
    }
    if (!shouldComposeDropdownPopup(visibilityState.currentState, visibilityState.targetState)) return

    val popupWidth = if (anchorWidthPx > 0) with(density) { anchorWidthPx.toDp() } else KNetDropdownDefaults.DefaultWidth
    val animationDuration = if (motion.animationsEnabled) motion.durationFast else motion.durationInstant
    val positionProvider = remember(density) {
        DropdownPopupPositionProvider(with(density) { 4.dp.roundToPx() })
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = dropdownPopupProperties(focusable)
    ) {
        AnimatedVisibility(
            visibleState = visibilityState,
            enter = fadeIn(
                animationSpec = tween(animationDuration, easing = motion.easingStandard)
            ) + scaleIn(
                initialScale = 0.98f,
                transformOrigin = TransformOrigin(0.5f, 0f),
                animationSpec = tween(animationDuration, easing = motion.easingStandard)
            ),
            exit = fadeOut(
                animationSpec = tween(animationDuration, easing = motion.easingStandard)
            ) + scaleOut(
                targetScale = 0.98f,
                transformOrigin = TransformOrigin(0.5f, 0f),
                animationSpec = tween(animationDuration, easing = motion.easingStandard)
            )
        ) {
            Box(
                modifier = Modifier
                    .width(popupWidth)
                    .heightIn(max = KNetDropdownDefaults.MaxMenuHeight)
                    .shadow(elevation.level3, shapes.medium)
                    .clip(shapes.medium)
                    .background(colors.surfaceVariant)
                    .border(1.dp, colors.borderFocused.copy(alpha = 0.7f), shapes.medium)
                    .padding(vertical = 4.dp)
            ) {
                content()
            }
        }
    }
}

/** Keeps the popup composed until its exit transition has completed. */
internal fun shouldComposeDropdownPopup(currentVisible: Boolean, targetVisible: Boolean): Boolean {
    return currentVisible || targetVisible
}

/** Resolves popup input ownership without changing the shared dismissal contract. */
internal fun dropdownPopupProperties(focusable: Boolean): PopupProperties {
    return PopupProperties(
        focusable = focusable,
        dismissOnBackPress = true,
        dismissOnClickOutside = true
    )
}

@Composable
internal fun KNetDropdownMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    textColor: Color? = null,
    content: (@Composable () -> Unit)? = null
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    val background = when {
        selected -> colors.interaction.selectedOverlay
        highlighted -> colors.interaction.hoverOverlay
        else -> Color.Transparent
    }
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (content != null) {
                        content()
                    } else {
                        Text(
                            text = text,
                            color = textColor ?: if (selected || highlighted) colors.textPrimary else colors.textSecondary,
                            style = typography.bodySmall,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (selected) {
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = KNetDropdownDefaults.HorizontalPadding),
        modifier = modifier
            .fillMaxWidth()
            .height(KNetDropdownDefaults.ItemHeight)
            .background(background)
            .semantics { this.selected = selected }
            .handCursor()
    )
}
