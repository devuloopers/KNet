package com.devuloopers.knet.ui.core.components.dropdown

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.devuloopers.knet.ui.core.components.input.OverflowTextPopupHost
import com.devuloopers.knet.ui.core.components.scrollbar.KNetVerticalScrollbar
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.dimensions.KNetDimensions
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Density presets shared by KNet single-select and multi-select dropdowns. */
enum class KNetDropdownSize {
    Compact,
    Standard,
    Large
}

/** Shared dimensions for compact KNet dropdown fields and menus. */
object KNetDropdownDefaults {
    /** Height used when a dropdown participates in a dense toolbar. */
    val CompactFieldHeight: Dp = KNetDimensions.inputHeightCompact
    /** Height of the anchored selection field. */
    val FieldHeight: Dp = 36.dp
    /** Height used when a dropdown sits beside large request-authoring controls. */
    val LargeFieldHeight: Dp = 40.dp
    /** Minimum height of each popup option. */
    val ItemHeight: Dp = 34.dp
    /** Horizontal inset shared by the field and popup items. */
    val HorizontalPadding: Dp = 10.dp
    /** Spacing between the selected label and chevron when rendered as one centered group. */
    val AnchorContentSpacing: Dp = 8.dp
    /** Maximum popup height before its content scrolls. */
    val MaxMenuHeight: Dp = 248.dp
    /** Minimum content-responsive width for a single-select dropdown. */
    val MinimumWidth: Dp = 72.dp
    /** Maximum content-responsive anchor width before its selected label truncates. */
    val MaximumWidth: Dp = 280.dp
    /** Default width for searchable controls before query results are known. */
    val SearchableWidth: Dp = 120.dp
    /** Default width for multi-select controls whose popup rows include a selection indicator. */
    val MultiSelectWidth: Dp = 148.dp
    /** Horizontal space between a popup label and its selected indicator. */
    val ItemContentSpacing: Dp = 8.dp
    /** Size of the popup's selected indicator. */
    val SelectionIndicatorSize: Dp = 15.dp

    /** Resolves the fixed anchor height for [size]. */
    fun fieldHeight(size: KNetDropdownSize): Dp = when (size) {
        KNetDropdownSize.Compact -> CompactFieldHeight
        KNetDropdownSize.Standard -> FieldHeight
        KNetDropdownSize.Large -> LargeFieldHeight
    }

    /** Resolves the chevron region width for [size]. */
    fun chevronWidth(size: KNetDropdownSize): Dp = when (size) {
        KNetDropdownSize.Compact -> 26.dp
        KNetDropdownSize.Standard,
        KNetDropdownSize.Large -> 30.dp
    }

    /**
     * Resolves a stable dropdown width from the widest measured option label.
     *
     * Anchor label padding and chevron chrome are included so changing the current selection never changes width.
     */
    fun contentWidth(widestLabelWidth: Dp, size: KNetDropdownSize): Dp {
        val anchorChrome = HorizontalPadding + AnchorContentSpacing + chevronWidth(size)
        return (widestLabelWidth + anchorChrome).coerceIn(MinimumWidth, MaximumWidth)
    }

    /** Resolves the menu width required by the widest option plus its selected-row indicator chrome. */
    fun menuContentWidth(widestLabelWidth: Dp): Dp {
        val menuChrome = HorizontalPadding * 2 + ItemContentSpacing + SelectionIndicatorSize
        return maxOf(MinimumWidth, widestLabelWidth + menuChrome)
    }
}

/**
 * Compact, anchored KNet selection field.
 *
 * The anchor is stably sized from the complete option set while the popup may grow wider for menu chrome and
 * long labels. Selection is represented independently from display text, and keyboard users can open or close
 * the list with Enter, Space, the arrow keys, or Escape.
 *
 * @param selectedItem Current selection.
 * @param items Available values in visual order.
 * @param onItemSelected Invoked once when a value is chosen.
 * @param modifier Modifier applied to the anchored field; sizing modifiers override the content-responsive width.
 * @param placeholder Text shown for [defaultItem].
 * @param defaultItem Explicit value represented by [placeholder].
 * @param enabled Whether the field can be opened.
 * @param size Fixed anchor density used when calculating height and visual chrome.
 * @param centeredAnchorContent Whether the selected label and chevron form one centered, compact group.
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
    centeredAnchorContent: Boolean = false,
    itemText: (T) -> String = { it.toString() },
    itemColor: ((T) -> Color?)? = null
) {
    val colors = KNetTheme.colors
    val typography = KNetTheme.typography
    val motion = KNetTheme.motion
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val expansionState = rememberDropdownExpansionState()
    val expanded = expansionState.expanded
    var anchorSizePx by remember { mutableStateOf(IntSize.Zero) }

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
    val optionLabels = remember(items, placeholder, itemText) {
        buildList {
            items.forEach { add(itemText(it)) }
            placeholder?.takeIf(String::isNotBlank)?.let(::add)
            if (isEmpty()) add("")
        }
    }
    val widestAnchorLabelWidthPx = remember(optionLabels, typography.labelMedium) {
        optionLabels.maxOf { label ->
            textMeasurer.measure(
                text = label,
                style = typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1
            ).size.width
        }
    }
    val widestMenuLabelWidthPx = remember(optionLabels, typography.bodySmall) {
        optionLabels.maxOf { label ->
            textMeasurer.measure(
                text = label,
                style = typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1
            ).size.width
        }
    }
    val contentResponsiveAnchorWidth = KNetDropdownDefaults.contentWidth(
        widestLabelWidth = with(density) { widestAnchorLabelWidthPx.toDp() },
        size = size
    )
    val contentResponsiveMenuWidth = KNetDropdownDefaults.menuContentWidth(
        widestLabelWidth = with(density) { widestMenuLabelWidthPx.toDp() }
    )
    val widthAnimationDuration = if (motion.animationsEnabled) motion.durationFast else motion.durationInstant
    val animatedAnchorWidth by animateDpAsState(
        targetValue = contentResponsiveAnchorWidth,
        animationSpec = tween(widthAnimationDuration),
        label = "dropdownAnchorWidth"
    )
    val animatedMenuWidth by animateDpAsState(
        targetValue = contentResponsiveMenuWidth,
        animationSpec = tween(widthAnimationDuration),
        label = "dropdownMenuWidth"
    )

    Box(
        modifier = modifier
            .width(animatedAnchorWidth)
            .onSizeChanged { anchorSizePx = it }
    ) {
        KNetDropdownAnchor(
            text = displayText,
            expanded = expanded,
            enabled = canExpand,
            size = size,
            textColor = textColor,
            fontWeight = if (isDefaultSelected) FontWeight.Medium else FontWeight.SemiBold,
            centeredContent = centeredAnchorContent,
            onToggle = { expansionState.toggle() },
            onOpen = expansionState::open,
            onClose = expansionState::close,
        )

        KNetDropdownPopup(
            expanded = expanded,
            onDismissRequest = expansionState::dismissFromPopup,
            anchorSizePx = anchorSizePx,
            preferredMenuWidth = animatedMenuWidth
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
                        val isSelected = item == selectedItem
                        val customColor = itemColor?.invoke(item)?.takeUnless { it == Color.Unspecified }
                        KNetDropdownMenuItem(
                            text = itemText(item),
                            selected = isSelected,
                            textColor = customColor,
                            onClick = {
                                onItemSelected(item)
                                expansionState.close()
                            }
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
    centeredContent: Boolean = false,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
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
    val chevronWidth = KNetDropdownDefaults.chevronWidth(size)
    val active = expanded || focused
    val anchorTextStyle = typography.labelMedium.copy(
        color = textColor,
        fontWeight = fontWeight
    )
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
            ) { onToggle() }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !enabled) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.Spacebar, Key.DirectionDown, Key.DirectionUp -> {
                        onOpen()
                        true
                    }
                    Key.Escape -> {
                        onClose()
                        true
                    }
                    else -> false
                }
            }
            .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
            .handCursor(enabled),
        horizontalArrangement = if (centeredContent) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OverflowTextPopupHost(
            text = text,
            textStyle = anchorTextStyle,
            enabled = text.isNotEmpty(),
            modifier = if (centeredContent) {
                Modifier
            } else {
                Modifier
                    .weight(1f)
                    .padding(
                        start = KNetDropdownDefaults.HorizontalPadding,
                        end = KNetDropdownDefaults.AnchorContentSpacing
                    )
            },
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text,
                style = anchorTextStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (centeredContent) {
            Spacer(modifier = Modifier.width(KNetDropdownDefaults.AnchorContentSpacing))
        }
        Box(
            modifier = if (centeredContent) {
                Modifier.size(17.dp)
            } else {
                Modifier
                    .height(fieldHeight)
                    .width(chevronWidth)
                    .background(if (active) colors.interaction.selectedOverlay else Color.Transparent)
            },
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

private class DropdownMenuPopupPositionProvider(
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
        val alignedY = when (
            dropdownPopupVerticalPlacement(
                anchorBounds = anchorBounds,
                windowHeight = windowSize.height,
                menuHeight = popupContentSize.height,
                verticalOffset = verticalOffsetPx,
            )
        ) {
            DropdownPopupVerticalPlacement.Below -> belowY
            DropdownPopupVerticalPlacement.Above -> aboveY
        }
        return IntOffset(alignedX, alignedY.coerceIn(0, maxY))
    }
}

/** Vertical side selected for a dropdown menu relative to its anchor. */
internal enum class DropdownPopupVerticalPlacement {
    Below,
    Above
}

/**
 * Selects the side that keeps the menu visible, preferring the conventional below-anchor position.
 *
 * When neither side contains the complete menu, the side with more available space wins.
 */
internal fun dropdownPopupVerticalPlacement(
    anchorBounds: IntRect,
    windowHeight: Int,
    menuHeight: Int,
    verticalOffset: Int
): DropdownPopupVerticalPlacement {
    val availableBelow = (windowHeight - anchorBounds.bottom - verticalOffset).coerceAtLeast(0)
    val availableAbove = (anchorBounds.top - verticalOffset).coerceAtLeast(0)
    return if (menuHeight <= availableBelow || availableBelow >= availableAbove) {
        DropdownPopupVerticalPlacement.Below
    } else {
        DropdownPopupVerticalPlacement.Above
    }
}

/**
 * Intrinsic-measurement-free, non-focus-stealing popup shell shared by every KNet dropdown.
 *
 * Explicit width and height constraints allow lazy result content without nesting a `SubcomposeLayout`
 * inside Material dropdown intrinsic measurement. Pointer events outside the menu can reach another anchor,
 * while the shared expansion coordinator guarantees one active popup.
 *
 * @param preferredMenuWidth Optional content-derived menu width. It may exceed the anchor width but is clamped
 * by the popup window's measurement constraints.
 */
@Composable
internal fun KNetDropdownPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorSizePx: IntSize,
    preferredMenuWidth: Dp? = null,
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

    val resolvedAnchorWidthPx = anchorSizePx.width.takeIf { it > 0 }
        ?: with(density) { KNetDropdownDefaults.MinimumWidth.roundToPx() }
    val resolvedPreferredMenuWidthPx = preferredMenuWidth
        ?.let { with(density) { it.roundToPx() } }
        ?.coerceAtLeast(resolvedAnchorWidthPx)
        ?: resolvedAnchorWidthPx
    val popupWidth = with(density) { resolvedPreferredMenuWidthPx.toDp() }
    val animationDuration = if (motion.animationsEnabled) motion.durationFast else motion.durationInstant
    val verticalOffsetPx = with(density) { 4.dp.roundToPx() }
    val menuPositionProvider = remember(verticalOffsetPx) {
        DropdownMenuPopupPositionProvider(verticalOffsetPx)
    }

    Popup(
        popupPositionProvider = menuPositionProvider,
        onDismissRequest = onDismissRequest,
        properties = dropdownPopupProperties()
    ) {
        val menu: @Composable () -> Unit = {
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

        menu()
    }
}

/** Resolves a preferred menu width against its anchor and the popup window's finite constraints. */
internal fun resolvedDropdownPopupWidth(
    anchorWidthPx: Int,
    preferredMenuWidthPx: Int,
    minimumWidthPx: Int,
    maximumWidthPx: Int
): Int {
    val boundedMaximumWidthPx = maximumWidthPx.coerceAtLeast(1)
    return maxOf(anchorWidthPx, preferredMenuWidthPx, minimumWidthPx, 1)
        .coerceAtMost(boundedMaximumWidthPx)
}

/** Keeps the popup composed until its exit transition has completed. */
internal fun shouldComposeDropdownPopup(currentVisible: Boolean, targetVisible: Boolean): Boolean {
    return currentVisible || targetVisible
}

/** Lets pointer input reach another anchor while retaining outside-click and keyboard dismissal. */
internal fun dropdownPopupProperties(): PopupProperties {
    return PopupProperties(
        focusable = false,
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
    val resolvedTextColor = textColor ?: if (selected || highlighted) colors.textPrimary else colors.textSecondary
    val resolvedFontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
    val itemTextStyle = typography.bodySmall.copy(
        color = resolvedTextColor,
        fontWeight = resolvedFontWeight
    )
    DropdownMenuItem(
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KNetDropdownDefaults.ItemContentSpacing)
            ) {
                if (content != null) {
                    Box(modifier = Modifier.weight(1f)) {
                        content()
                    }
                } else {
                    OverflowTextPopupHost(
                        text = text,
                        textStyle = itemTextStyle,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = text,
                            style = itemTextStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (selected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(KNetDropdownDefaults.SelectionIndicatorSize)
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
