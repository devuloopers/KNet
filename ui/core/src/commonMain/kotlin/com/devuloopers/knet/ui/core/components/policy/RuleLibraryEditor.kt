package com.devuloopers.knet.ui.core.components.policy

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.ui.core.components.button.*
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Display-only library choice; execution ownership remains in the feature's captured command. */
enum class RuleLibraryLocation {
    /** Available exclusively in the captured workspace. */
    PRIVATE,
    /** Available for independent activation in every workspace. */
    SHARED;

    /** Plain-language availability label reused by editors and all three rule lists. */
    val label: String
        get() = when (this) {
            PRIVATE -> "Only this device"
            SHARED -> "All devices"
        }

    /** Identifies the captured private target without implying activation on other devices. */
    fun labelFor(workspaceName: String): String =
        if (this == PRIVATE) "$label — $workspaceName" else label
}

/** Immutable information shown by every rule editor. No device identity is inferred from these labels. */
data class RuleLibraryEditorState(
    val workspaceName: String,
    val location: RuleLibraryLocation,
    val editing: Boolean,
    val usedBy: List<String> = emptyList(),
    val sharedDefinition: Boolean = false,
)

/**
 * Reusable availability/impact controls. New definitions always enable only their captured workspace.
 * Shared-to-private conversion uses duplication, preserving every existing activation.
 */
@Composable
fun RuleLibraryEditor(
    state: RuleLibraryEditorState,
    onSharedChange: (Boolean) -> Unit,
    onDuplicate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
        LibraryText("Available to")
        KNetDropdown(selectedItem = state.location,
            items = if (state.editing && state.sharedDefinition) listOf(RuleLibraryLocation.SHARED) else RuleLibraryLocation.entries,
            itemText = { it.labelFor(state.workspaceName) },
            onItemSelected = { onSharedChange(it == RuleLibraryLocation.SHARED) },
            modifier = Modifier.fillMaxWidth().testTag("Rule library location"))
        LibraryText(if (state.editing) "Editing for ${state.workspaceName}. Enabled devices stay unchanged."
            else "Enabled only for ${state.workspaceName} after saving.")
        if (state.location == RuleLibraryLocation.SHARED) {
            LibraryText("Other devices can enable it separately.")
            if (state.editing) {
                LibraryText("Enabled for ${state.usedBy.size} ${if (state.usedBy.size == 1) "device" else "devices"}")
                state.usedBy.forEach { LibraryText(it) }
                KNetButton(onClick = onDuplicate, variant = ButtonVariant.Secondary) {
                    Text("Duplicate for this device", maxLines = 1, softWrap = false)
                }
            }
        }

    }
}

@Composable
private fun LibraryText(value: String) {
    Text(value, style = KNetTheme.typography.bodySmall, color = KNetTheme.colors.textSecondary,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

/** Confirms definition deletion across all affected activations; disabling remains a separate row action. */
@Composable
fun RuleLibraryDeleteDialog(affectedWorkspaces: List<String>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    com.devuloopers.knet.ui.core.components.dialog.ConfirmDialog(
        title = "Delete rule?",
        message = if (affectedWorkspaces.isEmpty()) "This removes the saved rule."
            else "This deletes the rule for: ${affectedWorkspaces.joinToString()}. To keep it available, disable it only for this device.",
        onConfirm = onConfirm, onDismissRequest = onDismiss, confirmText = "Delete rule")
}
