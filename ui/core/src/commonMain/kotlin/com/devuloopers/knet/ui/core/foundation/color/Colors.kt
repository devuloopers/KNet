package com.devuloopers.knet.ui.core.foundation.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Immutable holder for the full color scheme tokens used across KNet Design System v3.0 (Frozen Specification).
 *
 * @property background Surface 0: Base application window background color (#0B1016).
 * @property surface Surface 1: Navigation rail, sidebar & status bar color (#0B1016).
 * @property surfaceVariant Surface 3: Workspace panel surface color (#111827).
 * @property panelHeader Panel title header background color (#111827).
 * @property border Structural panel and container border color (#1F2937).
 * @property borderFocused Border color when an element has input focus (#3B82F6).
 * @property textPrimary Highest contrast text color for primary content (#E5E7EB).
 * @property textSecondary Medium contrast text color for supporting labels (#9CA3AF).
 * @property textMuted Low contrast text color for hints and disabled content (#6B7280).
 * @property accent Primary brand accent blue color (#3B82F6).
 * @property semantic Semantic status colors for success, error, warning, info.
 * @property interaction Hover, selected, and active state overlay colors.
 */
@Immutable
data class Colors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val panelHeader: Color,
    val border: Color,
    val borderFocused: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val semantic: SemanticColors,
    val interaction: InteractionColors
)

/**
 * Semantic status color tokens.
 */
@Immutable
data class SemanticColors(
    val success: Color,
    val successContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color
)

/**
 * Interactive state overlay tokens.
 */
@Immutable
data class InteractionColors(
    val hoverOverlay: Color,
    val selectedOverlay: Color,
    val pressedOverlay: Color,
    val focusRing: Color
)

/**
 * Default Dark Mode Palette — KNet Desktop v3.0 Master Design System.
 */
val KNetDarkColors: Colors = Colors(
    background = Color(0xFF0B1016),      // Surface 0: Application Base
    surface = Color(0xFF0B1016),         // Surface 1: Navigation & Sidebar
    surfaceVariant = Color(0xFF111827),  // Surface 3: Panels & Inspector
    panelHeader = Color(0xFF111827),     // Surface 2: Toolbars & Headers
    border = Color(0xFF1F2937),
    borderFocused = Color(0xFF3B82F6),
    textPrimary = Color(0xFFE5E7EB),
    textSecondary = Color(0xFF9CA3AF),
    textMuted = Color(0xFF6B7280),
    accent = Color(0xFF3B82F6),
    semantic = SemanticColors(
        success = Color(0xFF22C55E),
        successContainer = Color(0x2622C55E),
        error = Color(0xFFEF4444),
        errorContainer = Color(0x26EF4444),
        warning = Color(0xFFEAB308),
        warningContainer = Color(0x26EAB308),
        info = Color(0xFF3B82F6),
        infoContainer = Color(0x263B82F6)
    ),
    interaction = InteractionColors(
        hoverOverlay = Color(0x1AFFFFFF),
        selectedOverlay = Color(0x263B82F6),
        pressedOverlay = Color(0x33FFFFFF),
        focusRing = Color(0xFF3B82F6)
    )
)

/**
 * Default Light Mode Palette derived from token hierarchy.
 */
val KNetLightColors: Colors = Colors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFFF1F5F9),
    panelHeader = Color(0xFFF8FAFC),
    border = Color(0xFFE2E8F0),
    borderFocused = Color(0xFF2563EB),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF94A3B8),
    accent = Color(0xFF2563EB),
    semantic = SemanticColors(
        success = Color(0xFF16A34A),
        successContainer = Color(0x1F16A34A),
        error = Color(0xFFDC2626),
        errorContainer = Color(0x1FDC2626),
        warning = Color(0xFFCA8A04),
        warningContainer = Color(0x1FCA8A04),
        info = Color(0xFF2563EB),
        infoContainer = Color(0x1F2563EB)
    ),
    interaction = InteractionColors(
        hoverOverlay = Color(0x0A000000),
        selectedOverlay = Color(0x1F2563EB),
        pressedOverlay = Color(0x1A000000),
        focusRing = Color(0xFF2563EB)
    )
)
