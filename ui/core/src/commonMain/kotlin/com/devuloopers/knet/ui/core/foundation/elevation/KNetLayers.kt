package com.devuloopers.knet.ui.core.foundation.elevation

/**
 * Centralized z-index rendering layers for KNet UI Application Architecture.
 *
 * Prevents arbitrary z-index values across feature modules and guarantees explicit visual hierarchy ordering.
 */
object KNetLayers {
    /** Base content layer hosting workspace panels, editors, tables, and split views. */
    const val Workspace: Float = 0f

    /** Navigation layer hosting the desktop navigation rail and expanded floating panel overlay. */
    const val Navigation: Float = 100f

    /** Floating overlay layer hosting context menus, tooltips, dropdowns, and floating inspectors. */
    const val Overlay: Float = 200f

    /** Modal dialog layer hosting dialog windows, wizards, and settings panels. */
    const val Dialog: Float = 300f

    /** Top notification layer hosting system toasts, progress bars, and alert banners. */
    const val Notification: Float = 400f
}
