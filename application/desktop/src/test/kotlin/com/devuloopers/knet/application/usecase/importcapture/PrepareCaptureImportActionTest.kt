package com.devuloopers.knet.application.usecase.importcapture

import com.devuloopers.knet.application.contract.importcapture.*
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolRegistry
import com.devuloopers.knet.application.usecase.traffic.*
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.domain.policy.PolicyWorkspace
import com.devuloopers.knet.domain.rules.model.BreakpointPortCriteria
import com.devuloopers.knet.identity.RegisteredDeviceId
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Imported files can supply editor values, never live identities, automatic sends or saved policy. */
class PrepareCaptureImportActionTest {
    @Test fun `all four actions return explicit previews or drafts under the user selected workspace`() = runTest {
        val repo = Repository(); val useCase = service(repo)
        val workspace = PolicyWorkspace.Device(RegisteredDeviceId("selected-device"))
        val studio = useCase.execute(repo.capture, 0, CaptureImportAction.API_STUDIO, workspace) as CaptureImportActionDraft.ApiStudio
        assertEquals("https://api.test:8443/graphql?operationName=Q", studio.request.url)
        assertEquals("{\"query\":\"query Q { hello }\"}", studio.request.bodyPayload)
        assertEquals(RequestBodyType.JSON, studio.request.bodyType)
        assertEquals(listOf("one", "two"), studio.request.headers.filter { it.first == "X-Test" }.map { it.second })
        val intercept = useCase.execute(repo.capture, 0, CaptureImportAction.INTERCEPT, workspace) as CaptureImportActionDraft.Intercept
        assertEquals("https://api.test/graphql", intercept.rule.urlPattern)
        assertEquals(BreakpointPortCriteria.Exact(8443), intercept.rule.portCriteria)
        assertEquals(workspace, intercept.workspace)
        val conditions = useCase.execute(repo.capture, 0, CaptureImportAction.NETWORK_CONDITIONS, workspace) as CaptureImportActionDraft.NetworkConditions
        val protected = useCase.execute(repo.capture, 0, CaptureImportAction.PROTECTED_TRAFFIC, workspace) as CaptureImportActionDraft.ProtectedTraffic
        assertEquals(conditions.target, protected.target)
        assertEquals("api.test", protected.target.normalizedHost); assertEquals(8443, protected.target.port)
        assertEquals(workspace, conditions.workspace); assertEquals(workspace, protected.workspace)
        assertTrue(repo.limits.all { it == 1_048_576 })
    }

    @Test fun `destination parsing preserves default ports IPv6 and rejects ambiguous or unsupported destinations`() = runTest {
        val repo = Repository(); val useCase = service(repo)
        for ((url, host, port) in listOf(Triple("http://api.test/path", "api.test", 80), Triple("https://api.test/path", "api.test", 443),
            Triple("https://[2001:db8::1]:9443/path", "2001:db8:0:0:0:0:0:1", 9443))) {
            repo.record = repo.record.copy(destination = url)
            val draft = useCase.execute(repo.capture, 0, CaptureImportAction.PROTECTED_TRAFFIC, PolicyWorkspace.Other) as CaptureImportActionDraft.ProtectedTraffic
            assertEquals(host, draft.target.normalizedHost); assertEquals(port, draft.target.port)
        }
        for (url in listOf("file:///tmp/capture", "ftp://api.test/file", "https://api.test:0/", "https://api.test:65536/", "https://api.test:-2/", "not a host", "/relative")) {
            repo.record = repo.record.copy(destination = url)
            assertNotNull(useCase.unavailableReason(CaptureImportAction.PROTECTED_TRAFFIC, repo.record), url)
            assertFailsWith<IllegalArgumentException> { useCase.execute(repo.capture, 0, CaptureImportAction.PROTECTED_TRAFFIC, PolicyWorkspace.Other) }
        }
        repo.record = repo.record.copy(destination = "tls.test:443", method = null, request = null)
        assertNull(useCase.unavailableReason(CaptureImportAction.NETWORK_CONDITIONS, repo.record))
        assertNotNull(useCase.unavailableReason(CaptureImportAction.API_STUDIO, repo.record))
        assertNotNull(useCase.unavailableReason(CaptureImportAction.INTERCEPT, repo.record))
    }

    @Test fun `missing partial oversized binary compressed and invalid UTF8 requests cannot become misleading drafts`() = runTest {
        val repo = Repository(); val useCase = service(repo); val original = repo.record
        for (state in ImportedBodyState.entries.filter { it !in setOf(ImportedBodyState.COMPLETE, ImportedBodyState.EMPTY) }) {
            repo.record = original.copy(request = original.request!!.copy(body = original.request.body.copy(state = state)))
            assertNotNull(useCase.unavailableReason(CaptureImportAction.API_STUDIO, repo.record), state.name)
            assertNull(useCase.unavailableReason(CaptureImportAction.INTERCEPT, repo.record))
        }
        repo.record = original.copy(request = original.request!!.copy(body = original.request.body.copy(bytes = 1_048_577)))
        assertNotNull(useCase.unavailableReason(CaptureImportAction.API_STUDIO, repo.record))
        repo.record = original.copy(request = original.request.copy(headers = listOf("Content-Type" to "application/octet-stream"), body = original.request.body.copy(utf8Text = false)))
        assertNotNull(useCase.unavailableReason(CaptureImportAction.API_STUDIO, repo.record))
        repo.record = original.copy(request = original.request.copy(headers = original.request.headers + ("Content-Encoding" to "gzip"), body = original.request.body.copy(decoded = false)))
        assertNotNull(useCase.unavailableReason(CaptureImportAction.API_STUDIO, repo.record))
        repo.record = original.copy(request = original.request.copy(body = original.request.body.copy(bytes = 1)))
        repo.bytes = byteArrayOf(0xff.toByte())
        assertFails { useCase.execute(repo.capture, 0, CaptureImportAction.API_STUDIO, PolicyWorkspace.Other) }
        repo.bytes = byteArrayOf(); repo.truncated = true
        assertFailsWith<IllegalArgumentException> { useCase.execute(repo.capture, 0, CaptureImportAction.API_STUDIO, PolicyWorkspace.Other) }
    }

    @Test fun `decoded request drafts remove old encoding and missing records cannot prepare actions`() = runTest {
        val repo = Repository(); val useCase = service(repo)
        repo.record = repo.record.copy(request = repo.record.request!!.let { it.copy(headers = it.headers + ("Content-Encoding" to "gzip")) })
        val draft = useCase.execute(repo.capture, 0, CaptureImportAction.API_STUDIO, PolicyWorkspace.Other) as CaptureImportActionDraft.ApiStudio
        assertTrue(draft.request.headers.none { it.first.equals("Content-Encoding", true) })
        assertNotNull(useCase.unavailableReason(CaptureImportAction.API_STUDIO, null))
        for (action in CaptureImportAction.entries) assertNotNull(useCase.unavailableReason(action, null))
    }

    @Test fun `dropped paths use the same parser without opening the native picker`() = runTest {
        val repo = Repository(); var picked = 0
        val open = OpenCaptureImportUseCase(repo, CaptureImportFilePicker { picked++; null })
        assertEquals(repo.capture, open.execute("/tmp/capture.har") {})
        assertEquals(listOf("/tmp/capture.har"), repo.opened); assertEquals(0, picked)
        assertNull(open.execute {}); assertEquals(1, picked); assertEquals(1, repo.opened.size)
    }

    private fun service(repo: Repository) = PrepareCaptureImportActionUseCase(repo, BreakpointProtocolRegistry(), NetworkConditionProtocolRegistry())
    private class Repository : CaptureImportRepository {
        val capture = ImportedCapture("private-handle", "session.har", CaptureImportFormat.HAR, 1, 0, emptyList())
        var bytes = "{\"query\":\"query Q { hello }\"}".encodeToByteArray()
        var truncated = false
        val opened = mutableListOf<String>(); val limits = mutableListOf<Int>()
        var record = ImportedRecord(0, "http", "POST", "https://api.test:8443/graphql?operationName=Q", 200, 1000, 25.0,
            ImportedMessage(listOf("Content-Type" to "application/json", "X-Test" to "one", "X-Test" to "two"),
                ImportedBody("body", ImportedBodyState.COMPLETE, bytes.size.toLong(), true, true)), null,
            "{\"deviceId\":\"untrusted-imported-device\",\"action\":\"BLOCK\"}")
        override suspend fun open(path: String, progress: (CaptureImportProgress) -> Unit): ImportedCapture { opened += path; return capture }
        override suspend fun page(id: String, offset: Long, limit: Int) = listOf(record)
        override suspend fun record(id: String, index: Long) = record
        override suspend fun body(id: String, body: ImportedBody, limit: Int): ImportedBodyPreview { limits += limit; return ImportedBodyPreview(bytes.take(limit).toByteArray(), truncated) }
        override suspend fun close(id: String) = Unit
    }
}
