package com.devuloopers.knet.ui.desktop.traffic.table

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.testTag
import com.devuloopers.knet.ui.core.foundation.motion.KNetMotion
import com.devuloopers.knet.ui.core.foundation.motion.Motion
import androidx.compose.ui.use
import com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollMode
import com.devuloopers.knet.domain.workspace.model.TrafficAutoScrollSettings
import com.devuloopers.knet.traffic.id.ExchangeId
import com.devuloopers.knet.traffic.model.*
import com.devuloopers.knet.traffic.model.http.*
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.traffic.model.*
import com.devuloopers.knet.ui.desktop.traffic.toolbar.TrafficAutoScrollControl
import java.awt.EventQueue
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*

/** Rendered interactions exercise actual Compose scroll/selection behavior without opening the desktop product. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class TrafficSmartScrollInteractionTest {
    private var nanos = 0L

    @Test fun `smart pauses on wheel scrolling and insertion preserves the visible row anchor`() = EventQueue.invokeAndWait {
        fixture { scene, state ->
            assertTrue(state.settings.value.follows(state.reading.value))
            val list = scene.verticalList()
            scene.sendPointerEvent(PointerEventType.Scroll, Offset(350f, 140f), scrollDelta = Offset(0f, 5f))
            scene.frames()
            assertFalse(state.reading.value.atTop)
            assertFalse(state.settings.value.follows(state.reading.value))
            val anchor = scene.eventLabels().first()
            state.rows.value = listOf(row(31)) + state.rows.value
            state.reading.value = state.reading.value.copy(newCount = 1)
            scene.frames()
            assertEquals(anchor, scene.eventLabels().first(), "Insertion must retain the same row and vertical offset")
            assertEquals("row-29", state.selected.value)
            // A semantic scroll also exercises keyboard/accessibility navigation through the actual lazy list.
            list.config[SemanticsActions.ScrollBy].action!!.invoke(0f, -10_000f)
            scene.frames()
            assertTrue(state.reading.value.atTop)
            assertTrue(state.settings.value.follows(state.reading.value))
        }
    }

    @Test fun `scrollbar dragging pauses smart and remains paused after releasing the thumb`() = EventQueue.invokeAndWait {
        fixture { scene, state ->
            val track = scene.nodes().first { "Scroll traffic rows" in it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty() }.boundsInRoot
            val start = Offset(track.center.x, track.top + 12f)
            scene.sendPointerEvent(PointerEventType.Press, start, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
            scene.frames()
            scene.sendPointerEvent(PointerEventType.Move, Offset(start.x, track.bottom - 30f), buttons = PointerButtons(isPrimaryPressed = true))
            scene.frames()
            scene.sendPointerEvent(PointerEventType.Release, Offset(start.x, track.bottom - 30f), button = PointerButton.Primary)
            scene.frames()
            assertFalse(state.reading.value.atTop)
            assertFalse(state.settings.value.follows(state.reading.value))
        }
    }

    @Test fun `footer pill makes one jump in manual off without changing mode or footer geometry`() = EventQueue.invokeAndWait {
        fixture { scene, state ->
            state.settings.value = TrafficAutoScrollSettings(TrafficAutoScrollMode.MANUAL, false)
            scene.frames()
            scene.verticalList().config[SemanticsActions.ScrollBy].action!!.invoke(0f, 200f)
            scene.frames()
            val footer = scene.footerBounds()
            state.reading.value = state.reading.value.copy(newCount = 24)
            scene.frames()
            assertEquals(footer, scene.footerBounds())
            scene.save("manual-history")
            scene.click(scene.nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "New traffic" })
            scene.frames()
            assertEquals(1, state.jumps)
            assertEquals(false, state.settings.value.manualEnabled)
            assertEquals(TrafficAutoScrollMode.MANUAL, state.settings.value.mode)
            assertEquals(0f, scene.verticalList().config[SemanticsProperties.VerticalScrollAxisRange].value())
            assertFalse(scene.nodes().any { it.config.getOrNull(SemanticsProperties.TestTag) == "New traffic" })
            assertEquals(footer, scene.footerBounds())
        }
    }

    @Test fun `mode menu uses auto scroll title and manual reveals an operable switch`() = EventQueue.invokeAndWait {
        fixture { scene, state ->
            assertTrue(scene.nodes().any { it.hasText("Auto scroll") })
            assertTrue(scene.nodes().none { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch })
            scene.click(scene.nodes().first { it.hasText("Smart") }); scene.frames()
            scene.click(scene.nodes().first { it.hasText("Manual") }); scene.frames()
            assertEquals(TrafficAutoScrollMode.MANUAL, state.settings.value.mode)
            val switch = scene.nodes().first { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }
            assertEquals(androidx.compose.ui.state.ToggleableState.On, switch.config[SemanticsProperties.ToggleableState])
            scene.click(switch); scene.frames()
            assertFalse(state.settings.value.manualEnabled)
            scene.save("manual-control")
        }
    }

    @Test fun `manual enters and exits through intermediate widths without remounting the selector or changing height`() = EventQueue.invokeAndWait {
        fixture { scene, state ->
            val initial = scene.controlBounds()
            val anchorId = scene.modeAnchor().id
            state.settings.value = state.settings.value.copy(mode = TrafficAutoScrollMode.MANUAL)
            scene.frameAfter(1); scene.frameAfter(16)
            val entering = scene.controlBounds()
            scene.frameAfter(50)
            val middle = scene.controlBounds()
            scene.frameAfter(200)
            val expanded = scene.controlBounds()
            assertTrue(entering.width < middle.width && middle.width < expanded.width,
                "Manual must reveal progressively: $entering, $middle, $expanded")
            for (bounds in listOf(entering, middle, expanded)) {
                assertEquals(initial.height, bounds.height)
                assertEquals(initial.right, bounds.right)
            }
            assertEquals(anchorId, scene.modeAnchor().id, "The keyboard/popup anchor must stay mounted")
            assertEquals(1, scene.nodes().count { it.hasText("Manual") }, "Only the selected label should have semantics during a crossfade")
            state.settings.value = state.settings.value.copy(mode = TrafficAutoScrollMode.SMART)
            scene.frameAfter(1); scene.frameAfter(16)
            val leaving = scene.controlBounds()
            val outgoingSwitch = scene.nodes().first { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch }
            assertTrue(outgoingSwitch.config.contains(SemanticsProperties.Disabled), "The outgoing switch cannot still change Manual state")
            scene.frameAfter(50)
            val shrinking = scene.controlBounds()
            scene.frameAfter(200)
            assertTrue(leaving.width > shrinking.width && shrinking.width > scene.controlBounds().width)
            assertEquals(initial, scene.controlBounds())
            assertEquals(anchorId, scene.modeAnchor().id)
            assertTrue(scene.nodes().none { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch })
        }
    }

    @Test fun `rapid mode reversal retargets from the current width and retains manual switch state`() = EventQueue.invokeAndWait {
        fixture { scene, state ->
            val initial = scene.controlBounds()
            state.settings.value = TrafficAutoScrollSettings(TrafficAutoScrollMode.MANUAL, false)
            scene.frames()
            val fullyExpanded = scene.controlBounds()
            state.settings.value = state.settings.value.copy(mode = TrafficAutoScrollMode.SMART)
            scene.frames()
            state.settings.value = state.settings.value.copy(mode = TrafficAutoScrollMode.MANUAL)
            scene.frameAfter(1); scene.frameAfter(16); scene.frameAfter(45)
            val partial = scene.controlBounds()
            state.settings.value = state.settings.value.copy(mode = TrafficAutoScrollMode.SMART)
            scene.frameAfter(1); scene.frameAfter(16)
            val reversing = scene.controlBounds()
            // The first frame can finish the already scheduled enter tick before the new target is observed.
            // It must remain intermediate, then decrease rather than jumping to the full Manual width.
            assertTrue(partial.width < fullyExpanded.width && reversing.width < fullyExpanded.width)
            scene.frameAfter(50)
            assertTrue(scene.controlBounds().width < reversing.width)
            state.settings.value = state.settings.value.copy(mode = TrafficAutoScrollMode.MANUAL)
            scene.frameAfter(1); scene.frameAfter(16); scene.frameAfter(250)
            assertTrue(scene.controlBounds().width > initial.width)
            assertFalse(state.settings.value.manualEnabled)
            assertEquals(initial.height, scene.controlBounds().height)
            assertEquals(initial.right, scene.controlBounds().right)
            scene.click(scene.nodes().first { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch })
            scene.frames()
            assertTrue(state.settings.value.manualEnabled)
        }
    }

    @Test fun `reduced motion applies mode and switch presence immediately`() = EventQueue.invokeAndWait {
        fixture(motion = KNetMotion.copy(animationsEnabled = false)) { scene, state ->
            val initial = scene.controlBounds()
            state.settings.value = state.settings.value.copy(mode = TrafficAutoScrollMode.MANUAL)
            scene.frameAfter(1); scene.frameAfter(16)
            val expanded = scene.controlBounds()
            scene.frameAfter(200)
            assertEquals(expanded, scene.controlBounds())
            assertTrue(expanded.width > initial.width)
            state.settings.value = state.settings.value.copy(mode = TrafficAutoScrollMode.SMART)
            scene.frameAfter(1); scene.frameAfter(16)
            assertEquals(initial, scene.controlBounds())
            assertTrue(scene.nodes().none { it.config.getOrNull(SemanticsProperties.Role) == Role.Switch })
        }
    }

    @Test fun `history prepend retains the anchor and only fully visible new rows are reported`() = EventQueue.invokeAndWait {
        fixture { scene, state ->
            state.settings.value = TrafficAutoScrollSettings(TrafficAutoScrollMode.MANUAL, false)
            scene.frames()
            val oldAnchor = scene.eventLabels().first()
            state.rows.value = (49 downTo 31).map(::row) + state.rows.value
            state.reading.value = state.reading.value.copy(newCount = 19)
            scene.frames()
            assertEquals(oldAnchor, scene.eventLabels().first())
            val newKeys = (31..49).map { "row-$it" }.toSet()
            assertTrue(state.visible.value.transactionIds.none { it in newKeys })
            scene.verticalList().config[SemanticsActions.ScrollBy].action!!.invoke(0f, -17f)
            scene.frames()
            assertTrue(state.visible.value.transactionIds.none { it in newKeys }, "A clipped row must not advance last-seen")
            scene.verticalList().config[SemanticsActions.ScrollBy].action!!.invoke(0f, -17f)
            scene.frames()
            assertEquals(listOf("row-31"), state.visible.value.transactionIds.filter { it in newKeys })
            assertFalse(state.visible.value.atWindowTop, "The first new row is not the actual latest row")
            scene.verticalList().config[SemanticsActions.ScrollBy].action!!.invoke(0f, -34f)
            scene.frames()
            assertEquals(listOf("row-32", "row-31"), state.visible.value.transactionIds.filter { it in newKeys })
            assertEquals(0, state.jumps)
        }
    }

    @Test fun `old loaded top requests a newer page without issuing jump or clearing the pending counter`() = EventQueue.invokeAndWait {
        fixture { scene, state ->
            state.settings.value = TrafficAutoScrollSettings(TrafficAutoScrollMode.MANUAL, false)
            state.reading.value = state.reading.value.copy(newCount = 19)
            state.canLoadNewer.value = true
            scene.frames()
            assertEquals(1, state.newerLoads)
            assertEquals(0, state.jumps)
            assertEquals(19L, state.reading.value.newCount)
        }
    }

    private class State {
        val rows = mutableStateOf((30 downTo 1).map(::row))
        val selected = mutableStateOf<String?>("row-29")
        val settings = mutableStateOf(TrafficAutoScrollSettings())
        val reading = mutableStateOf(TrafficReadingState())
        val visible = mutableStateOf(TrafficVisibleRows(emptyList(), false))
        val canLoadNewer = mutableStateOf(false)
        var newerLoads = 0
        var jumps = 0
    }

    private fun fixture(motion: Motion = KNetMotion, check: (ImageComposeScene, State) -> Unit) {
        val state = State()
        ImageComposeScene(1100, 600, content = { KNetTheme(motion = motion) { Column {
            Row(Modifier.fillMaxWidth().padding(KNetTheme.spacing.md), horizontalArrangement = Arrangement.End) {
                Box(Modifier.testTag("Auto scroll control")) {
                    TrafficAutoScrollControl(state.settings.value,
                        { state.settings.value = state.settings.value.copy(mode = it) },
                        { state.settings.value = state.settings.value.copy(manualEnabled = !state.settings.value.manualEnabled) })
                }
            }
            TrafficTable(state.rows.value, state.selected.value, state.settings.value.follows(state.reading.value),
                { state.selected.value = it }, "256 KB", state.rows.value.size.toLong(), modifier = Modifier.weight(1f),
                reading = state.reading.value, canLoadNewer = state.canLoadNewer.value, scrollActions = TrafficTableScrollActions(
                    onVisibleRowsChanged = { visible ->
                        state.visible.value = visible
                        // The real controller resumes from measured visibility, including accessibility scrolls
                        // whose short scroll transaction can finish between frame snapshots.
                        if (visible.atWindowTop && state.rows.value.firstOrNull()?.transactionId in visible.transactionIds)
                            state.reading.value = state.reading.value.copy(atTop = true)
                    },
                    onLoadNewer = { state.newerLoads++; state.canLoadNewer.value = false },
                    onUserViewportChanged = { viewport -> if (!isTrafficAtTop(viewport.row, viewport.offset))
                        state.reading.value = state.reading.value.copy(atTop = false) },
                    onJumpToLatest = { state.jumps++; state.reading.value = state.reading.value.copy(newCount = 0, atTop = true, jumpRevision = state.reading.value.jumpRevision + 1) }),
                throughputIndicator = { Text("Download 0 kbps    Upload 0 kbps", color = KNetTheme.colors.textSecondary, style = KNetTheme.typography.caption, maxLines = 1, softWrap = false) })
        } } }).use { scene -> scene.frames(); check(scene, state) }
    }

    private fun ImageComposeScene.frameAfter(milliseconds: Long) { nanos += milliseconds * 1_000_000; render(nanos).close() }
    private fun ImageComposeScene.frames() { repeat(8) { frameAfter(1_000) } }
    private fun ImageComposeScene.controlBounds() = nodes().single { it.config.getOrNull(SemanticsProperties.TestTag) == "Auto scroll control" }.boundsInRoot
    private fun ImageComposeScene.modeAnchor() = nodes().single {
        it.config.getOrNull(SemanticsProperties.Role) == Role.Button && it.config.contains(SemanticsProperties.StateDescription)
    }
    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap { owner ->
        fun descend(node: SemanticsNode): List<SemanticsNode> = listOf(node) + node.children.flatMap(::descend)
        descend(owner.rootSemanticsNode)
    }
    private fun ImageComposeScene.verticalList() = nodes().first { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }
    private fun ImageComposeScene.footerBounds() = nodes().first { it.config.getOrNull(SemanticsProperties.TestTag) == "Traffic statistics" }.boundsInRoot
    private fun SemanticsNode.hasText(text: String) = config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == text }
    private fun ImageComposeScene.eventLabels() = nodes().filter { node -> node.boundsInRoot.top >= 80f && node.boundsInRoot.bottom < 550f }
        .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().filter { it.text.startsWith("/events/") }.map { it.text to node.boundsInRoot.top } }
    private fun ImageComposeScene.click(node: SemanticsNode) {
        val position = node.boundsInRoot.center
        sendPointerEvent(PointerEventType.Move, position)
        sendPointerEvent(PointerEventType.Press, position, buttons = PointerButtons(isPrimaryPressed = true), button = PointerButton.Primary)
        sendPointerEvent(PointerEventType.Release, position, button = PointerButton.Primary)
    }
    private fun ImageComposeScene.save(name: String) = render((nanos + 1_000_000_000).also { nanos = it }).use { image ->
        image.encodeToData()!!.use { data -> ImageIO.write(ImageIO.read(ByteArrayInputStream(data.bytes)), "png", File("/tmp/knet-smart-scroll-$name.png")) }
    }
    private companion object {
        fun row(index: Int) = HttpExchangeSnapshot(id = ExchangeId("row-$index"),
            request = HttpRequestSnapshot(RequestHead(HttpMethod.fromToken("GET"),
                RequestTarget.Absolute(HttpScheme.fromToken("https"), Authority("api.example.test", 443), "/events/$index"),
                ApplicationProtocol.fromToken("HTTP/1.1"), emptyList())),
            response = HttpResponseSnapshot(ResponseHead(ApplicationProtocol.fromToken("HTTP/1.1"), HttpStatus(200), "OK", emptyList())),
            state = ExchangeState.COMPLETED, startedAtEpochMillis = index * 1_000L,
            timings = ExchangeTimings(totalMillis = 50)).toTrafficRowUiState(index.toLong())
    }
}
