package com.devuloopers.knet.ui.desktop.networkconditions.view

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import androidx.compose.ui.semantics.*
import androidx.compose.ui.input.pointer.*
import com.devuloopers.knet.ui.core.components.policy.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import kotlin.test.*

/** Rendered shared controls retain the captured target and distinguish availability from activation. */
@OptIn(ExperimentalComposeUiApi::class)
class RuleLibraryEditorTest {
    @Test fun privateDefaultAndSharedChoiceKeepCapturedDeviceVisible() = EventQueue.invokeAndWait {
        var state by mutableStateOf(RuleLibraryEditorState("Device A · suffix",RuleLibraryLocation.PRIVATE,false))
        ImageComposeScene(540,420,content={ KNetTheme {
            RuleLibraryEditor(state,{ shared -> state=state.copy(location=if(shared) RuleLibraryLocation.SHARED else RuleLibraryLocation.PRIVATE) }, {})
        } }).use { scene ->
            scene.frame()
            assertTrue(scene.labels().contains("Available to"))
            assertFalse(scene.nodes().any { it.config.getOrNull(SemanticsProperties.TestTag)=="Replace equivalent activation" })
            assertTrue(scene.labels().contains("Only this device — Device A · suffix"))
            scene.activateTag("Rule library location"); scene.frame(); scene.activate("All devices"); scene.frame()
            assertEquals(RuleLibraryLocation.SHARED,state.location)
            assertTrue(scene.labels().any { it=="Enabled only for Device A · suffix after saving." })
            assertTrue(scene.labels().contains("Other devices can enable it separately."))
        }
    }

    @Test fun unsavedPromotionCanReturnPrivateAndSharedDefinitionDisclosesUsageAndDuplication() = EventQueue.invokeAndWait {
        var state by mutableStateOf(RuleLibraryEditorState("Device A",RuleLibraryLocation.SHARED,true,listOf("Device A","Device B"),sharedDefinition=false))
        var duplicated=false
        ImageComposeScene(540,480,content={ KNetTheme {
            RuleLibraryEditor(state,{ shared -> state=state.copy(location=if(shared) RuleLibraryLocation.SHARED else RuleLibraryLocation.PRIVATE) }, { duplicated=true })
        } }).use { scene ->
            scene.frame()
            assertTrue("Enabled for 2 devices" in scene.labels())
            scene.activateTag("Rule library location"); scene.frame(); scene.activate("Only this device — Device A"); scene.frame()
            assertEquals(RuleLibraryLocation.PRIVATE,state.location)
            state=state.copy(location=RuleLibraryLocation.SHARED,sharedDefinition=true); scene.frame()
            scene.activate("Duplicate for this device"); assertTrue(duplicated)
            scene.activateTag("Rule library location"); scene.frame()
            assertFalse(scene.nodes().any { it.label()=="Only this device — Device A" })
        }
    }

    @Test fun replacementConfirmationNamesTheCapturedDeviceAndDisappearsWithoutChangingTheEditor() = EventQueue.invokeAndWait {
        var notice by mutableStateOf<RuleReplacementNoticeState?>(RuleReplacementNoticeState("save-a","Device A"))
        ImageComposeScene(360,100,content={ KNetTheme { RuleReplacementNotice(notice) } }).use { scene ->
            scene.frame()
            assertTrue(scene.labels().contains("Existing rule replaced for Device A."))
            assertTrue(scene.nodes().any { it.config.getOrNull(SemanticsProperties.LiveRegion)==LiveRegionMode.Polite })
            notice=null; scene.frame()
            assertFalse(scene.nodes().any { it.config.getOrNull(SemanticsProperties.TestTag)=="Rule replacement confirmation" })
        }
    }

    private var frameNanos=0L
    private fun ImageComposeScene.frame() { repeat(2) { frameNanos+=1_000_000_000L; render(frameNanos).close() } }
    private fun ImageComposeScene.nodes():List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun children(node:SemanticsNode):List<SemanticsNode> = listOf(node)+node.children.flatMap(::children)
        children(owner.rootSemanticsNode)
    }
    private fun SemanticsNode.label():String = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    private fun ImageComposeScene.labels() = nodes().map { it.label() }
    private fun ImageComposeScene.activate(label:String) {
        val position=nodes().first { it.label()==label }.boundsInWindow.center
        sendPointerEvent(PointerEventType.Move,position)
        sendPointerEvent(PointerEventType.Press,position,buttons=PointerButtons(isPrimaryPressed=true),button=PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release,position,buttons=PointerButtons(),button=PointerButton.Primary)
    }
    private fun ImageComposeScene.activateTag(tag:String) {
        val position=nodes().first { it.config.getOrNull(SemanticsProperties.TestTag)==tag }.boundsInWindow.center
        sendPointerEvent(PointerEventType.Move,position)
        sendPointerEvent(PointerEventType.Press,position,buttons=PointerButtons(isPrimaryPressed=true),button=PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release,position,buttons=PointerButtons(),button=PointerButton.Primary)
    }
}
