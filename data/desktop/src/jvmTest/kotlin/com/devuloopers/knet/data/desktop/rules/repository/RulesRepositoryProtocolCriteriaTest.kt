package com.devuloopers.knet.data.desktop.rules.repository

import com.devuloopers.knet.application.coordinator.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.contract.breakpoint.GraphQLBreakpointMatch
import com.devuloopers.knet.application.contract.breakpoint.GraphQLBreakpointScope
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointPortCriteria
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLBreakpointExtension
import com.devuloopers.knet.engine.protocol.inspector.graphql.GraphQLBreakpointProtocol
import com.devuloopers.knet.storage.database.DatabaseFactory
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RulesRepositoryProtocolCriteriaTest {
    @Test fun `Room round trip preserves an extension-owned operation-name criterion`() = roundTrip(exact = false)

    @Test fun `Room round trip preserves the APQ hash and captured operation selector`() = roundTrip(exact = true)

    private fun roundTrip(exact: Boolean) = runTest {
        val root = Files.createTempDirectory("knet-protocol-rule-").toFile()
        val database = DatabaseFactory.create(root.resolve("rules.db"))
        try {
            val extension = GraphQLBreakpointExtension()
            val registry = BreakpointProtocolRegistry(listOf(extension))
            val coordinator = BreakpointCoordinator(protocolRegistry = registry)
            val repository = RulesRepositoryImpl(
                breakpointRuleDao = database.breakpointRuleDao(),
                breakpointControl = coordinator,
                coroutineScope = backgroundScope,
            )
            val criteria = requireNotNull(
                extension.createCriteria(
                    if (exact) listOf(
                        ProtocolCriteriaValue(GraphQLBreakpointMatch.scopeField, GraphQLBreakpointScope.EXACT.token),
                        ProtocolCriteriaValue(GraphQLBreakpointMatch.hashField, "5a".repeat(32)),
                        ProtocolCriteriaValue(GraphQLBreakpointMatch.requestOperationField, "GetProfile"),
                    ) else listOf(ProtocolCriteriaValue(GraphQLBreakpointProtocol.operationNameFieldId, "GetProfile")),
                ),
            )
            val rule = BreakpointRule(
                id = "graphql-persisted",
                urlPattern = if (exact) "https://api.example.test/graphql" else "*graphql*",
                portCriteria = BreakpointPortCriteria.Exact(8443),
                method = HttpMethod.POST,
                phase = BreakpointPhase.RESPONSE,
                protocolCriteria = criteria,
            )

            repository.saveRule(rule)

            val restored = repository.rulesFlow.first { rules -> rules.any { it.id == rule.id } }.single()
            assertEquals(criteria, restored.protocolCriteria)
            assertEquals(exact, registry.compile(restored.protocolCriteria)?.requiresExactEndpoint)
            assertEquals(rule.urlPattern, restored.urlPattern)
            assertEquals(rule.phase, restored.phase)
            assertEquals(BreakpointPortCriteria.Exact(8443), restored.portCriteria)
            // The UI and coordinator collect separate Room flows. Await the runtime projection independently.
            coordinator.requirements.first { it.hasResponseRules }

            val stored = database.breakpointRuleDao().getAllRules().single()
            assertEquals(GraphQLBreakpointProtocol.id.value, stored.protocolCriteriaType)
            assertEquals(criteria.encodedPayload, stored.protocolCriteriaData)
            assertEquals(8443, stored.port)
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }
}
