package com.devuloopers.knet.ui.desktop.apistudio.view

import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.use
import com.devuloopers.knet.application.contract.graphql.GraphQLManifestImportResult
import com.devuloopers.knet.domain.graphql.*
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Actual desktop toolbar rendering exposes explicit import and separates lookup from request editing. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PersistedQueryToolbarTest {
    @Test fun `unknown query offers import and disables read only view with compact responsive rendering`() = EventQueue.invokeAndWait {
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
            val import = all.first { it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { t -> t.text == "Import manifest" } }
            fun clickable(node: SemanticsNode): SemanticsNode? = if (node.config.contains(SemanticsActions.OnClick)) node else node.parent?.let(::clickable)
            clickable(import)!!.config[SemanticsActions.OnClick].action!!.invoke()
            assertEquals(1, imports)
            scene.render(4_000_000_000).use { rendered -> rendered.encodeToData()?.use {
                File("/tmp/knet-persisted-query-toolbar.png").writeBytes(it.bytes)
            }}
        }
    }
}
