package com.devuloopers.knet.data.desktop.certificate

import com.devuloopers.knet.engine.certificate.CertificateAuthority
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrustStoreInstallerTest {

    private val ca = CertificateAuthority.generate(commonName = "Trust Installer Test CA")

    @Test
    fun testWindowsInstallationUsesInjectedCommandRunner() {
        val destination = kotlin.io.path.createTempDirectory("knet-trust-test").resolve("root.crt").toFile()
        var capturedCommand: Array<String>? = null
        val result = TrustStoreInstaller.install(
            caCertificate = ca.certificate,
            manualCertificateFile = destination,
            operatingSystem = "windows",
            commandRunner = { command, _ ->
                capturedCommand = command
                InstallationResult.Success
            },
        )
        assertEquals(InstallationResult.Success, result)
        assertTrue(capturedCommand.orEmpty().contains("certutil"))
        assertTrue(destination.isFile)
        destination.parentFile.deleteRecursively()
    }

    @Test
    fun testLinuxReturnsStableManualInstructionsWithoutExecutingACommand() {
        val destination = kotlin.io.path.createTempDirectory("knet-trust-test").resolve("root.crt").toFile()
        val result = TrustStoreInstaller.install(
            caCertificate = ca.certificate,
            manualCertificateFile = destination,
            operatingSystem = "linux",
            commandRunner = { _, _ -> error("Linux must not execute an installer command") },
        )
        val manual = result as InstallationResult.ManualActionRequired
        assertTrue(manual.instructions.contains(destination.absolutePath))
        assertTrue(destination.isFile)
        destination.parentFile.deleteRecursively()
    }
}
