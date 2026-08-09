package com.devuloopers.knet.ui.desktop.apistudio.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.input.KNetInputField
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Dialog component for renaming an existing saved request item within a collection.
 *
 * @param currentName Initial/current name of the saved request.
 * @param onDismiss Callback executed when the dialog is dismissed or cancelled.
 * @param onConfirm Callback executed with the new request name when submitted.
 */
@Composable
public fun RenameRequestDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    var textFieldValue by remember(currentName) {
        mutableStateOf(
            TextFieldValue(
                text = currentName,
                selection = TextRange(0, currentName.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(themeColors.surfaceVariant)
                .border(1.dp, themeColors.border, RoundedCornerShape(12.dp))
                .padding(spacing.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(
                    text = "Rename Request",
                    style = typography.titleMedium.copy(
                        color = themeColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = "Request Name",
                        style = typography.caption.copy(color = themeColors.textSecondary, fontWeight = FontWeight.Medium)
                    )
                    KNetInputField(
                        value = textFieldValue,
                        onValueChange = { textFieldValue = it },
                        placeholder = "Request Name",
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KNetButton(
                        onClick = onDismiss,
                        variant = ButtonVariant.Secondary,
                        modifier = Modifier.padding(end = spacing.sm)
                    ) {
                        Text("Cancel")
                    }
                    KNetButton(
                        onClick = {
                            if (textFieldValue.text.isNotBlank()) {
                                onConfirm(textFieldValue.text.trim())
                            }
                        },
                        variant = ButtonVariant.Primary,
                        enabled = textFieldValue.text.isNotBlank() && textFieldValue.text.trim() != currentName
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
