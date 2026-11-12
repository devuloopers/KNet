package com.devuloopers.knet.ui.desktop.httppanel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.use
import com.devuloopers.knet.engine.formatter.model.BodyFormat
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.components.*
import com.devuloopers.knet.ui.desktop.httppanel.model.*
import java.io.File
import java.awt.EventQueue
import kotlin.test.*

/** The shared body viewer supports both explicit query navigation and its existing local tab behavior. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class GraphQLBodyViewerNavigationTest {
    private val format = BodyFormat.GraphQL("Query", "GetAccount", "query GetAccount { account { id } }", "{\"id\":1}", "{\"client\":\"test\"}")

    @Test fun `controlled selection selects query without remounting the sub tabs`() = EventQueue.invokeAndWait {
        val selected = mutableStateOf(GraphQLBodySubTab.VARIABLES)
        ImageComposeScene(680, 700, content = { KNetTheme {
            GraphQLBodyViewer(format, "{}", activeSubTab = selected.value, onSubTabSelected = { selected.value = it })
        }}).use { scene ->
            scene.frame(); val queryNode = scene.tab("Query").id
            assertTrue(scene.tab("Variables").config[SemanticsProperties.Selected])
            selected.value = GraphQLBodySubTab.QUERY; scene.frame()
            assertEquals(queryNode, scene.tab("Query").id)
            assertTrue(scene.tab("Query").config[SemanticsProperties.Selected])
            scene.tab("Extensions").config[SemanticsActions.OnClick].action!!.invoke(); scene.frame()
            assertEquals(GraphQLBodySubTab.EXTENSIONS, selected.value)
            assertTrue(scene.tab("Extensions").config[SemanticsProperties.Selected])
        }
    }

    @Test fun `uncontrolled viewer keeps working for existing consumers`() = EventQueue.invokeAndWait {
        ImageComposeScene(680, 700, content = { KNetTheme { GraphQLBodyViewer(format, "{}") } }).use { scene ->
            scene.frame(); assertTrue(scene.tab("Query").config[SemanticsProperties.Selected])
            scene.tab("Variables").config[SemanticsActions.OnClick].action!!.invoke(); scene.frame()
            assertTrue(scene.tab("Variables").config[SemanticsProperties.Selected])
        }
    }

    @Test fun `an empty resolved query never enables navigation and preparing remains explicit`() {
        assertEquals(GraphQLQueryAvailability.UNAVAILABLE,
            PayloadInspectionSpec(resolvedFormat = format.copy(queryText = "")).graphQLQueryAvailability)
        assertEquals(GraphQLQueryAvailability.PREPARING,
            PayloadInspectionSpec(resolvedFormat = format, isPreparing = true).graphQLQueryAvailability)
    }

    @Test fun `GET Variables and Extensions render structured multiline JSON in the shared editor`() {
        val prepared = PayloadInspectionSpec.fromGraphQLParameters("GET",
            "/graphql?query=query+BootStrapQuery+%7B+bootstrap+%7B+id+%7D+%7D" +
                "&variables=%7B%22device%22%3A%22ANDROID%22%2C%22language%22%3A%22en%22%2C%22siteId%22%3A%22fixture-id%22%7D" +
                "&extensions=%7B%22client%22%3A%7B%22name%22%3A%22fixture%22%2C%22flags%22%3A%5B%22news%22%2C%22sport%22%5D%7D%7D")
        val projection = prepared.resolvedFormat as BodyFormat.GraphQL
        EventQueue.invokeAndWait {
            val selected = mutableStateOf(GraphQLBodySubTab.VARIABLES)
            ImageComposeScene(680, 700, content = { KNetTheme {
                GraphQLBodyViewer(projection, "", activeSubTab = selected.value, onSubTabSelected = { selected.value = it })
            }}).use { scene ->
                scene.frame()
                assertTrue(scene.nodes().any { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "5 lines" } })
                scene.render(++nanos * 1_000_000_000).use { rendered -> rendered.encodeToData()?.use {
                    File("/tmp/knet-graphql-json-formatting-variables.png").writeBytes(it.bytes)
                }}
                scene.tab("Extensions").config[SemanticsActions.OnClick].action!!.invoke(); scene.frame()
                assertTrue(scene.nodes().any { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "9 lines" } })
                scene.render(++nanos * 1_000_000_000).use { rendered -> rendered.encodeToData()?.use {
                    File("/tmp/knet-graphql-json-formatting-extensions.png").writeBytes(it.bytes)
                }}
            }
        }
    }

    private var nanos = 0L
    private fun ImageComposeScene.frame() { repeat(3) { render(++nanos * 1_000_000_000).close() } }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
        descend(owner.rootSemanticsNode)
    }
    private fun ImageComposeScene.tab(label: String): SemanticsNode = nodes().first { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab &&
        it.config.getOrNull(SemanticsProperties.Text).orEmpty().any { text -> text.text == label } }
}
