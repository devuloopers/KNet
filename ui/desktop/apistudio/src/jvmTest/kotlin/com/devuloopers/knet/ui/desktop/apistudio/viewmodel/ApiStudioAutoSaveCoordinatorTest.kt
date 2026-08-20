package com.devuloopers.knet.ui.desktop.apistudio.viewmodel

import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.ui.desktop.apistudio.model.RequestEditorState
import com.devuloopers.knet.ui.desktop.apistudio.model.SessionContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ApiStudioAutoSaveCoordinatorTest {

    @Test
    fun `debounce persists only newest immutable revision for a document`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val persisted = mutableListOf<ApiStudioAutoSaveSnapshot>()
        val coordinator = ApiStudioAutoSaveCoordinator(
            scope = backgroundScope,
            dispatcher = dispatcher,
            persist = persisted::add,
            onFailure = { throw it }
        )
        val context = SessionContext.UnsavedDraft("draft-1")

        coordinator.schedule(snapshot(context, "first", revision = 1L))
        coordinator.schedule(snapshot(context, "latest", revision = 2L))
        runCurrent()
        advanceTimeBy(301L)
        runCurrent()

        assertEquals(listOf("latest"), persisted.map { it.editorState.url })
    }

    @Test
    fun `flush acknowledges persistence before promotion can continue`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val persisted = mutableListOf<ApiStudioAutoSaveSnapshot>()
        val coordinator = ApiStudioAutoSaveCoordinator(
            scope = backgroundScope,
            dispatcher = dispatcher,
            persist = persisted::add,
            onFailure = { throw it }
        )
        val target = snapshot(SessionContext.UnsavedDraft("draft-2"), "final", revision = 3L)

        val completion = async { coordinator.flush(target) }
        advanceUntilIdle()

        assertTrue(completion.await().isSuccess)
        assertEquals(listOf(target), persisted)
    }

    private fun snapshot(
        context: SessionContext,
        url: String,
        revision: Long
    ): ApiStudioAutoSaveSnapshot = ApiStudioAutoSaveSnapshot(
        context = context,
        title = "Request",
        nameOrigin = RequestNameOrigin.GENERATED,
        editorState = RequestEditorState(url = url),
        revision = revision
    )
}
