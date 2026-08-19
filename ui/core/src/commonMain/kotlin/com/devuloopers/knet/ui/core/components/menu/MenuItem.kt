package com.devuloopers.knet.ui.core.components.menu

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Immutable

/** Immutable desktop application menu action. */
@Immutable
data class MenuItem(
    val label: String,
    val onClick: () -> Unit,
    val isEnabled: Boolean = true,
    val icon: ImageVector? = null,
    val shortcut: String? = null
)
