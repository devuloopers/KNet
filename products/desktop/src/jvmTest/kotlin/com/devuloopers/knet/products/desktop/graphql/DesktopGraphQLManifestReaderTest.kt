package com.devuloopers.knet.products.desktop.graphql

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

/** Native reads stay bounded even when the selected file is larger than the catalog budget. */
class DesktopGraphQLManifestReaderTest {
    @Test fun `reader returns exact short content and at most one overflow byte for oversized file`() = runTest {
        val file = Files.createTempFile("knet-query-manifest-", ".json")
        try {
            Files.writeString(file, "{}")
            val reader = DesktopGraphQLManifestReader()
            assertContentEquals("{}".encodeToByteArray(), reader.read(file.toString(), 100))
            Files.write(file, ByteArray(4096))
            assertEquals(101, reader.read(file.toString(), 100).size)
        } finally { Files.deleteIfExists(file) }
    }
    @Test fun `missing file read fails and impossible allocation bounds are rejected`() = runTest {
        val reader = DesktopGraphQLManifestReader()
        assertFailsWith<IllegalArgumentException> { reader.read("unused", 0) }
        assertFailsWith<IllegalArgumentException> { reader.read("unused", Int.MAX_VALUE) }
        assertFailsWith<java.io.IOException> { reader.read("/tmp/knet-missing-query-manifest-${kotlin.uuid.Uuid.random()}", 100) }
    }
}
