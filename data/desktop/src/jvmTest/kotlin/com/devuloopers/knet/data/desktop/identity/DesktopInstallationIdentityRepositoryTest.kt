package com.devuloopers.knet.data.desktop.identity

import com.devuloopers.knet.companion.model.CompanionDesktopId
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.uuid.Uuid

class DesktopInstallationIdentityRepositoryTest {
    @Test
    fun identityIsStableAcrossRepositoryRecreationAndIndependentFromLegacyAliases() {
        val directory = Files.createTempDirectory("knet-desktop-identity").toFile()
        try {
            val expected = Uuid.parse("4ac0c20a-65e2-4bd8-ad63-122567fdb5e0")
            val first = DesktopInstallationIdentityRepository(directory) { expected }
                .loadOrCreate(setOf(CompanionDesktopId("knet-${"a".repeat(64)}")))
            val restored = DesktopInstallationIdentityRepository(directory) { Uuid.random() }
                .loadOrCreate(setOf(CompanionDesktopId("knet-${"b".repeat(64)}")))

            assertEquals(expected.toString(), first.canonicalId.value)
            assertEquals(first.canonicalId, restored.canonicalId)
            assertNotEquals(first.legacyIds, restored.legacyIds)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun separateInstallationsReceiveDifferentCanonicalIds() {
        val firstDirectory = Files.createTempDirectory("knet-desktop-one").toFile()
        val secondDirectory = Files.createTempDirectory("knet-desktop-two").toFile()
        try {
            val first = DesktopInstallationIdentityRepository(firstDirectory).loadOrCreate()
            val second = DesktopInstallationIdentityRepository(secondDirectory).loadOrCreate()

            assertNotEquals(first.canonicalId, second.canonicalId)
        } finally {
            firstDirectory.deleteRecursively()
            secondDirectory.deleteRecursively()
        }
    }

    @Test
    fun invalidPersistedIdentityFailsClosed() {
        val directory = Files.createTempDirectory("knet-desktop-invalid").toFile()
        try {
            directory.resolve("identity").mkdirs()
            directory.resolve("identity/desktop-id").writeText("not-a-uuid")

            assertFailsWith<IllegalStateException> {
                DesktopInstallationIdentityRepository(directory).loadOrCreate()
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
