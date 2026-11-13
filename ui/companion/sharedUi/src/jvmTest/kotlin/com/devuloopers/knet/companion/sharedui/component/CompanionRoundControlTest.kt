package com.devuloopers.knet.companion.sharedui.component

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.devuloopers.knet.ui.core.foundation.motion.Motion
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import java.awt.EventQueue
import kotlin.test.*

/** Real control rendering verifies reduced motion and the accessible interaction boundary. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class CompanionRoundControlTest {
    @Test
    fun circleAndCaptionAreOneActionAndBusyStateRemovesTheAction() = EventQueue.invokeAndWait {
        val state = mutableStateOf(CompanionRoundControlState(CompanionControlSymbol.POWER, "Start", "Start inspection"))
        var clicks = 0
        ImageComposeScene(360, 400, Density(1f), content = {
            KNetTheme { CompanionRoundControl(state.value) { clicks++ } }
        }).use { scene ->
            repeat(4) { scene.render(it * 100_000_000L).close() }
            val button = nodes(scene).filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Button }
            assertEquals(1, button.size)
            assertTrue(button.single().config[SemanticsActions.OnClick].action!!.invoke())
            assertEquals(1, clicks)
            state.value = state.value.copy(label = "Starting…", busy = true, enabled = false)
            repeat(4) { scene.render((it + 4) * 100_000_000L).close() }
            assertFalse(nodes(scene).any { it.config.getOrNull(SemanticsActions.OnClick) != null })
            assertEquals(1, nodes(scene).count { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo) != null })
        }
    }

    @Test
    fun progressIsStaticWithReducedMotionAndAnimatesOnlyWhenEnabled() = EventQueue.invokeAndWait {
        for (motionEnabled in listOf(false, true)) {
            ImageComposeScene(360, 400, Density(1f), content = {
                KNetTheme(motion = Motion(animationsEnabled = motionEnabled)) {
                    CompanionRoundControl(CompanionRoundControlState(CompanionControlSymbol.SHIELD, "Checking…", busy = true))
                }
            }).use { scene ->
                repeat(5) { scene.render(it * 100_000_000L).close() }
                val first = scene.render(500_000_000L).use { it.encodeToData()!!.use { data -> data.bytes } }
                val later = scene.render(1_000_000_000L).use { it.encodeToData()!!.use { data -> data.bytes } }
                assertEquals(!motionEnabled, first.contentEquals(later), "Motion enabled: $motionEnabled")
            }
        }
    }

    private fun nodes(scene: ImageComposeScene): List<SemanticsNode> = scene.semanticsOwners.flatMap { descendants(it.rootSemanticsNode) }
    private fun descendants(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descendants)
}
