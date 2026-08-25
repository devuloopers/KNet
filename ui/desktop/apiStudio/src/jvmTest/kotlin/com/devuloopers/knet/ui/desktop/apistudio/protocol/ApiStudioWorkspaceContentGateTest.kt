package com.devuloopers.knet.ui.desktop.apistudio.protocol

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiStudioWorkspaceContentGateTest {
    @Test
    fun `durable editor content waits for the requested document`() {
        assertFalse(
            isApiStudioWorkspaceContentReady(
                requestedDocumentId = "websocket-request",
                activeDocumentId = "",
            ),
        )
        assertTrue(
            isApiStudioWorkspaceContentReady(
                requestedDocumentId = "websocket-request",
                activeDocumentId = "websocket-request",
            ),
        )
    }

    @Test
    fun `transient editor content is ready only after durable state is cleared`() {
        assertFalse(
            isApiStudioWorkspaceContentReady(
                requestedDocumentId = null,
                activeDocumentId = "previous-request",
            ),
        )
        assertTrue(
            isApiStudioWorkspaceContentReady(
                requestedDocumentId = null,
                activeDocumentId = "",
            ),
        )
    }

    @Test
    fun `materializing a transient editor preserves its existing composition`() {
        assertTrue(
            isApiStudioWorkspaceContentReady(
                requestedDocumentId = null,
                activeDocumentId = "new-unsaved-request",
                transientEditorWasReady = true,
            ),
        )
        assertFalse(
            isApiStudioWorkspaceContentReady(
                requestedDocumentId = null,
                activeDocumentId = "previous-request",
                transientEditorWasReady = false,
            ),
        )
    }

    @Test
    fun `switching from a durable document does not expose its stale content`() {
        assertFalse(
            isApiStudioWorkspaceContentReady(
                requestedDocumentId = null,
                activeDocumentId = "previous-request",
                transientEditorWasReady = false,
            ),
        )
    }
}
