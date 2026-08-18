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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.devuloopers.knet.ui.core.foundation.pointer.handCursor
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.theme.ApiStudioColors

private val httpMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")

/**
 * Modern, high-density URL authoring bar featuring:
 * Sleek Method Dropdown Box + Seamless Monospaced URL TextField with Overflow Hover Dialog + Action Send KNetButton with loading support.
 *
 * @param method Active HTTP method string (GET, POST, PUT, DELETE, etc.).
 * @param url Target request URL string.
 * @param onMethodChanged Callback when HTTP method selection changes.
 * @param onUrlChanged Callback when URL text input changes.
 * @param onSendClicked Callback when Send button is clicked.
 * @param onCancelClicked Callback when Cancel button is clicked during in-flight execution.
 * @param onSaveClicked Callback when Save button is clicked.
 * @param isExecuting Reactive execution loading toggle (renders inline spinner on Send button).
 * @param modifier Layout modifier.
 */
@Composable
fun RequestUrlBar(
    method: String,
    url: String,
    onMethodChanged: (String) -> Unit,
    onUrlChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onCancelClicked: (() -> Unit)? = null,
    onSaveClicked: () -> Unit = {},
    isExecuting: Boolean = false,
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
        // Single Seamless Combined URL Bar Container
        Row(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(themeColors.surfaceVariant)
                .border(width = 1.dp, color = themeColors.border, shape = RoundedCornerShape(6.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Method Dropdown Selector Box with left padding
            KNetDropdown(
                selectedItem = method,
                items = httpMethods,
                onItemSelected = onMethodChanged,
                itemColor = { ApiStudioColors.getMethodTextColor(it) },
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp, end = 6.dp)
            )

            // Vertical Divider line between Method and URL input
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 6.dp)
                    .border(width = 0.5.dp, color = themeColors.border.copy(alpha = 0.6f))
            )

            // Seamless Monospaced URL Input Field with automatic overflow hover popup support
            KNetTextField(
                value = url,
                onValueChange = onUrlChanged,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                            if (isExecuting) {
                                onCancelClicked?.invoke()
                            } else {
                                onSendClicked()
                            }
                            true
                        } else {
                            false
                        }
                    },
                config = InputFieldConfig(
                    placeholder = "URL",
                    backgroundColor = Color.Transparent,
                    borderColor = Color.Transparent,
                    showHoverPopupOnOverflow = true
                )
            )
        }

        // Action Save Button
        KNetButton(
            onClick = onSaveClicked,
            variant = ButtonVariant.Secondary,
            modifier = Modifier.height(40.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "Save",
                    style = typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        // Modern Action Send / Cancel Button using KNetButton with native loading state support
        KNetButton(
            onClick = {
                if (isExecuting) {
                    onCancelClicked?.invoke()
                } else {
                    onSendClicked()
                }
            },
            variant = if (isExecuting) ButtonVariant.Secondary else ButtonVariant.Primary,
            loading = isExecuting,
            modifier = Modifier.height(40.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = if (isExecuting) "Cancel" else "Send",
                    style = typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    softWrap = false
                )
                if (!isExecuting) {
                    Icon(
                        imageVector = KNetIcons.Send,
                        contentDescription = "Send Request",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

