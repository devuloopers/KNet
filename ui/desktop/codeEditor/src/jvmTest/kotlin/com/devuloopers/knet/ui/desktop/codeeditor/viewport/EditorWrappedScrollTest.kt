package com.devuloopers.knet.ui.desktop.codeeditor.viewport

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.codeeditor.algorithm.FoldRegion
import com.devuloopers.knet.ui.desktop.codeeditor.api.CodeEditorStrings
import com.devuloopers.knet.ui.desktop.codeeditor.component.*
import com.devuloopers.knet.ui.desktop.codeeditor.document.*
import com.devuloopers.knet.ui.desktop.codeeditor.theme.*
import java.awt.EventQueue
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Real Compose pointer/layout regressions for shared request/response and editable editor bodies. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
class EditorWrappedScrollTest {
    private var nanos = 0L
    private val source = (0 until 566).joinToString("\n") {
        if (it == 395) "  \"message\": \"" + "large wrapped field with words and Unicode λ ".repeat(1500) + "\"," else "  \"value$it\": $it,"
    }

    @Test fun `background geometry survives rapid width changes and scene disposal`() {
        val snapshot = ChunkedEditorDocument((0 until 300).joinToString("\n") {
            "wrapped Unicode λ field $it " + "wide words ".repeat(60)
        }).snapshot
        val geometry = EditorVisualLineMap.build(snapshot.lineCount, emptyList(), emptySet())
        for (scale in listOf(1f, 1.4f, 1.1f)) {
            val result = AtomicReference<EditorLineHeightIndex?>()
            val width = mutableStateOf(180)
            var scene: ImageComposeScene? = null
            EventQueue.invokeAndWait {
                scene = ImageComposeScene(400, 600, Density(1f, scale), content = {
                    result.set(rememberEditorLineHeights(snapshot, geometry, width.value, true,
                        CodeEditorTokens.FontSize, CodeEditorTokens.LineHeight, null, CodeEditorSemanticColors(), false))
                    // Visible text and worker measurement run concurrently, with independent native fonts.
                    androidx.compose.foundation.text.BasicText("Rendering while wrapping ${width.value} λ",
                        style = CodeEditorTokens.editorTextStyle(CodeEditorTokens.FontSize, CodeEditorTokens.LineHeight))
                })
            }
            try {
                repeat(20) { iteration ->
                    EventQueue.invokeAndWait {
                        width.value = 150 + iteration * 7
                        scene!!.render(++nanos * 16_000_000).close()
                    }
                }
                val deadline = System.nanoTime() + 10_000_000_000L
                do {
                    EventQueue.invokeAndWait { scene!!.render(++nanos * 16_000_000).close() }
                    Thread.sleep(10)
                } while (result.get() == null && System.nanoTime() < deadline)
                val heights = assertNotNull(result.get(), "Worker must publish current wrapped geometry")
                assertEquals(snapshot.lineCount, heights.rowCount)
                assertTrue(heights.totalHeight > heights.baselineHeight * snapshot.lineCount)
                // Leave a replacement in flight when disposing; cancellation must not touch UI font ownership.
                EventQueue.invokeAndWait { width.value = 100; scene!!.render(++nanos * 16_000_000).close() }
            } finally { EventQueue.invokeAndWait { scene!!.close() } }
        }
    }

    @Test fun `drag both ways inside a very tall field remains stable after the pointer stops`() = fixture { f ->
        val initialThumb = f.thumbY()
        f.drag(initialThumb, 210f, release = false)
        val middle = f.position()
        assertEquals(395, middle.first, "Drag should land inside the large field")
        f.frames(12)
        assertEquals(middle, f.position(), "No scroll correction while stationary")
        f.edt { f.caret.value = EditorPosition(5, 3) }
        f.frames(4)
        assertEquals(middle, f.position(), "Caret updates must not interrupt a held thumb")
        f.release(210f)
        assertEquals(middle, f.position(), "Release must not replay the skipped caret reveal")
        val middleThumb = f.thumbY()
        f.drag(middleThumb, middleThumb + 50f)
        assertTrue(f.position().second > middle.second)
        val afterForward = f.position()
        f.drag(f.thumbY(), f.thumbY() - 80f)
        assertTrue(f.position().second < afterForward.second)
        f.frames(12)
        f.save("long-field")
        f.drag(f.thumbY(), 500f)
        f.edt { assertFalse(f.list.canScrollForward, "Thumb must reach the real bottom") }
        f.drag(f.thumbY(), -50f)
        assertEquals(0 to 0, f.position(), "Reverse drag must reach the real top")
    }

    @Test fun `late fold analysis leaves manually scrolled viewport alone`() = fixture { f ->
        f.scrollTo(395, 2500)
        val before = f.position()
        f.edt { f.folds.value = listOf(FoldRegion(0, 565, "}")) }
        f.frames(10)
        assertEquals(before, f.position())
    }

    @Test fun `explicit caret navigation reveals a match within an already visible giant field`() = fixture { f ->
        f.scrollTo(395, 0)
        f.edt { f.caret.value = EditorPosition(395, source.lines()[395].length - 20) }
        f.frames(12)
        assertEquals(395, f.position().first)
        assertTrue(f.position().second > 1000, "Caret deep within one logical row must be revealed")
    }

    @Test fun `offscreen search navigation reveals the correct visual part of a long row`() = fixture { f ->
        f.edt { f.caret.value = EditorPosition(395, source.lines()[395].length - 20) }
        f.frames(12)
        assertEquals(395, f.position().first)
        assertTrue(f.position().second > 1000)
    }

    @Test fun `editable body preserves thumb position when active line rendering changes`() = fixture(editable = true) { f ->
        f.edt { f.caret.value = EditorPosition(395, 5000) }
        f.frames(12)
        f.drag(f.thumbY(), 220f, release = false)
        val before = f.position()
        f.frames(12)
        assertEquals(395, before.first)
        assertEquals(before, f.position())
        f.release(220f)
        assertEquals(before, f.position())
    }

    @Test fun `whole document selection preserves manual scroll position`() = fixture { f ->
        f.scrollTo(395, 2000)
        val before = f.position()
        f.edt {
            val end = EditorPosition(565, f.snapshot.line(565).length)
            f.selection.value = EditorSelection(EditorPosition(0, 0), end)
            f.caret.value = end
        }
        f.frames(8)
        assertEquals(before, f.position())
    }

    @Test fun `scrollbar lane stays clear of long source text at larger font scale`() = fixture(fontScale = 1.5f) { f ->
        f.scrollTo(395, 1000)
        f.frames(6)
        val image = f.image()
        // Text viewport ends at 504; the 8 px lane before the 8 px scrollbar must contain only background.
        val expected = image.getRGB(507, 100)
        for (x in 504..510) for (y in 10..390) assertEquals(expected, image.getRGB(x, y), "Gutter at $x,$y")
        f.save("larger-text-clearance")
    }

    @Test fun `width changes and collapsed folds rebuild geometry without changing source`() = fixture { f ->
        f.edt { f.width.value = 380 }
        f.frames(10)
        f.drag(f.thumbY(), 200f)
        assertEquals(395, f.position().first)
        f.edt {
            f.folds.value = listOf(FoldRegion(0, 565, "}"))
            f.collapsed.value = setOf(0)
        }
        f.frames(10)
        assertEquals(1, f.list.layoutInfo.totalItemsCount)
        assertEquals(source, f.snapshot.text())
    }

    @Test fun `unwrapped mode keeps ordinary rows and reserves bottom scrollbar space`() = fixture(wrap = false) { f ->
        f.scrollTo(395, 0)
        f.frames(5)
        f.edt {
            assertEquals(384, f.list.layoutInfo.viewportSize.height)
            assertTrue(f.list.layoutInfo.visibleItemsInfo.all { it.size == 16 })
        }
        f.save("unwrapped")
    }

    private inner class Fixture(val scene: ImageComposeScene, val list: LazyListState,
        val snapshot: EditorDocumentSnapshot, val caret: MutableState<EditorPosition>,
        val folds: MutableState<List<FoldRegion>>, val collapsed: MutableState<Set<Int>>, val width: MutableState<Int>,
        val selection: MutableState<EditorSelection?>) {
        fun edt(block: () -> Unit) = EventQueue.invokeAndWait(block)
        fun frames(count: Int = 4) { repeat(count) { edt { scene.render(++nanos * 16_000_000).close() }; Thread.sleep(15) } }
        fun position(): Pair<Int, Int> { var value = 0 to 0; edt { value = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }; return value }
        fun scrollTo(row: Int, offset: Int) { edt { runBlocking { list.scrollToItem(row, offset) } }; frames() }
        fun image(): java.awt.image.BufferedImage {
            var result: java.awt.image.BufferedImage? = null
            edt { scene.render(++nanos * 16_000_000).use { image -> image.encodeToData()!!.use {
                result = ImageIO.read(ByteArrayInputStream(it.bytes))
            } } }
            return result!!
        }
        fun thumbY(): Float {
            // Geometry is asynchronous. Await the actual thumb rather than assuming that a fixed
            // number of 15 ms frames also completes paragraph work on a slower/emulated host.
            val deadline = System.nanoTime() + 5_000_000_000L
            while (true) {
                val image = image()
                val x = width.value - 4
                val background = image.getRGB(width.value - 12, 10)
                val occupied = (1 until image.height - 1).filter { image.getRGB(x, it) != background }
                if (occupied.isNotEmpty()) return (occupied.first() + occupied.last()) / 2f
                assertTrue(System.nanoTime() < deadline, "Geometry and thumb must be ready")
                frames(1)
            }
        }
        fun drag(from: Float, to: Float, release: Boolean = true) {
            edt {
                val x = width.value - 4f
                scene.sendPointerEvent(PointerEventType.Move, Offset(x, from))
                scene.sendPointerEvent(PointerEventType.Press, Offset(x, from), buttons = PointerButtons(isPrimaryPressed = true))
            }; frames(2)
            edt { scene.sendPointerEvent(PointerEventType.Move, Offset(width.value - 4f, to), buttons = PointerButtons(isPrimaryPressed = true)) }
            frames(4)
            if (release) release(to)
        }
        fun release(y: Float) {
            edt { scene.sendPointerEvent(PointerEventType.Release, Offset(width.value - 4f, y)) }; frames(4)
        }
        fun save(name: String) { ImageIO.write(image(), "png", File("/tmp/knet-editor-scroll-$name.png")) }
    }

    private fun fixture(fontScale: Float = 1f, wrap: Boolean = true, editable: Boolean = false, check: (Fixture) -> Unit) {
        val document = ChunkedEditorDocument(source).snapshot
        val list = LazyListState()
        val caret = mutableStateOf(EditorPosition(0, 0))
        val folds = mutableStateOf<List<FoldRegion>>(emptyList())
        val collapsed = mutableStateOf<Set<Int>>(emptySet())
        val width = mutableStateOf(520)
        val selection = mutableStateOf<EditorSelection?>(null)
        var scene: ImageComposeScene? = null
        EventQueue.invokeAndWait {
            scene = ImageComposeScene(520, 400, Density(1f, fontScale), content = { KNetTheme {
                LazyCodeBody(snapshot = document, mode = if (editable) LazyCodeBodyMode.Editable else LazyCodeBodyMode.ReadOnly, tokenizedDocument = null,
                    searchResult = null, activeSearchMatch = null, semanticColors = CodeEditorSemanticColors(), strings = CodeEditorStrings(),
                    foldRegions = folds.value, collapsedFoldStartLines = collapsed.value, onToggleFold = {}, isFoldingEnabled = true,
                    isWordWrapEnabled = wrap, fontSize = CodeEditorTokens.FontSize, lineHeight = CodeEditorTokens.LineHeight,
                    caret = caret.value, onCaretChange = { caret.value = it }, selection = selection.value, onSelectionChange = { selection.value = it },
                    onDeleteSelection = { false }, onDeleteCurrentLine = {}, onPaste = {}, onSelectAll = {}, onSelectionTextInput = {},
                    onLineChanged = { _, _ -> }, onLineSplit = { _, _ -> }, onLineMerge = {}, onMultiLinePaste = { _, _, _ -> },
                    onUndo = {}, onRedo = {}, onToggleComment = null, shouldRequestEditorFocus = false,
                    onOpenSearch = null, onCloseSearch = null, lazyListState = list, modifier = Modifier.width(width.value.dp))
            } })
        }
        val f = Fixture(scene!!, list, document, caret, folds, collapsed, width, selection)
        try { f.frames(20); check(f) } finally { f.edt { f.scene.close() } }
    }
}
