package com.devuloopers.knet.ui.desktop.apistudio.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.theme.ApiStudioColors

private val httpMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

/**
 * Custom URL authoring bar matching the exact design structure:
 * Dark Method Dropdown Box + Monospaced URL TextField + Solid Blue Send Button.
 */
@Composable
public fun RequestUrlBar(
    method: String,
    url: String,
    onMethodChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Combined Method Dropdown Box + Monospaced URL Input
        Row(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF11111B))
                .border(width = 1.dp, color = Color(0xFF424750), shape = RoundedCornerShape(4.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Method Dropdown Selector
            KNetDropdown(
                selectedItem = method,
                items = httpMethods,
                onItemSelected = onMethodChanged,
                itemColor = { ApiStudioColors.getMethodTextColor(it) }
            )

            // Divider line between Method and URL input
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .border(width = 0.5.dp, color = Color(0xFF424750))
            )

            // Monospaced URL Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (url.isEmpty()) {
                    Text(
                        text = "https://api.knet.dev/v1/resource",
                        style = typography.codeMedium.copy(color = Color(0xFF8D919B))
                    )
                }
                BasicTextField(
                    value = url,
                    onValueChange = onUrlChanged,
                    textStyle = typography.codeMedium.copy(color = Color(0xFFE2E2E8)),
                    cursorBrush = SolidColor(themeColors.accent),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        }

        // Send Button
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF89B4FA))
                .clickable(onClick = onSendClicked)
                .handCursor()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Send",
                style = typography.titleMedium.copy(
                    color = Color(0xFF001B3C),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Icon(
                imageVector = KNetIcons.Send,
                contentDescription = "Send Request",
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF001B3C)
            )
        }
    }
}
