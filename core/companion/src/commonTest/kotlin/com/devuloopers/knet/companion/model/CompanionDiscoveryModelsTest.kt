package com.devuloopers.knet.companion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CompanionDiscoveryModelsTest {
    private val canonical = CompanionDesktopId("4ac0c20a-65e2-4bd8-ad63-122567fdb5e0")
    private val legacy = CompanionDesktopId("knet-${"a".repeat(64)}")
    private val runtime = CompanionDesktopRuntimeId.parse("f9a4ed22-f9c9-4b87-9b27-55efab33a84d")

    @Test
    fun txtMetadataRoundTripsAndMatchesCanonicalOrLegacyIdentity() {
        val codec = CompanionDiscoveryTxtCodec()
        val advertisement = CompanionDiscoveryAdvertisement(1, canonical, setOf(legacy), runtime)

        val restored = codec.decode(codec.encode(advertisement))

        assertEquals(advertisement, restored)
        assertTrue(restored.matches(setOf(canonical)))
        assertTrue(restored.matches(setOf(legacy)))
    }

    @Test
    fun unknownOrOversizedTxtMetadataIsRejected() {
        val codec = CompanionDiscoveryTxtCodec()
        assertFailsWith<IllegalArgumentException> {
            codec.decode(mapOf("unknown" to "value"))
        }
        assertFailsWith<IllegalArgumentException> {
            codec.decode(
                mapOf(
                    "v" to "1",
                    "id" to "d".repeat(128),
                    "aliases" to "a".repeat(512),
                    "runtime" to runtime.value.toString(),
                ),
            )
        }
    }

    @Test
    fun endpointDescriptorRoundTripsWithLegacyAlias() {
        val codec = CompanionEndpointReconciliationCodec()
        val descriptor = CompanionEndpointDescriptor(1, canonical, setOf(legacy), runtime, 8183, 8182)

        val restored = codec.decodeDescriptor(codec.encodeDescriptor(descriptor))

        assertEquals(descriptor, restored)
        assertTrue(restored.accepts(legacy))
    }

    @Test
    fun duplicatedEndpointFieldsAreRejected() {
        val codec = CompanionEndpointReconciliationCodec()

        assertFailsWith<IllegalArgumentException> {
            codec.decodeRequest("desktopId=${canonical.value}\ndesktopId=${legacy.value}".encodeToByteArray())
        }
    }
}
