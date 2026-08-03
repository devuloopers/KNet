package com.devuloopers.knet.ui.core.foundation.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Single entry point for core vector iconography across KNet Design System.
 */
@Immutable
public object KNetIcons {
    public val Search: ImageVector = Icons.Default.Search
    public val Clear: ImageVector = Icons.Default.Clear
    public val Close: ImageVector = Icons.Default.Close
    public val Add: ImageVector = Icons.Default.Add
    public val Delete: ImageVector = Icons.Default.Delete
    public val Edit: ImageVector = Icons.Default.Edit
    public val Check: ImageVector = Icons.Default.Check
    public val Refresh: ImageVector = Icons.Default.Refresh
    public val Play: ImageVector = Icons.Default.PlayArrow
    public val Settings: ImageVector = Icons.Default.Settings
    public val ChevronDown: ImageVector = Icons.Default.KeyboardArrowDown
    public val ChevronRight: ImageVector = Icons.Default.KeyboardArrowRight
    public val Info: ImageVector = Icons.Default.Info
    public val Warning: ImageVector = Icons.Default.Warning
    public val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    public val List: ImageVector = Icons.AutoMirrored.Filled.List
}
