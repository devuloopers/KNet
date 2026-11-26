package com.devuloopers.knet.ui.desktop.apistudio.view

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.application.contract.graphql.GraphQLManifestFailure
import com.devuloopers.knet.application.contract.graphql.GraphQLManifestImportResult
import com.devuloopers.knet.domain.graphql.*
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.core.foundation.clipboard.readPlainText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Actual desktop toolbar rendering exposes explicit import and separates lookup from request editing. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class PersistedQueryToolbarTest {
    @Test fun `unknown query offers import and hides read only view with compact responsive rendering`() = EventQueue.invokeAndWait {
        var imports = 0
        val format = BodyFormat.GraphQL("", null, "", "{}", persistedQuery = GraphQLPersistedQuery(GraphQLQueryHash("a".repeat(64))))
        ImageComposeScene(520, 240, content = { KNetTheme {
            PersistedQueryToolbar(format, null, false) { imports++ }
        }}).use { scene ->
            repeat(3) { scene.render((it + 1L) * 1_000_000_000).close() }
            fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap { owner ->
                fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
                descend(owner.rootSemanticsNode)
            }
            val all = nodes()
            assertTrue(all.any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.text == "Query source unavailable" } })
            assertFalse(all.any { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.text == "View query" } })
            val import = all.first { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.text == "Import manifest" } }
            fun clickable(node: SemanticsNode): SemanticsNode? = if (node.config.contains(SemanticsActions.OnClick)) node else node.parent?.let(::clickable)
            clickable(import)!!.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, imports)
            scene.render(4_000_000_000).use { rendered -> rendered.encodeToData()?.use {
                val reports = File("build/reports/persisted-query-strip").apply { mkdirs() }
                File(reports, "source-unavailable.png").writeBytes(it.bytes)
            }}
        }
    }

    @Test fun `compact strip exposes the full hash and responds to manifest results at narrow width`() = EventQueue.invokeAndWait {
        val hash = "a".repeat(56) + "1234abcd"
        val pending = BodyFormat.GraphQL("", null, "", "{}", persistedQuery = GraphQLPersistedQuery(GraphQLQueryHash(hash)))
        val preview = mutableStateOf(pending)
        val result = mutableStateOf<GraphQLManifestImportResult?>(null)
        val importing = mutableStateOf(false)
        val clipboard = object : Clipboard {
            private var entry: ClipEntry? = null
            override suspend fun getClipEntry(): ClipEntry? = entry
            override suspend fun setClipEntry(clipEntry: ClipEntry?) { entry = clipEntry }
        }
        ImageComposeScene(330, 450, Density(1f, 1.15f), coroutineContext = UnconfinedTestDispatcher(), content = {
            KNetTheme { CompositionLocalProvider(LocalClipboard provides clipboard) {
                PersistedQueryToolbar(preview.value, result.value, importing.value) {}
            } }
        }).use { scene ->
            var time = 0L
            fun frame() = repeat(4) { scene.render(++time * 1_000_000_000).close() }
            fun nodes(): List<SemanticsNode> = scene.semanticsOwners.flatMap {
                fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
                descend(it.rootSemanticsNode)
            }
            fun label(value: String) = nodes().firstOrNull { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == value }
            }
            fun click(node: SemanticsNode) {
                val actionNode = generateSequence(node) { it.parent }.first { it.config.contains(SemanticsActions.OnClick) }
                actionNode.config[SemanticsActions.OnClick].action!!.invoke()
                frame()
            }
            frame()
            assertNull(label(hash))
            click(assertNotNull(label("SHA-256")))
            assertNotNull(label(hash))
            click(nodes().first { "Copy SHA-256 hash" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() })
            assertEquals(hash, runBlocking { clipboard.readPlainText() })
            importing.value = true
            frame()
            assertTrue(generateSequence(assertNotNull(label("Importing…"))) { it.parent }.any { it.config.contains(SemanticsProperties.Disabled) })
            importing.value = false
            result.value = GraphQLManifestImportResult.Rejected(GraphQLManifestFailure.HASH_MISMATCH)
            frame()
            assertNotNull(label(GraphQLManifestFailure.HASH_MISMATCH.description))
            preview.value = pending.copy(queryText = "query Example { item { id } }", queryProvenance = GraphQLQueryProvenance.IMPORTED_MANIFEST)
            result.value = GraphQLManifestImportResult.Imported(1)
            frame()
            assertNotNull(label("Imported manifest"))
            assertNotNull(label("Imported 1 verified queries for this endpoint."))
            for (value in listOf("View query", "Import manifest")) {
                val bounds = assertNotNull(label(value)).boundsInWindow
                assertTrue(bounds.width > 40 && bounds.left >= 0 && bounds.right <= 330)
            }
            click(assertNotNull(label("View query")))
            assertNotNull(label("Persisted query · Imported manifest"))
        }
    }
}
