package com.devuloopers.knet.ui.desktop.codeeditor.modifier

/** Owner selected for one uninterrupted primary-pointer drag gesture. */
internal enum class EditorPointerDragOwner {
    /** No primary-pointer gesture is active. */
    None,

    /** The editor text surface owns selection and auto-scroll updates. */
    Text,

    /** A vertical or horizontal scrollbar owns the gesture. */
    Scrollbar
}

/**
 * Retains pointer ownership from primary-button press until release.
 *
 * Ownership must be decided only at the initial press. Re-evaluating it as the pointer moves makes a
 * downward text selection stop as soon as it enters the horizontal scrollbar hit zone.
 */
internal class EditorPointerDragOwnership {
    /** Current gesture owner, or [EditorPointerDragOwner.None] while the primary button is released. */
    var owner: EditorPointerDragOwner = EditorPointerDragOwner.None
        private set

    /**
     * Updates ownership from the latest pointer state.
     *
     * @param isPrimaryPressed Whether the primary pointer button is currently pressed.
     * @param isOverScrollbarZone Whether the current pointer position intersects a scrollbar hit zone.
     * @return Stable owner for the current gesture.
     */
    fun update(isPrimaryPressed: Boolean, isOverScrollbarZone: Boolean): EditorPointerDragOwner {
        owner = when {
            !isPrimaryPressed -> EditorPointerDragOwner.None
            owner != EditorPointerDragOwner.None -> owner
            isOverScrollbarZone -> EditorPointerDragOwner.Scrollbar
            else -> EditorPointerDragOwner.Text
        }
        return owner
    }
}

/**
 * Returns whether a pointer starts inside a scrollbar-owned viewport edge.
 *
 * The vertical scrollbar always exists. The bottom edge is scrollbar-owned only when the editor is
 * configured for non-wrapped horizontal scrolling; wrapped editors must leave that edge available
 * to text selection and downward drag auto-scroll.
 *
 * @param pointerX Horizontal pointer coordinate inside the viewport.
 * @param pointerY Vertical pointer coordinate inside the viewport.
 * @param containerWidth Current viewport width.
 * @param containerHeight Current viewport height.
 * @param scrollbarHitZoneWidth Width reserved for easy scrollbar targeting.
 * @param hasHorizontalScrollbar Whether a horizontal scrollbar is rendered.
 * @return `true` when the pointer is inside a rendered scrollbar's hit zone.
 */
internal fun isPointerOverEditorScrollbar(
    pointerX: Float,
    pointerY: Float,
    containerWidth: Float,
    containerHeight: Float,
    scrollbarHitZoneWidth: Float,
    hasHorizontalScrollbar: Boolean
): Boolean {
    val isOverVerticalScrollbar = containerWidth > 0f &&
        pointerX >= containerWidth - scrollbarHitZoneWidth
    val isOverHorizontalScrollbar = hasHorizontalScrollbar &&
        containerHeight > 0f &&
        pointerY >= containerHeight - scrollbarHitZoneWidth
    return isOverVerticalScrollbar || isOverHorizontalScrollbar
}
