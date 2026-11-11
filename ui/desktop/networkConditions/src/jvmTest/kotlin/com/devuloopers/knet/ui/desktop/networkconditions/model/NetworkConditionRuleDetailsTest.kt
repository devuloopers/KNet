package com.devuloopers.knet.ui.desktop.networkconditions.model

import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldDefinition
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaFieldId
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaOption
import com.devuloopers.knet.application.contract.breakpoint.ProtocolCriteriaValue
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionInterceptionUnit
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionProtocolDefinition
import com.devuloopers.knet.domain.networkconditions.NetworkConditionProtocolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkConditionRuleDetailsTest {
    private val path = ProtocolCriteriaFieldId("path")
    private val match = ProtocolCriteriaFieldId("match")
    private val definition = NetworkConditionProtocolDefinition(
        protocolId = NetworkConditionProtocolId.HTTP,
        displayName = "HTTP",
        criteriaVersion = 1,
        interceptionUnit = NetworkConditionInterceptionUnit.HTTP_EXCHANGE,
        fields = listOf(
            ProtocolCriteriaFieldDefinition.Text(path, "Path", null, "/video/"),
            ProtocolCriteriaFieldDefinition.Choice(
                id = match, label = "Path match", description = null,
                options = listOf(ProtocolCriteriaOption("exact", "Exact path"), ProtocolCriteriaOption("prefix", "Path prefix")),
                defaultValue = "exact",
            ),
        ),
    )

    @Test
    fun `transport and missing extensions are not misrepresented as semantic matches`() {
        assertEquals("All traffic", networkConditionRuleDetails(null, emptyList(), true).protocol)
        val unavailable = networkConditionRuleDetails(null, emptyList(), false)
        assertEquals("Unavailable protocol", unavailable.protocol)
        assertTrue(unavailable.criteria.contains("review"))
    }

    @Test
    fun `exact and prefix rules keep distinguishable human readable choice labels`() {
        val values = listOf(ProtocolCriteriaValue(path, "/video/"), ProtocolCriteriaValue(match, "prefix"))
        val prefix = networkConditionRuleDetails(definition, values, false)
        val exact = networkConditionRuleDetails(definition, values.dropLast(1) + ProtocolCriteriaValue(match, "exact"), false)
        assertEquals("HTTP", prefix.protocol)
        assertEquals("Path: /video/ · Path match: Path prefix", prefix.criteria)
        assertTrue(exact.criteria.contains("Exact path"))
    }

    @Test
    fun `missing blank and unsupported fields do not invent match conditions`() {
        val result = networkConditionRuleDetails(
            definition,
            listOf(ProtocolCriteriaValue(path, "  "), ProtocolCriteriaValue(match, "unsupported"),
                ProtocolCriteriaValue(ProtocolCriteriaFieldId("unknown"), "private payload")),
            false,
        )
        assertEquals("No criteria to display", result.criteria)
        assertFalse(result.criteria.contains("private payload"))
    }

    @Test
    fun `complete long criteria remain available to the table overflow preview`() {
        val longPath = "/" + "segment/".repeat(100)
        val result = networkConditionRuleDetails(definition, listOf(ProtocolCriteriaValue(path, longPath)), false)
        assertTrue(result.criteria.contains(longPath))
    }
}
