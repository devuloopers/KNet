package com.devuloopers.knet.ui.core.components.drop

import com.devuloopers.knet.ui.core.foundation.drop.localDroppedFiles
import com.devuloopers.knet.ui.core.foundation.drop.queueLocalFileDrop
import java.awt.datatransfer.*
import java.io.File
import kotlin.test.*

/** Native transfer decoding remains local, bounded and independent of source-file contents. */
class LocalDroppedFilesTest {
    @Test fun `native local file lists preserve spaces Unicode and duplicates without reading files`() {
        val file = File("/tmp/capture ü session.har")
        assertEquals(listOf(file.absolutePath, file.absolutePath), localDroppedFiles(transfer(DataFlavor.javaFileListFlavor, listOf(file, file))))
    }
    @Test fun `URI lists decode file names once and reject remote URLs authorities query and fragment`() {
        val flavor = DataFlavor("text/uri-list;class=java.lang.String")
        assertEquals(listOf("/tmp/a b%20.har"), localDroppedFiles(transfer(flavor, "# file\r\nfile:///tmp/a%20b%2520.har\r\n")))
        for (value in listOf("https://remote.test/session.har", "file://remote.test/session.har", "file:///tmp/test?query", "file:///tmp/test#fragment", " "))
            assertFails { localDroppedFiles(transfer(flavor, value)) }
    }
    @Test fun `invalid empty and excessive native transfers are rejected instead of partially imported`() {
        for (files in listOf(emptyList<Any>(), listOf("not-a-file"), List(17) { File("/tmp/$it.har") }))
            assertFails { localDroppedFiles(transfer(DataFlavor.javaFileListFlavor, files)) }
        assertFails { localDroppedFiles(StringSelection("https://remote.test/capture.har")) }
        assertFails { localDroppedFiles(transfer(DataFlavor("text/uri-list;class=java.lang.String"), "x".repeat(65_537))) }
    }
    @Test fun `drop reads transfer during native lifetime and defers chooser publication with release callback`() {
        val queue = mutableListOf<() -> Unit>()
        val path = File("/tmp/native lifetime.har")
        var valid = true
        val transfer = object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
            override fun isDataFlavorSupported(candidate: DataFlavor) = candidate == DataFlavor.javaFileListFlavor
            override fun getTransferData(candidate: DataFlavor): Any {
                check(valid) { "Native transfer already ended" }
                return listOf(path)
            }
        }
        var delivered: List<String>? = null
        var callback: (List<String>) -> Unit = { delivered = it }
        assertTrue(queueLocalFileDrop(transfer, { queue += it }, callback) { fail(it) })
        assertNull(delivered)
        valid = false
        callback = { fail("A later workspace callback must not replace the release callback") }
        queue.single().invoke()
        assertEquals(listOf(path.absolutePath), delivered)
        assertNotNull(callback)
    }

    @Test fun `rejected native transfer also defers error presentation until the native callback returns`() {
        val queue = mutableListOf<() -> Unit>()
        var error: String? = null
        assertFalse(queueLocalFileDrop(StringSelection("not a capture"), { queue += it }, { fail("Unexpected accepted transfer") }) { error = it })
        assertNull(error)
        queue.single().invoke()
        assertNotNull(error)
    }

    private fun transfer(flavor: DataFlavor, value: Any) = object : Transferable {
        override fun getTransferDataFlavors() = arrayOf(flavor)
        override fun isDataFlavorSupported(candidate: DataFlavor) = candidate == flavor
        override fun getTransferData(candidate: DataFlavor): Any = if (candidate == flavor) value else throw UnsupportedFlavorException(candidate)
    }
}
