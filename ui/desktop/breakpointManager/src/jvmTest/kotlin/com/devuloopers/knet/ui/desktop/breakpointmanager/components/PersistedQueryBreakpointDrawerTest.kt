package com.devuloopers.knet.ui.desktop.breakpointmanager.components

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolDefinition
import com.devuloopers.knet.application.contract.breakpoint.GraphQLBreakpointMatch
import com.devuloopers.knet.application.contract.breakpoint.GraphQLBreakpointScope
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaOption
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Checks the existing drawer's GraphQL-only fields and its actual Save action. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class PersistedQueryBreakpointDrawerTest {
    private val hash = "abcd".repeat(16)
    private val definition = BreakpointProtocolDefinition(
        GraphQLBreakpointMatch.protocolId, "GraphQL", 2,
        fields = listOf(ProtocolCriteriaFieldDefinition.Choice(
            GraphQLBreakpointMatch.scopeField, "Match",
            options = GraphQLBreakpointScope.entries.map { ProtocolCriteriaOption(it.token, it.label) },
            defaultValue = GraphQLBreakpointScope.CHOOSE.token,
        )),
    )

    @Test fun `hash-only draft renders scoped criteria and saves the exact captured identity`() {
        val values = listOf(
            ProtocolCriteriaValue(GraphQLBreakpointMatch.scopeField, GraphQLBreakpointScope.EXACT.token),
            ProtocolCriteriaValue(GraphQLBreakpointMatch.hashField, hash),
            ProtocolCriteriaValue(GraphQLBreakpointMatch.requestOperationField, "ReadItem"),
        )
        scene(values) { driver ->
            assertNotNull(driver.label("Exact persisted query"))
            assertNotNull(driver.label("Matches this persisted query only."))
            assertNotNull(driver.label("From request · ReadItem"))
            assertNull(driver.label(hash))
            driver.click("SHA-256")
            assertNotNull(driver.label(hash))
            driver.click("Save and enable")
            assertEquals(values, driver.saved.single())
        }
    }

    @Test fun `scope-free draft disables save until endpoint-wide matching is chosen`() {
        scene(listOf(ProtocolCriteriaValue(GraphQLBreakpointMatch.scopeField, GraphQLBreakpointScope.CHOOSE.token))) { driver ->
            val save = assertNotNull(driver.label("Save and enable"))
            assertTrue(generateSequence(save) { it.parent }.any { it.config.contains(SemanticsProperties.Disabled) })
            driver.click("Choose match scope")
            driver.click("All operations on this endpoint")
            assertNotNull(driver.label("Matches every GraphQL operation within the endpoint and method filters."))
            assertFalse(generateSequence(assertNotNull(driver.label("Save and enable"))) { it.parent }
                .any { it.config.contains(SemanticsProperties.Disabled) })
            driver.click("Save and enable")
            assertEquals(GraphQLBreakpointScope.ENDPOINT.token, driver.saved.single().single().value)
        }
    }

    private fun scene(values: List<ProtocolCriteriaValue>, check: (Driver) -> Unit) = EventQueue.invokeAndWait {
        val saved = mutableListOf<List<ProtocolCriteriaValue>>()
        val draft = BreakpointRule(id = "fixture", urlPattern = "https://api.example.test/graphql", method = HttpMethod.POST,
            protocolCriteria = ProtocolMatchCriteria(GraphQLBreakpointMatch.protocolId, """{"version":2}"""))
        ImageComposeScene(980, 900, Density(1.25f), content = {
            KNetTheme {
                AddEditBreakpointRuleDrawer(visible = true, rule = draft, isEditingExistingRule = false,
                    protocolDefinitions = listOf(definition), initialProtocolValues = values, onDismiss = {},
                    onSave = { _, _, _, _, _, _, criteriaValues -> saved += criteriaValues })
            }
        }).use { scene -> Driver(scene, saved).also { it.frame(); check(it) } }
    }

    private class Driver(private val scene: ImageComposeScene, val saved: List<List<ProtocolCriteriaValue>>) {
        private var nanos = 0L
        fun frame() = repeat(5) { scene.render(++nanos * 500_000_000).close() }
        fun label(value: String): SemanticsNode? {
            fun descend(node: SemanticsNode): Sequence<SemanticsNode> = sequenceOf(node) + node.children.asSequence().flatMap(::descend)
            return scene.semanticsOwners.asSequence().flatMap { descend(it.rootSemanticsNode) }.firstOrNull { node ->
                node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == value }
            }
        }
        fun click(value: String) {
            val target = generateSequence(assertNotNull(label(value), value)) { it.parent }
                .first { it.config.contains(SemanticsActions.OnClick) }
            target.config[SemanticsActions.OnClick].action!!.invoke()
            frame()
        }
    }
}
