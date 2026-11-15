package com.devuloopers.knet.ui.core.components.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.PopupPositionProvider
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Button-anchored themed nested menu with the same panels/keyboard navigation as context menus.
 * [ownerKey] closes stale menus when the associated execution changes; leaf callbacks freeze at opening.
 */
@Composable
fun KNetActionMenu(items: List<ContextMenuEntry>, ownerKey: Any?, enabled: Boolean = true,
    modifier: Modifier = Modifier, content: @Composable (open: () -> Unit) -> Unit) {
    var snapshot by remember(ownerKey) { mutableStateOf<List<ContextMenuEntry>>(emptyList()) }
    LaunchedEffect(enabled) { if (!enabled) snapshot = emptyList() }
    val gap = with(LocalDensity.current) { KNetTheme.spacing.xs.roundToPx() }
    Box(modifier) {
        content { if (enabled && items.isNotEmpty()) snapshot = items.toList() }
        if (enabled && snapshot.isNotEmpty()) KNetMenuPopup(snapshot, remember(gap) { AnchoredActionMenuPosition(gap) }) { snapshot = emptyList() }
    }
}

/** Anchors below the trigger's trailing edge, flipping above and clamping to the available window. */
internal class AnchoredActionMenuPosition(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val horizontal = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val below = anchorBounds.bottom + gap
        val vertical = if (below + popupContentSize.height <= windowSize.height) below else anchorBounds.top - popupContentSize.height - gap
        return IntOffset(horizontal.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            vertical.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }
}
