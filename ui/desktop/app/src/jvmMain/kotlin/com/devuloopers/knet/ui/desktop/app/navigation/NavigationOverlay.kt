package com.devuloopers.knet.ui.desktop.app.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.devuloopers.knet.ui.core.foundation.elevation.KNetLayers
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Independent Floating Overlay Panel composable rendered in [KNetLayers.Navigation] layer (z = 100).
 *
 * Primary navigation starts immediately at the top (Traffic, API Studio, Inspector),
 * while KNet branding is relocated to a passive footer at the bottom below Settings.
 *
 * @param navigationState Single source of truth navigation interaction state.
 * @param presentation Animated presentation values (width, alpha, offset).
 * @param currentDestination Currently active screen destination.
 * @param onDestinationSelected Callback when a destination is selected.
 * @param modifier Layout modifier.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun NavigationOverlay(
    navigationState: NavigationState,
    presentation: NavigationPresentation,
    currentDestination: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors

    val topSection = listOf(
        NavigationDestinationInfo(DesktopDestination.Traffic, "Traffic", Icons.Default.WifiTethering, "Ctrl+1"),
        NavigationDestinationInfo(DesktopDestination.ApiStudio, "API Studio", Icons.Default.Navigation, "Ctrl+2")
    )

    val middleSection = listOf(
        NavigationDestinationInfo(DesktopDestination.Certificate, "Certificates", Icons.Default.Lock, "Ctrl+3"),
        NavigationDestinationInfo(DesktopDestination.Scripting, "Scripting", Icons.Default.Code, "Ctrl+4")
    )

    val bottomSection = listOf(
        NavigationDestinationInfo(DesktopDestination.Settings, "Settings", Icons.Default.Settings, "Ctrl+,")
    )

    Column(
        modifier = modifier
            .width(presentation.overlayWidth)
            .fillMaxHeight()
            .zIndex(KNetLayers.Navigation)
            .onPointerEvent(PointerEventType.Enter) { navigationState.onPointerEnter() }
            .onPointerEvent(PointerEventType.Exit) { navigationState.onPointerExit() }
            .onKeyEvent { keyEvent -> navigationState.handleKeyEvent(keyEvent, onDestinationSelected) }
            .shadow(
                elevation = if (navigationState.isExpanded) 8.dp else 0.dp,
                shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)
            )
            .background(
                color = themeColors.surface,
                shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)
            )
            .border(
                border = BorderStroke(1.dp, themeColors.border),
                shape = RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp)
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Top Section (Traffic, API Studio)
        topSection.forEach { info ->
            NavigationRailRowItem(
                info = info,
                isSelected = currentDestination == info.destination,
                isExpanded = navigationState.isExpanded,
                labelAlpha = presentation.labelAlpha,
                labelOffset = presentation.labelOffset,
                onSelect = {
                    navigationState.onItemClick()
                    onDestinationSelected(info.destination)
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 1.dp,
            color = themeColors.border
        )

        // Middle Section (Certificates, Scripting)
        middleSection.forEach { info ->
            NavigationRailRowItem(
                info = info,
                isSelected = currentDestination == info.destination,
                isExpanded = navigationState.isExpanded,
                labelAlpha = presentation.labelAlpha,
                labelOffset = presentation.labelOffset,
                onSelect = {
                    navigationState.onItemClick()
                    onDestinationSelected(info.destination)
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Bottom Section (Settings)
        bottomSection.forEach { info ->
            NavigationRailRowItem(
                info = info,
                isSelected = currentDestination == info.destination,
                isExpanded = navigationState.isExpanded,
                labelAlpha = presentation.labelAlpha,
                labelOffset = presentation.labelOffset,
                onSelect = {
                    navigationState.onItemClick()
                    onDestinationSelected(info.destination)
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            thickness = 1.dp,
            color = themeColors.border
        )

        // Passive Footer: KNet Branding & Version Metadata
        NavigationBrandingFooter(
            isExpanded = navigationState.isExpanded,
            presentation = presentation
        )
    }
}

/**
 * Passive Branding Footer composable rendered below Settings.
 *
 * Visually communicates application information without competing with primary navigation.
 * Uses a fixed height container to ensure zero vertical layout shifts during hover expansion.
 */
@Composable
private fun NavigationBrandingFooter(
    isExpanded: Boolean,
    presentation: NavigationPresentation,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(3.dp))

        // Blue "K" App Logo Tile
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(themeColors.accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "K",
                    style = typography.titleSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }

        if (presentation.labelAlpha > 0.01f) {
            Column(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .offset(x = presentation.labelOffset)
                    .alpha(presentation.labelAlpha)
            ) {
                Text(
                    text = "KNet",
                    style = typography.caption.copy(
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = "Developer Suite v2.0",
                    style = typography.caption.copy(
                        color = themeColors.textSecondary,
                        fontSize = 9.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}
