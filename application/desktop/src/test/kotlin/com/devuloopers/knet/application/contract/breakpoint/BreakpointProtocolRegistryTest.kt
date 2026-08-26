package com.devuloopers.knet.application.contract.breakpoint

import com.devuloopers.knet.application.coordinator.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.domain.rules.model.BreakpointPhase
import com.devuloopers.knet.domain.rules.model.BreakpointProtocolId
import com.devuloopers.knet.domain.rules.model.BreakpointRule
import com.devuloopers.knet.domain.rules.model.ProtocolMatchCriteria
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.HttpRequestSnapshot
import com.devuloopers.knet.traffic.model.http.ApplicationProtocol
import com.devuloopers.knet.traffic.model.http.Authority
import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.HeaderName
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.traffic.model.http.HttpScheme
import com.devuloopers.knet.traffic.model.http.RequestHead
import com.devuloopers.knet.traffic.model.http.RequestTarget
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class BreakpointProtocolRegistryTest {
    @Test
    fun `custom format becomes matchable through registration only`() = runTest {
        val extension = CustomFormatExtension()
        val registry = BreakpointProtocolRegistry(listOf(extension))
        val criteria = requireNotNull(
            registry.createCriteria(
                CUSTOM_PROTOCOL_ID,
                listOf(ProtocolCriteriaValue(CUSTOM_VALUE_FIELD, "pause-me")),
            ),
        )
        val coordinator = BreakpointCoordinator(protocolRegistry = registry)
        coordinator.replaceRules(
            listOf(
                BreakpointRule(
                    id = "custom-rule",
                    phase = BreakpointPhase.REQUEST,
                    protocolCriteria = criteria,
                ),
            ),
        )

        assertSame(
            BreakpointDecision.ContinueUnchanged,
            coordinator.intercept(candidate("different")),
        )
        val decision = async { coordinator.intercept(candidate("pause-me")) }
        runCurrent()

        val pending = coordinator.pendingBreakpoints.value.single()
        assertEquals("custom-rule", pending.ruleId)
        coordinator.resolve(pending.id, BreakpointDecision.ContinueUnchanged)
        assertSame(BreakpointDecision.ContinueUnchanged, decision.await())
    }

    @Test
    fun `duplicate extension identities are rejected at composition`() {
        assertFailsWith<IllegalArgumentException> {
            BreakpointProtocolRegistry(listOf(CustomFormatExtension(), CustomFormatExtension()))
        }
    }

    private fun candidate(value: String): BreakpointCandidate = BreakpointCandidate(
        exchangeId = ExchangeId("exchange-$value"),
        phase = BreakpointPhase.REQUEST,
        request = HttpRequestSnapshot(
            RequestHead(
                method = HttpMethod.POST,
                target = RequestTarget.Absolute(
                    scheme = HttpScheme.fromToken("https"),
                    authority = Authority("example.test"),
                    pathAndQuery = "/custom",
                ),
                protocol = ApplicationProtocol.fromToken("HTTP/1.1"),
                headers = listOf(HeaderField(HeaderName(CUSTOM_HEADER), value)),
            ),
        ),
        startedAtEpochMillis = 1L,
    )
}

private val CUSTOM_PROTOCOL_ID = BreakpointProtocolId("custom-format")
private val CUSTOM_VALUE_FIELD = ProtocolCriteriaFieldId("value")
private const val CUSTOM_HEADER = "X-Custom-Format"

private data class CustomFormatObservation(val value: String) : ProtocolObservation {
    override val protocolId = CUSTOM_PROTOCOL_ID
}

private class CustomFormatExtension : BreakpointProtocolExtension {
    override val definition = BreakpointProtocolDefinition(
        protocolId = CUSTOM_PROTOCOL_ID,
        displayName = "Custom Format",
        criteriaVersion = 1,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Text(
                id = CUSTOM_VALUE_FIELD,
                label = "Value",
                optional = false,
            ),
        ),
    )

    override fun compile(criteria: ProtocolMatchCriteria): CompiledProtocolCriteria? {
        if (criteria.protocolId != CUSTOM_PROTOCOL_ID || criteria.encodedPayload.isBlank()) return null
        return CustomCompiledCriteria(criteria.encodedPayload)
    }

    override fun inspect(input: ProtocolInspectionInput): ProtocolObservation? = input.candidate.request.head.headers
        .firstOrNull { it.name.value.equals(CUSTOM_HEADER, ignoreCase = true) }
        ?.value
        ?.let(::CustomFormatObservation)

    override fun editorValues(criteria: ProtocolMatchCriteria): List<ProtocolCriteriaValue> =
        listOf(ProtocolCriteriaValue(CUSTOM_VALUE_FIELD, criteria.encodedPayload))

    override fun createCriteria(values: List<ProtocolCriteriaValue>): ProtocolMatchCriteria? = values
        .singleOrNull { it.fieldId == CUSTOM_VALUE_FIELD }
        ?.value
        ?.takeIf(String::isNotBlank)
        ?.let { ProtocolMatchCriteria(CUSTOM_PROTOCOL_ID, it) }
}

private class CustomCompiledCriteria(private val expected: String) : CompiledProtocolCriteria {
    override val protocolId = CUSTOM_PROTOCOL_ID

    override fun matches(observation: ProtocolObservation?): Boolean =
        (observation as? CustomFormatObservation)?.value == expected
}
