package com.devuloopers.knet.ui.apistudio.view.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.widgets.KNetDropdown
import com.devuloopers.knet.widgets.KNetInputField

/**
 * Modes available when saving an unsaved session tab.
 */
private enum class SaveSessionMode {
    EXISTING_COLLECTION,
    CREATE_NEW
}

/**
 * Dialog for saving an unsaved request session into a collection.
 * Supports choosing from existing collections or creating a new collection on the fly,
 * with live duplicate request name collision checks.
 *
 * @param request The unsaved request session being saved.
 * @param collections List of active saved collections.
 * @param onSaveToExisting Callback when saving into an existing collection by ID and request name.
 * @param onSaveToNew Callback when creating a new collection by Name and saving the session into it.
 * @param onDismiss Callback when cancelled.
 */
@Composable
fun SaveSessionDialog(
    request: SavedApiRequest,
    collections: List<ApiCollection>,
    onSaveToExisting: (collectionId: String, requestName: String) -> Unit,
    onSaveToNew: (collectionName: String, requestName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember(collections) {
        mutableStateOf(if (collections.isEmpty()) SaveSessionMode.CREATE_NEW else SaveSessionMode.EXISTING_COLLECTION)
    }

    var selectedCollection by remember(collections) { mutableStateOf(collections.firstOrNull()) }
    var newCollectionName by remember { mutableStateOf("") }
    var sessionName by remember(request) { mutableStateOf(request.name) }

    val isNameConflict = remember(mode, selectedCollection, sessionName) {
        if (mode == SaveSessionMode.EXISTING_COLLECTION && selectedCollection != null) {
            selectedCollection!!.folders.flatMap { it.requests }.any {
                it.name.equals(sessionName.trim(), ignoreCase = true)
            }
        } else false
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(400.dp)
                .background(KNetColors.SurfaceDark, RoundedCornerShape(10.dp))
                .border(1.dp, KNetColors.BorderDark, RoundedCornerShape(10.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Save Session to Collection",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Editable Session Name Field
                Text("Saved Session Name:", color = KNetColors.TextSecondary, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(6.dp))
                KNetInputField(
                    value = sessionName,
                    onValueChange = { sessionName = it },
                    placeholder = "Request Name",
                    height = 36.dp,
                    fontSize = 12.sp,
                    cornerRadius = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (collections.isNotEmpty()) {
                    SaveSessionModeTabs(
                        selectedMode = mode,
                        onModeSelected = { mode = it }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                when (mode) {
                    SaveSessionMode.EXISTING_COLLECTION -> {
                        Text("Select Target Collection:", color = KNetColors.TextSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        KNetDropdown(
                            items = collections,
                            selectedItem = selectedCollection,
                            itemLabel = { it.name },
                            onItemSelected = { selectedCollection = it },
                            placeholder = "Select Collection...",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    SaveSessionMode.CREATE_NEW -> {
                        Text("New Collection Name:", color = KNetColors.TextSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        KNetInputField(
                            value = newCollectionName,
                            onValueChange = { newCollectionName = it },
                            placeholder = "e.g. Authentication Services",
                            height = 36.dp,
                            fontSize = 12.sp,
                            cornerRadius = 6.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (isNameConflict) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "⚠️ A request named \"${sessionName.trim()}\" already exists in this collection. Please change the name.",
                        color = Color(0xFFF59E0B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                val canSave = sessionName.isNotBlank() && !isNameConflict && (
                        (mode == SaveSessionMode.EXISTING_COLLECTION && selectedCollection != null) ||
                                (mode == SaveSessionMode.CREATE_NEW && newCollectionName.isNotBlank())
                        )

                SaveSessionActions(
                    canSave = canSave,
                    onConfirm = {
                        if (mode == SaveSessionMode.EXISTING_COLLECTION && selectedCollection != null) {
                            onSaveToExisting(selectedCollection!!.id, sessionName.trim())
                        } else if (mode == SaveSessionMode.CREATE_NEW && newCollectionName.isNotBlank()) {
                            onSaveToNew(newCollectionName.trim(), sessionName.trim())
                        }
                    },
                    onDismiss = onDismiss
                )
            }
        }
    }
}

/**
 * Segmented mode tabs for switching between Existing Collection vs Create New.
 */
@Composable
private fun SaveSessionModeTabs(
    selectedMode: SaveSessionMode,
    onModeSelected: (SaveSessionMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KNetColors.FieldDark, RoundedCornerShape(6.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SaveSessionTabItem(
            label = "Existing Collection",
            isSelected = selectedMode == SaveSessionMode.EXISTING_COLLECTION,
            onClick = { onModeSelected(SaveSessionMode.EXISTING_COLLECTION) },
            modifier = Modifier.weight(1f)
        )
        SaveSessionTabItem(
            label = "Create New",
            isSelected = selectedMode == SaveSessionMode.CREATE_NEW,
            onClick = { onModeSelected(SaveSessionMode.CREATE_NEW) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SaveSessionTabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                if (isSelected) KNetColors.ActiveBlue.copy(alpha = 0.25f) else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .border(
                1.dp,
                if (isSelected) KNetColors.ActiveBlue else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.White else KNetColors.TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SaveSessionActions(
    canSave: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("Cancel", color = KNetColors.TextSecondary, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(
                    if (canSave) KNetColors.ActiveBlue else KNetColors.ActiveBlue.copy(alpha = 0.4f),
                    RoundedCornerShape(6.dp)
                )
                .clickable(enabled = canSave) { onConfirm() }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Save", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
