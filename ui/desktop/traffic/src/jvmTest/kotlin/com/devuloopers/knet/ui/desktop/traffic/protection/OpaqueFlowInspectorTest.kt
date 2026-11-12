package com.devuloopers.knet.ui.desktop.traffic.protection

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.devuloopers.knet.ui.core.foundation.theme.ThemeMode
import com.devuloopers.knet.ui.core.foundation.motion.Motion
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.devuloopers.knet.application.usecase.protectedtraffic.ProtectedTrafficFlowHandling
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.inspector.OpaqueFlowInspectorPanel
import com.devuloopers.knet.ui.desktop.traffic.inspector.opaqueDiagnostic
import com.devuloopers.knet.ui.desktop.traffic.model.*
import java.awt.EventQueue
import java.io.File
import kotlin.test.*

/** Real rendering and pointer actions verify the approved layout without launching the desktop application. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class OpaqueFlowInspectorTest {
    private var nanos=0L
    private fun ProtectionFixture.state()=TrafficProtectionState(captured.id,ProtectedTrafficFlowHandling(captured.id,
        ProtectedTrafficDecision(ProtectedTrafficAction.INSPECT,ProtectedTrafficDecisionEvidence.GlobalDefault),true))

    @Test fun `automatic tunnelling explains the previous rejection and offers explicit alternatives`() = onUiThread {
        val fixture = ProtectionFixture()
        val flow = fixture.captured.copy(policyAction = OpaqueFlowPolicyAction.TUNNEL, protectionOrigin = ProtectionDecisionOrigin.AUTOMATIC,
            state = OpaqueFlowState.COMPLETED, terminalOutcome = ExchangeTerminalOutcome.Completed)
        val handling = ProtectedTrafficFlowHandling(flow.id,
            ProtectedTrafficDecision(ProtectedTrafficAction.TUNNEL, ProtectedTrafficDecisionEvidence.AutomaticFallback), true,
            automaticFallbackEnabled = true)
        var applied: ProtectedTrafficAction? = null
        scene(flow.toTrafficRowUiState(1), TrafficProtectionState(flow.id, handling), TrafficProtectionActions({ applied = it }, {})).use { scene ->
            scene.frame()
            assertTrue(scene.nodes().any { it.text().startsWith("Automatically tunnelled") })
            assertFalse(scene.nodes().any { it.text() == "Device rejected KNet’s certificate" })
            for ((label, action) in listOf("Inspect next time" to ProtectedTrafficAction.INSPECT,
                "Always tunnel" to ProtectedTrafficAction.TUNNEL, "Block" to ProtectedTrafficAction.BLOCK)) {
                scene.click(label); scene.frame(); assertEquals(action, applied)
            }
            scene.save("automatic-fallback")
        }
        assertEquals("Closed", flow.toTrafficRowUiState(1).displayStatusLabel)
    }

    @Test fun `a saved manual tunnel exposes use default in the same inspector`() = onUiThread {
        val fixture = ProtectionFixture()
        val rule = ProtectedTrafficRule(ProtectedTrafficRuleId("manual"), action = ProtectedTrafficAction.TUNNEL,
            destination = ProtectedDestinationSelector.parse(fixture.captured.serverName!!), port = 443, transport = ProtectedTransportProtocol.TCP)
        val handling = ProtectedTrafficFlowHandling(fixture.captured.id,
            ProtectedTrafficDecision(ProtectedTrafficAction.TUNNEL, ProtectedTrafficDecisionEvidence.Rule(rule.id, null)), true, rule, rule)
        var reset = false
        scene(fixture.captured.toTrafficRowUiState(1), fixture.state().copy(handling = handling),
            TrafficProtectionActions({}, { reset = true })).use { scene ->
            scene.frame(); scene.click("Use default"); scene.frame()
            assertTrue(reset)
            assertTrue(scene.nodes().any { it.text() == "Tunnelling enabled" })
            scene.save("manual-override-reset")
        }
    }

    @Test fun `confirmed rejection reason remains readable with manual inline actions`()=onUiThread {
        val fixture=ProtectionFixture();var applied:ProtectedTrafficAction?=null
        scene(fixture.captured.toTrafficRowUiState(1),fixture.state(),TrafficProtectionActions({applied=it})).use { scene ->
            scene.frame()
            assertTrue(scene.nodes().any {it.text()=="Device rejected KNet’s certificate"})
            assertTrue(scene.nodes().any {it.text()=="Evidence: certificate rejection alert"})
            assertTrue(scene.nodes().any {it.text().startsWith("Possible causes:")})
            assertFalse(scene.nodes().any {it.text().contains("Smart")})
            assertFalse(scene.nodes().any {it.text()=="Source application"})
            scene.click("Always tunnel");scene.frame()
            assertEquals(ProtectedTrafficAction.TUNNEL,applied)
            scene.save("tls-rejected")
        }
    }

    @Test fun `open and closed tunnel badges match the table status without pending request language`()=onUiThread {
        val fixture=ProtectionFixture()
        val open=fixture.captured.copy(state=OpaqueFlowState.ACTIVE,completedAtEpochMillis=null,terminalOutcome=null,
            policyAction=OpaqueFlowPolicyAction.TUNNEL,inspectionFailureReason=null,uploadedBytes=120,downloadedBytes=450)
        listOf(open to "Active",open.copy(state=OpaqueFlowState.COMPLETED,completedAtEpochMillis=10,
            terminalOutcome=ExchangeTerminalOutcome.Completed) to "Closed").forEach { (flow,label) ->
            val row=flow.toTrafficRowUiState(1)
            assertEquals(label,row.displayStatusLabel)
            ImageComposeScene(1680,900,content={KNetTheme {
                androidx.compose.foundation.layout.Row(Modifier.fillMaxSize()) {
                    com.devuloopers.knet.ui.desktop.traffic.table.TrafficTable(
                        transactions=listOf(row),selectedId=row.transactionId,autoScroll=false,onSelectTransaction={},
                        formattedVisibleSize=row.formattedSize,totalAvailableCount=1,modifier=Modifier.weight(2f))
                    OpaqueFlowInspectorPanel(row,fixture.state(),TrafficProtectionActions({}),Modifier.weight(1f))
                }
            }}).use { scene ->
                scene.frame()
                scene.save("tunnel-${label.lowercase()}")
                // Table rows merge cell semantics; each cell still contributes its own text entry.
                val labels=scene.nodes().flatMap {it.config.getOrNull(SemanticsProperties.Text).orEmpty()}.map {it.text}
                assertTrue(labels.count {it==label}>=2,"Both table and inspector must display $label: $labels")
                assertFalse(labels.any {it in listOf("Pending","In Progress")})
                assertTrue("120 B" in labels)
                assertTrue("450 B" in labels)
            }
        }
    }

    @Test fun `technical details start collapsed and policy edits never relabel the captured rejection`()=onUiThread {
        val fixture=ProtectionFixture();val state=mutableStateOf(fixture.state())
        ImageComposeScene(580,1000,content={KNetTheme {
            OpaqueFlowInspectorPanel(fixture.captured.toTrafficRowUiState(1),state.value,TrafficProtectionActions({
                state.value=state.value.copy(handling=state.value.handling!!.copy(decision=ProtectedTrafficDecision(it,
                    ProtectedTrafficDecisionEvidence.Rule(ProtectedTrafficRuleId("saved"),null))),feedback=TrafficProtectionFeedback.Saved(it))
            }))
        }}).use { scene ->
            scene.frame();scene.click("Always tunnel");scene.frame()
            assertTrue(scene.nodes().any {it.text()=="Tunnel · Saved rule"})
            assertTrue(scene.nodes().any {it.text()=="TLS Rejected"})
            assertTrue(scene.nodes().any {it.text().startsWith("Tunnel rule saved")})
            assertFalse(scene.nodes().any {it.text().startsWith("Smart fallback paused")})
            scene.click("Technical details");scene.frame()
            assertTrue(scene.nodes().any {it.text()=="Captured policy action"})
            assertTrue(scene.nodes().any {it.text()=="INSPECT"})
            scene.click("Technical details");scene.frame()
            assertFalse(scene.nodes().any {it.text()=="Captured policy action"})
            scene.click("Inspect next time");scene.frame()
            assertTrue(scene.nodes().any {it.text()=="Inspect · Saved rule"})
        }
    }

    @Test fun `tunnel inactivity explains the deadline with transferred bytes and future handling intact`()=onUiThread {
        val fixture=ProtectionFixture()
        val flow=fixture.captured.copy(state=OpaqueFlowState.FAILED,policyAction=OpaqueFlowPolicyAction.TUNNEL,
            inspectionFailureReason=null,uploadedBytes=11115,downloadedBytes=8966,
            terminalOutcome=ExchangeTerminalOutcome.Failed(TrafficTerminationReason.Transport.READ_TIMED_OUT))
        scene(flow.toTrafficRowUiState(1),fixture.state(),TrafficProtectionActions({})).use { scene ->
            scene.frame()
            assertTrue(scene.nodes().any {it.text()=="Timed Out"})
            assertTrue(scene.nodes().any {it.text()=="Tunnel inactivity timeout"})
            assertTrue(scene.nodes().any {it.text().contains("Earlier app operations may have finished")})
            assertFalse(scene.nodes().any {it.text().contains("Device rejected")})
            assertTrue(scene.nodes().any {it.text()=="10 KB"})
            assertTrue(scene.nodes().any {it.text()=="8 KB"})
            assertTrue(scene.nodes().any {it.text()=="Always tunnel"})
            scene.save("tunnel-inactivity-timeout")
        }
    }

    @Test fun `manual connection actions have no automatic decision controls`()=onUiThread {
        val fixture=ProtectionFixture()
        scene(fixture.captured.toTrafficRowUiState(1),fixture.state(),TrafficProtectionActions({})).use { scene ->
            scene.frame()
            assertTrue(scene.nodes().any {it.text()=="Always tunnel"})
            assertTrue(scene.nodes().any {it.text()=="Inspect next time"})
            assertFalse(scene.nodes().any {it.text().contains("Smart") || it.text().contains("Forget automatic")})
            assertFalse(scene.nodes().any {it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().contains("Connection actions")})
        }
    }

    @Test fun `technical details show recorded close owner reason and time without guessing for old rows`()=onUiThread {
        val fixture=ProtectionFixture()
        val cases=listOf(
            OpaqueFlowClosure(OpaqueFlowCloseInitiator.DEVICE,OpaqueFlowCloseReason.PEER_EOF,1000) to "Device",
            OpaqueFlowClosure(OpaqueFlowCloseInitiator.SERVER,OpaqueFlowCloseReason.PEER_EOF,1000) to "Server",
            OpaqueFlowClosure(OpaqueFlowCloseInitiator.KNET,OpaqueFlowCloseReason.PROXY_STOPPED,1000) to "KNet",
            OpaqueFlowClosure(OpaqueFlowCloseInitiator.UNKNOWN,OpaqueFlowCloseReason.IO_ERROR,1000) to "Unknown",
            null to "Unknown",
        )
        cases.forEachIndexed { index,(closure,label) ->
            val row=fixture.captured.copy(state=OpaqueFlowState.COMPLETED,startedAtEpochMillis=0,completedAtEpochMillis=2000,
                terminalOutcome=ExchangeTerminalOutcome.Completed,
                policyAction=OpaqueFlowPolicyAction.TUNNEL,inspectionFailureReason=null,closure=closure).toTrafficRowUiState(1)
            assertEquals(closure,row.closure)
            val compact=index==1
            scene(row,fixture.state(),TrafficProtectionActions({}),if(compact)320 else 580,
                if(compact)740 else 1000,if(compact)1.25f else 1f).use { scene ->
                scene.frame()
                assertFalse(scene.nodes().any {it.text()=="Closed by"})
                if(compact) {
                    val toggle=scene.nodes().first {it.text()=="Technical details" && it.config.getOrNull(SemanticsActions.OnClick)!=null}
                    assertTrue(toggle.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke())
                } else scene.click("Technical details")
                scene.frame()
                assertTrue(scene.nodes().any {it.text()=="Closed by"})
                assertTrue(scene.nodes().any {it.text()==label})
                assertTrue(scene.nodes().any {it.text()=="Close reason"})
                assertTrue(scene.nodes().any {it.text()==com.devuloopers.knet.ui.desktop.traffic.inspector.closureReasonLabel(closure?.reason)})
                assertTrue(scene.nodes().any {it.text()=="Close observed at"})
                if (closure==null) assertTrue(scene.nodes().any {it.text()=="Unavailable"})
                else {
                    val time=com.devuloopers.knet.ui.core.foundation.time.KNetDateTime.time(closure.observedAtEpochMillis,true)
                    assertTrue(scene.nodes().any {it.text().endsWith(" $time")})
                }
                if(compact) assertTrue(scene.nodes().any {it.config.getOrNull(SemanticsActions.ScrollBy)!=null})
                scene.save("close-provenance-$index")
            }
        }
    }

    @Test fun `selecting another flow resets technical disclosure`()=onUiThread {
        val fixture=ProtectionFixture()
        val row=mutableStateOf(fixture.captured.toTrafficRowUiState(1))
        val state=mutableStateOf(fixture.state())
        ImageComposeScene(580,1000,content={KNetTheme {
            OpaqueFlowInspectorPanel(row.value,state.value,TrafficProtectionActions({}))
        }}).use { scene ->
            scene.frame();scene.click("Technical details");scene.frame()
            assertTrue(scene.nodes().any {it.text()=="Captured policy action"})
            val other=com.devuloopers.knet.traffic.id.OpaqueFlowId("other")
            row.value=row.value.copy(transactionId=other.value)
            state.value=state.value.copy(flowId=other,handling=state.value.handling!!.copy(flowId=other))
            scene.frame()
            assertFalse(scene.nodes().any {it.text()=="Forget automatic decision"})
            assertFalse(scene.nodes().any {it.text()=="Captured policy action"})
        }
    }

    @Test fun `compact inspector with larger text keeps action labels readable and supports narrow overflow`()=onUiThread {
        val fixture=ProtectionFixture()
        listOf(320,240).forEach { width ->
            scene(fixture.captured.toTrafficRowUiState(1),fixture.state(),TrafficProtectionActions({}),width,560,1.25f).use { scene ->
                scene.frame()
                scene.save("compact-$width")
                listOf("Always tunnel","Inspect next time").forEach { label ->
                    val text=scene.nodes().first {it.text()==label && it.config.getOrNull(SemanticsActions.GetTextLayoutResult)!=null}
                    val layouts=mutableListOf<TextLayoutResult>()
                    assertTrue(text.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(layouts))
                    // Text containers round to device pixels; tolerate less than one pixel, never ellipsis or lost glyphs.
                    assertFalse(layouts.any {it.didOverflowHeight || it.isLineEllipsized(0) ||
                        it.multiParagraph.intrinsics.maxIntrinsicWidth > it.size.width + 1f},"Action must retain its full label: $label at $width: " +
                        layouts.joinToString { "size=${it.size}, widthOverflow=${it.didOverflowWidth}, heightOverflow=${it.didOverflowHeight}" })
                }
                assertTrue(scene.nodes().any {it.config.getOrNull(SemanticsActions.ScrollBy)!=null})
                scene.save("compact-$width")
            }
        }
    }

    @Test fun `generic handshake or cancelled tunnel never falsely claims certificate rejection`() {
        val fixture=ProtectionFixture()
        val unknown=fixture.captured.copy(inspectionFailureReason=InspectionFailureReason.UNKNOWN_HANDSHAKE)
        assertEquals("TLS handshake failed",opaqueDiagnostic(unknown.toTrafficRowUiState(1)).title)
        assertNull(opaqueDiagnostic(unknown.toTrafficRowUiState(1)).possibleCauses)
        val cancelled=fixture.captured.copy(policyAction=OpaqueFlowPolicyAction.TUNNEL,state=OpaqueFlowState.CANCELLED,
            terminalOutcome=ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED))
        val diagnosis=opaqueDiagnostic(cancelled.toTrafficRowUiState(1))
        assertFalse(diagnosis.failure);assertEquals("End-to-end encryption preserved",diagnosis.title)
        assertEquals("Tunnel connection failed",opaqueDiagnostic(fixture.captured.copy(policyAction=OpaqueFlowPolicyAction.TUNNEL).toTrafficRowUiState(1)).title)
    }

    @Test fun `loading saving and not ready policy disable mutation controls`()=onUiThread {
        val fixture=ProtectionFixture()
        listOf(fixture.state().copy(isLoading=true),fixture.state().copy(isApplying=true),
            fixture.state().copy(handling=fixture.state().handling!!.copy(configurationReady=false))).forEach { state ->
            scene(fixture.captured.toTrafficRowUiState(1),state,TrafficProtectionActions({})).use { scene ->
                scene.frame()
                val button=scene.nodes().first {it.text()=="Always tunnel" && it.config.getOrNull(SemanticsActions.OnClick)!=null}
                assertNotNull(button.config.getOrNull(SemanticsProperties.Disabled))
            }
        }
    }

    @Test fun `saved options are visibly selected disabled and keyboard alternatives work at compact scale`()=onUiThread {
        val fixture=ProtectionFixture()
        listOf(ThemeMode.Dark,ThemeMode.Light).forEach { theme ->
            listOf(ProtectedTrafficAction.TUNNEL,ProtectedTrafficAction.INSPECT).forEach { action ->
                val rule=ProtectedTrafficRule(ProtectedTrafficRuleId("saved"),action=action,
                    destination=ProtectedDestinationSelector.parse("play-fe.googleapis.com"),port=443,transport=ProtectedTransportProtocol.TCP)
                val state=fixture.state().let {it.copy(handling=it.handling!!.copy(
                    decision=ProtectedTrafficDecision(action,ProtectedTrafficDecisionEvidence.Rule(rule.id,null)),
                    destinationRule=rule,winningRule=rule))}
                val row=fixture.captured.copy(state=OpaqueFlowState.CANCELLED,
                    terminalOutcome=ExchangeTerminalOutcome.Cancelled(TrafficTerminationReason.Transport.DOWNSTREAM_CANCELLED)).toTrafficRowUiState(1)
                var applied:ProtectedTrafficAction?=null
                scene(row,state,TrafficProtectionActions({applied=it}),320,820,1.25f,theme,true).use { scene ->
                    scene.frame()
                    val button=scene.action(action)
                    assertEquals(true,button.config.getOrNull(SemanticsProperties.Selected))
                    assertEquals(Role.RadioButton,button.config.getOrNull(SemanticsProperties.Role))
                    assertNotNull(button.config.getOrNull(SemanticsProperties.Disabled))
                    val label=if(action==ProtectedTrafficAction.TUNNEL)"Tunnelling enabled" else "Inspection enabled"
                    val text=scene.nodes().first {it.text()==label && it.config.getOrNull(SemanticsActions.GetTextLayoutResult)!=null}
                    val layouts=mutableListOf<TextLayoutResult>()
                    assertTrue(text.config.getOrNull(SemanticsActions.GetTextLayoutResult)!!.action!!.invoke(layouts))
                    assertFalse(layouts.any {it.didOverflowHeight || it.isLineEllipsized(0) || it.multiParagraph.intrinsics.maxIntrinsicWidth > it.size.width+1f})
                    scene.pointerClick(button);scene.frame();assertNull(applied)
                    assertTrue(scene.nodes().any {it.text()=="Cancelled"})
                    val alternative=if(action==ProtectedTrafficAction.TUNNEL)ProtectedTrafficAction.INSPECT else ProtectedTrafficAction.TUNNEL
                    val other=scene.action(alternative)
                    assertEquals(false,other.config.getOrNull(SemanticsProperties.Selected))
                    assertNull(other.config.getOrNull(SemanticsProperties.Disabled))
                    assertTrue(assertNotNull(other.config.getOrNull(SemanticsActions.RequestFocus)?.action).invoke())
                    scene.frame();scene.sendKeyEvent(KeyEvent(Key.Enter,KeyEventType.KeyDown));scene.sendKeyEvent(KeyEvent(Key.Enter,KeyEventType.KeyUp));scene.frame()
                    assertEquals(alternative,applied)
                    scene.save("selected-${action.name}-${theme.name}")
                }
            }
        }
    }

    @Test fun `a saved rule overridden by an application rule shows the reason and no false selected option`()=onUiThread {
        val fixture=ProtectionFixture()
        val destination=ProtectedTrafficRule(ProtectedTrafficRuleId("destination"),action=ProtectedTrafficAction.TUNNEL,
            destination=ProtectedDestinationSelector.parse("play-fe.googleapis.com"),port=443,transport=ProtectedTransportProtocol.TCP)
        val application=ProtectedTrafficRule(ProtectedTrafficRuleId("application"),action=ProtectedTrafficAction.BLOCK,
            sourceApplication=ProtectedSourceApplicationId("com.example.billing"))
        val state=fixture.state().let {it.copy(handling=it.handling!!.copy(destinationRule=destination,winningRule=application,
            decision=ProtectedTrafficDecision(application.action,ProtectedTrafficDecisionEvidence.Rule(application.id,null))))}
        scene(fixture.captured.toTrafficRowUiState(1),state,TrafficProtectionActions({})).use { scene ->
            scene.frame()
            assertTrue(scene.nodes().any {it.text()=="Tunnel rule overridden"})
            assertTrue(scene.nodes().any {it.text().contains("overridden by an application rule")})
            assertEquals(false,scene.action(ProtectedTrafficAction.TUNNEL).config.getOrNull(SemanticsProperties.Selected))
            assertNotNull(scene.action(ProtectedTrafficAction.TUNNEL).config.getOrNull(SemanticsProperties.Disabled))
            scene.save("selection-overridden")
        }
    }

    @Test fun `a row transition cannot flash the previous rows active selection`()=onUiThread {
        val fixture=ProtectionFixture()
        val rule=ProtectedTrafficRule(ProtectedTrafficRuleId("saved"),action=ProtectedTrafficAction.TUNNEL,
            destination=ProtectedDestinationSelector.parse("play-fe.googleapis.com"),port=443,transport=ProtectedTransportProtocol.TCP)
        val previous=fixture.state().let {it.copy(handling=it.handling!!.copy(destinationRule=rule,winningRule=rule,
            decision=ProtectedTrafficDecision(rule.action,ProtectedTrafficDecisionEvidence.Rule(rule.id,null))))}
        val row=fixture.captured.toTrafficRowUiState(1).copy(transactionId="different")
        scene(row,previous,TrafficProtectionActions({})).use { scene ->
            scene.frame()
            assertFalse(scene.nodes().any {it.text()=="Tunnelling enabled"})
            assertTrue(scene.nodes().any {it.text()=="Loading…"})
            assertEquals(false,scene.action(ProtectedTrafficAction.TUNNEL).config.getOrNull(SemanticsProperties.Selected))
            assertNotNull(scene.action(ProtectedTrafficAction.TUNNEL).config.getOrNull(SemanticsProperties.Disabled))
        }
    }

    @Test fun `global application and wildcard sources are distinct from an exact saved choice`()=onUiThread {
        val fixture=ProtectionFixture()
        val application=ProtectedTrafficRule(ProtectedTrafficRuleId("application"),action=ProtectedTrafficAction.TUNNEL,
            sourceApplication=ProtectedSourceApplicationId("com.example.billing"))
        val wildcard=ProtectedTrafficRule(ProtectedTrafficRuleId("wildcard"),action=ProtectedTrafficAction.TUNNEL,
            destination=ProtectedDestinationSelector.parse("*.googleapis.com"))
        val base=fixture.state().handling!!
        val cases=listOf(
            base.copy(decision=ProtectedTrafficDecision(ProtectedTrafficAction.TUNNEL,ProtectedTrafficDecisionEvidence.GlobalDefault)) to "Tunnel · Global default",
            base.copy(winningRule=application,decision=ProtectedTrafficDecision(ProtectedTrafficAction.TUNNEL,ProtectedTrafficDecisionEvidence.Rule(application.id,null))) to "Tunnel · Application rule",
            base.copy(winningRule=wildcard,decision=ProtectedTrafficDecision(ProtectedTrafficAction.TUNNEL,ProtectedTrafficDecisionEvidence.Rule(wildcard.id,null))) to "Tunnel · Domain rule",
        )
        cases.forEach { (handling,label) ->
            var applied=false
            scene(fixture.captured.toTrafficRowUiState(1),fixture.state().copy(handling=handling),TrafficProtectionActions({applied=true})).use { scene ->
                scene.frame();assertTrue(scene.nodes().any {it.text()==label})
                val action=scene.action(ProtectedTrafficAction.TUNNEL)
                assertEquals(false,action.config.getOrNull(SemanticsProperties.Selected))
                assertNull(action.config.getOrNull(SemanticsProperties.Disabled))
                scene.click("Always tunnel");scene.frame();assertTrue(applied)
            }
        }
    }

    private fun ImageComposeScene.action(action:ProtectedTrafficAction)=nodes().first {
        it.config.getOrNull(SemanticsProperties.TestTag)=="Protection action ${action.name}"
    }

    private fun scene(row:TrafficRowUiState,state:TrafficProtectionState,actions:TrafficProtectionActions,
        width:Int=580,height:Int=1000,fontScale:Float=1f,theme:ThemeMode=ThemeMode.Dark,reducedMotion:Boolean=false)=ImageComposeScene(width,height,Density(1f,fontScale),content={KNetTheme(themeMode=theme,motion=Motion(animationsEnabled=!reducedMotion)) {
            OpaqueFlowInspectorPanel(row,state,actions)
        }})
    private fun ImageComposeScene.frame() {repeat(3){nanos+=1_000_000_000;render(nanos).close()}}
    private fun ImageComposeScene.nodes():List<SemanticsNode> = semanticsOwners.flatMap {owner ->
        fun descend(node:SemanticsNode):List<SemanticsNode> = listOf(node)+node.children.flatMap(::descend)
        descend(owner.rootSemanticsNode)
    }
    private fun SemanticsNode.text()=config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" "){it.text}
    private fun ImageComposeScene.click(label:String) = pointerClick(nodes().first {node ->
        node.config.getOrNull(SemanticsActions.OnClick)!=null && (node.text()==label || label in node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty())
    })
    private fun ImageComposeScene.pointerClick(node:SemanticsNode) {
        val position=node.boundsInWindow.center
        sendPointerEvent(PointerEventType.Move,position)
        sendPointerEvent(PointerEventType.Press,position,buttons=PointerButtons(isPrimaryPressed=true),button=PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release,position,buttons=PointerButtons(),button=PointerButton.Primary)
    }
    private fun ImageComposeScene.save(name:String) {
        val directory=File("build/reports/traffic-protection-ui").apply {mkdirs()}
        render(++nanos).use {it.encodeToData()?.use {data -> File(directory,"$name.png").writeBytes(data.bytes)}}
    }
    private fun onUiThread(block:()->Unit)=EventQueue.invokeAndWait(block)
}
