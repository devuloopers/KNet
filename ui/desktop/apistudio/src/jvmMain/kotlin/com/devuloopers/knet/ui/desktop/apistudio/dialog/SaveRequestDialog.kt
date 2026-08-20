package com.devuloopers.knet.ui.desktop.apistudio.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.sidebar.SidebarFolderItem

enum class CollectionSaveMode {
    EXISTING_COLLECTION,
    NEW_COLLECTION
}

/**
 * Modern Compose Multiplatform Save Request Dialog for assigning a request to a persistent collection.
 *
 * Allows the user to edit the request name and choose whether to save into an existing collection
 * or create a new collection suite.
 *
 * @param defaultName Default request title prefilled in the name input field.
 * @param existingCollections List of current existing collection folders available for selection.
 * @param onDismiss Callback when the dialog is cancelled or closed.
 * @param onConfirm Save callback passing the request name, mode, typed folder destination, and new collection name.
 */
@Composable
fun SaveRequestDialog(
    defaultName: String,
    existingCollections: List<SidebarFolderItem>,
    onDismiss: () -> Unit,
    onConfirm: (
        requestName: String,
        mode: CollectionSaveMode,
        selectedFolder: SidebarFolderItem?,
        newCollectionName: String
    ) -> Unit
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    var requestName by remember(defaultName) { mutableStateOf(defaultName) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    var saveMode by remember {
        mutableStateOf(
            if (existingCollections.isNotEmpty()) CollectionSaveMode.EXISTING_COLLECTION else CollectionSaveMode.NEW_COLLECTION
        )
    }
    var selectedFolder by remember(existingCollections) { mutableStateOf(existingCollections.firstOrNull()) }
    var newCollectionName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(themeColors.surface)
                .border(width = 1.dp, color = themeColors.border, shape = RoundedCornerShape(8.dp))
                .padding(spacing.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                // Dialog Title
                Text(
                    text = "Save Request",
                    style = typography.titleLarge.copy(color = themeColors.textPrimary, fontWeight = FontWeight.Bold)
                )

                // Request Name Input
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = "Request Name",
                        style = typography.caption.copy(color = themeColors.textSecondary, fontWeight = FontWeight.Medium)
                    )
                    KNetTextField(
                        value = requestName,
                        onValueChange = { requestName = it },
                        config = InputFieldConfig(placeholder = "Request Name…"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onKeyEvent { keyEvent ->
                                val isConfirmEnabled = requestName.isNotBlank() && (
                                    saveMode == CollectionSaveMode.EXISTING_COLLECTION && selectedFolder != null ||
                                    saveMode == CollectionSaveMode.NEW_COLLECTION && newCollectionName.isNotBlank()
                                )
                                if (keyEvent.type == KeyEventType.KeyDown && (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)) {
                                    if (isConfirmEnabled) {
                                        onConfirm(requestName, saveMode, selectedFolder, newCollectionName)
                                        true
                                    } else false
                                } else false
                            }
                    )
                }

                // Save Target Destination Section
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        text = "Save to Collection",
                        style = typography.caption.copy(color = themeColors.textSecondary, fontWeight = FontWeight.Medium)
                    )

                    // Option 1: Existing Collection (if any)
                    if (existingCollections.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { saveMode = CollectionSaveMode.EXISTING_COLLECTION }
                        ) {
                            RadioButton(
                                selected = (saveMode == CollectionSaveMode.EXISTING_COLLECTION),
                                onClick = { saveMode = CollectionSaveMode.EXISTING_COLLECTION },
                                colors = RadioButtonDefaults.colors(selectedColor = themeColors.accent)
                            )
                            Text(
                                text = "Existing Collection",
                                style = typography.bodyMedium.copy(color = themeColors.textPrimary),
                                modifier = Modifier.padding(start = spacing.xs)
                            )
                        }

                        if (saveMode == CollectionSaveMode.EXISTING_COLLECTION) {
                            Box(modifier = Modifier.fillMaxWidth().padding(start = 32.dp)) {
                                KNetDropdown(
                                    selectedItem = selectedFolder ?: existingCollections.first(),
                                    items = existingCollections,
                                    onItemSelected = { selectedFolder = it },
                                    itemText = SidebarFolderItem::name,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // Option 2: New Collection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveMode = CollectionSaveMode.NEW_COLLECTION }
                    ) {
                        RadioButton(
                            selected = (saveMode == CollectionSaveMode.NEW_COLLECTION),
                            onClick = { saveMode = CollectionSaveMode.NEW_COLLECTION },
                            colors = RadioButtonDefaults.colors(selectedColor = themeColors.accent)
                        )
                        Text(
                            text = "Create New Collection",
                            style = typography.bodyMedium.copy(color = themeColors.textPrimary),
                            modifier = Modifier.padding(start = spacing.xs)
                        )
                    }

                    if (saveMode == CollectionSaveMode.NEW_COLLECTION) {
                        Box(modifier = Modifier.fillMaxWidth().padding(start = 32.dp)) {
                            KNetTextField(
                                value = newCollectionName,
                                onValueChange = { newCollectionName = it },
                                config = InputFieldConfig(placeholder = "Collection Name…"),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Action Buttons Footer
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
                        Text(text = "Cancel", style = typography.titleSmall)
                    }

                    val isConfirmEnabled = requestName.isNotBlank() && (
                            saveMode == CollectionSaveMode.EXISTING_COLLECTION && selectedFolder != null ||
                                    saveMode == CollectionSaveMode.NEW_COLLECTION && newCollectionName.isNotBlank()
                            )

                    KNetButton(
                        onClick = {
                            if (isConfirmEnabled) {
                                onConfirm(requestName, saveMode, selectedFolder, newCollectionName)
                            }
                        },
                        variant = ButtonVariant.Primary,
                        enabled = isConfirmEnabled
                    ) {
                        Text(text = "Save", style = typography.titleSmall)
                    }
                }
            }
        }
    }
}
