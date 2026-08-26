package com.devuloopers.knet.ui.desktop.apistudio.protocol

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.devuloopers.knet.application.contract.apistudio.ApiStudioEditorId
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/**
 * Prevents a contributed editor's transient/default state from being drawn while another document is loading.
 *
 * Workspace ViewModels restore documents asynchronously. The surrounding surface remains stable until the active
 * ViewModel state belongs to [requestedDocumentId], avoiding a one-frame display of default protocol controls.
 * Materializing the currently visible transient editor is detected from composition history and treated as an
 * in-place identity transition. The editor therefore stays composed while persistence and the shared Collections
 * projection publish the newly assigned document ID.
 */
@Composable
fun ApiStudioWorkspaceContentGate(
    workspaceKey: ApiStudioEditorId,
    requestedDocumentId: String?,
    activeDocumentId: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var transientEditorWasReady by remember(workspaceKey, requestedDocumentId) {
        mutableStateOf(requestedDocumentId == null && activeDocumentId.isBlank())
    }
    SideEffect {
        if (requestedDocumentId == null && activeDocumentId.isBlank()) {
            transientEditorWasReady = true
        }
    }

    Box(modifier = modifier.fillMaxSize().background(KNetTheme.colors.surface)) {
        if (
            isApiStudioWorkspaceContentReady(
                requestedDocumentId = requestedDocumentId,
                activeDocumentId = activeDocumentId,
                transientEditorWasReady = transientEditorWasReady,
            )
        ) {
            content()
        }
    }
}

/** A null shell selection and a transient editor state both use the canonical blank identifier. */
fun isApiStudioWorkspaceContentReady(
    requestedDocumentId: String?,
    activeDocumentId: String,
    transientEditorWasReady: Boolean = false,
): Boolean = requestedDocumentId.orEmpty() == activeDocumentId ||
    requestedDocumentId == null && activeDocumentId.isNotBlank() && transientEditorWasReady
