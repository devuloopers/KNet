package com.devuloopers.knet.companion.connectivity.transport

import com.devuloopers.knet.companion.connectivity.testing.companionInspectionConfigurationFixture
import java.io.File
import java.net.DatagramSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PlatformAndroidTunForwarderTest {
    @Test
    fun `forwarder writes local-only configuration and releases native resources`() = runTest {
        val directory = temporaryDirectory()
        val engine = RecordingEngine()
        val forwarder = PlatformAndroidTunForwarder(
            configurationDirectory = directory,
            transport = AndroidCompanionProxyTransport(),
            engine = engine,
        )
        try {
            assertEquals(
                AndroidTunForwarderStartResult.Started,
                forwarder.start(91, companionInspectionConfigurationFixture(), AllowAllProtector),
            )
            assertEquals(91, engine.fileDescriptor)
            val configurationFile = requireNotNull(engine.configurationFile)
            val configuration = configurationFile.readText()
            assertTrue(configuration.contains("address: '127.0.0.1'"))
            assertTrue(configuration.contains("port:"))
            assertFalse(configuration.contains("credential", ignoreCase = true))
            assertFalse(configuration.contains("192.168."))

            forwarder.stop()

            assertEquals(1, engine.stopCalls)
            assertFalse(configurationFile.exists())
        } finally {
            forwarder.stop()
            directory.deleteRecursively()
        }
    }

    @Test
    fun `native startup failure is contained and can be retried`() = runTest {
        val directory = temporaryDirectory()
        val engine = RecordingEngine(failStarts = true)
        val forwarder = PlatformAndroidTunForwarder(
            configurationDirectory = directory,
            transport = AndroidCompanionProxyTransport(),
            engine = engine,
        )
        try {
            assertEquals(
                AndroidTunForwarderStartResult.Failed,
                forwarder.start(92, companionInspectionConfigurationFixture(), AllowAllProtector),
            )
            assertTrue(engine.stopCalls >= 1)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        } finally {
            forwarder.stop()
            directory.deleteRecursively()
        }
    }

    private class RecordingEngine(
        private val failStarts: Boolean = false,
    ) : AndroidTun2SocksEngine {
        @Volatile
        var configurationFile: File? = null

        @Volatile
        var fileDescriptor: Int? = null

        var stopCalls: Int = 0

        override fun start(configurationPath: String, tunFileDescriptor: Int) {
            configurationFile = File(configurationPath)
            fileDescriptor = tunFileDescriptor
            if (failStarts) error("Expected native startup failure.")
        }

        override fun stop() {
            stopCalls += 1
        }
    }

    private object AllowAllProtector : AndroidSocketProtector {
        override fun protect(socket: Socket): Boolean = true
        override fun protect(socket: DatagramSocket): Boolean = true
    }
}

private fun temporaryDirectory(): File = File.createTempFile("knet-inspection-", ".tmp").apply {
    check(delete())
    check(mkdirs())
}
