package com.devuloopers.knet.data.desktop.rules.repository

import com.devuloopers.knet.application.port.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.port.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.port.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
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
import kotlin.test.assertTrue

class RulesRepositoryProtocolCriteriaTest {
    @Test
    fun `Room round trip preserves an extension-owned criteria envelope without protocol branches`() = runTest {
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
                    listOf(
                        ProtocolCriteriaValue(
                            GraphQLBreakpointProtocol.operationNameFieldId,
                            "GetProfile",
                        ),
                    ),
                ),
            )
            val rule = BreakpointRule(
                id = "graphql-persisted",
                urlPattern = "*graphql*",
                method = HttpMethod.POST,
                phase = BreakpointPhase.RESPONSE,
                protocolCriteria = criteria,
            )

            repository.saveRule(rule)

            val restored = repository.rulesFlow.first { rules -> rules.any { it.id == rule.id } }.single()
            assertEquals(criteria, restored.protocolCriteria)
            assertEquals(rule.phase, restored.phase)
            assertTrue(coordinator.requirements.value.hasResponseRules)

            val stored = database.breakpointRuleDao().getAllRules().single()
            assertEquals(GraphQLBreakpointProtocol.id.value, stored.protocolCriteriaType)
            assertEquals(criteria.encodedPayload, stored.protocolCriteriaData)
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }
}
