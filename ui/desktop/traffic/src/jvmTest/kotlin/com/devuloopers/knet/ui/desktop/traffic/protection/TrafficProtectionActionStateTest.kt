package com.devuloopers.knet.ui.desktop.traffic.protection

import com.devuloopers.knet.application.usecase.protectedtraffic.ProtectedTrafficFlowHandling
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.traffic.id.OpaqueFlowId
import com.devuloopers.knet.ui.desktop.traffic.model.*
import kotlin.test.*

/** Authoritative rule selection is independent of transient messages and inherited handling. */
class TrafficProtectionActionStateTest {
    private fun saved(action:ProtectedTrafficAction):TrafficProtectionState {
        val fixture=ProtectionFixture()
        val rule=ProtectedTrafficRule(ProtectedTrafficRuleId("saved"),action=action,
            destination=ProtectedDestinationSelector.parse("play-fe.googleapis.com"),port=443,transport=ProtectedTransportProtocol.TCP)
        return TrafficProtectionState(fixture.captured.id,ProtectedTrafficFlowHandling(fixture.captured.id,
            ProtectedTrafficDecision(action,ProtectedTrafficDecisionEvidence.Rule(rule.id,null)),true,rule,rule))
    }

    @Test fun `the winning saved option is selected and cannot submit again`() {
        listOf(ProtectedTrafficAction.TUNNEL,ProtectedTrafficAction.INSPECT).forEach { action ->
            val state=saved(action)
            assertTrue(state.destinationAction(action).selected)
            assertFalse(state.destinationAction(action).enabled)
            val alternative=if(action==ProtectedTrafficAction.TUNNEL)ProtectedTrafficAction.INSPECT else ProtectedTrafficAction.TUNNEL
            assertTrue(state.destinationAction(alternative).enabled);assertFalse(state.destinationAction(alternative).selected)
        }
    }

    @Test fun `disabled deleted and inherited rules are not saved exact selections`() {
        val state=saved(ProtectedTrafficAction.TUNNEL)
        val handling=state.handling!!
        listOf(handling.copy(destinationRule=handling.destinationRule!!.copy(enabled=false)),
            handling.copy(destinationRule=null),
            handling.copy(destinationRule=null,winningRule=null,decision=ProtectedTrafficDecision(ProtectedTrafficAction.TUNNEL,ProtectedTrafficDecisionEvidence.GlobalDefault)))
            .forEach { current ->
                assertFalse(state.copy(handling=current).destinationAction(ProtectedTrafficAction.TUNNEL).selected)
                assertTrue(state.copy(handling=current).destinationAction(ProtectedTrafficAction.TUNNEL).enabled)
            }
    }

    @Test fun `a saved choice overridden by a different winner is never presented as active`() {
        val state=saved(ProtectedTrafficAction.TUNNEL)
        val application=ProtectedTrafficRule(ProtectedTrafficRuleId("application"),action=ProtectedTrafficAction.BLOCK,
            sourceApplication=ProtectedSourceApplicationId("com.example.billing"))
        val handling=state.handling!!.copy(winningRule=application,
            decision=ProtectedTrafficDecision(application.action,ProtectedTrafficDecisionEvidence.Rule(application.id,null)))
        val option=state.copy(handling=handling).destinationAction(ProtectedTrafficAction.TUNNEL)
        assertEquals(TrafficProtectionActionStatus.OVERRIDDEN,option.status)
        assertFalse(option.selected);assertFalse(option.enabled)
    }

    @Test fun `saving an alternative preserves the previous committed selection until policy changes`() {
        val state=saved(ProtectedTrafficAction.TUNNEL).copy(isApplying=true,applyingAction=ProtectedTrafficAction.INSPECT)
        assertTrue(state.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
        assertEquals(TrafficProtectionActionStatus.SAVING,state.destinationAction(ProtectedTrafficAction.INSPECT).status)
        assertFalse(state.destinationAction(ProtectedTrafficAction.INSPECT).enabled)
    }

    @Test fun `feedback cannot manufacture or erase persisted selection`() {
        val state=saved(ProtectedTrafficAction.TUNNEL)
        assertTrue(state.copy(feedback=TrafficProtectionFeedback.Failed("Save failed")).destinationAction(ProtectedTrafficAction.TUNNEL).selected)
        val noRule=state.copy(handling=state.handling!!.copy(destinationRule=null),feedback=TrafficProtectionFeedback.Saved(ProtectedTrafficAction.TUNNEL))
        assertFalse(noRule.destinationAction(ProtectedTrafficAction.TUNNEL).selected)
    }

    @Test fun `loading missing history and another row cannot claim an active saved choice`() {
        val state=saved(ProtectedTrafficAction.TUNNEL)
        listOf(state.copy(isLoading=true),state.copy(handling=null),
            state.copy(handling=state.handling!!.copy(configurationReady=false)),state.copy(flowId=OpaqueFlowId("other"))).forEach {
            val option=it.destinationAction(ProtectedTrafficAction.TUNNEL)
            assertFalse(option.selected);assertFalse(option.enabled)
        }
    }
}
