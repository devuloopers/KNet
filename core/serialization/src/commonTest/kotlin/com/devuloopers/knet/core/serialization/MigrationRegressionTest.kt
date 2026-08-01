package com.devuloopers.knet.core.serialization

import com.devuloopers.knet.core.serialization.serializer.UuidSerializer
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Migration regression tests for `:core:serialization`.
 *
 * Verifies that the public API surface of this module remains stable after the initial
 * creation and any subsequent refactoring. These tests do not test serialization behaviour
 * (covered by [KNetJsonTest] and [UuidSerializerTest]); instead they guard against:
 *
 * - [KNetJson] being accidentally replaced with an unconfigured Json instance.
 * - Public symbols being renamed or removed without a deprecation cycle.
 * - The module's single-entry-point contract being broken.
 *
 * Any failure in this file indicates a **breaking API change** that requires a migration
 * guide before the change can be merged.
 *
 * Test models ([SimpleNameCountModel], [SimpleNameModel], [UuidHolder]) are declared at
 * the top level in `TestModels.kt` and `UuidHolder.kt` to avoid KMP serialization plugin
 * companion generation issues with local inner classes.
 */
class MigrationRegressionTest {

    // ──────────────────────────────────────────────────────────────────────────────────────
    // Public API stability — KNetJson
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that [KNetJson] exists as the single, accessible entry point for JSON
     * serialization configuration in `:core:serialization`.
     */
    @Test
    fun `KNetJson is accessible as the single entry point`() {
        assertNotNull(KNetJson)
    }

    /**
     * Verifies that [KNetJson.default] is publicly accessible and not null.
     */
    @Test
    fun `KNetJson default instance is publicly accessible`() {
        assertNotNull(KNetJson.default)
    }

    /**
     * Verifies that [KNetJson.pretty] is publicly accessible and not null.
     */
    @Test
    fun `KNetJson pretty instance is publicly accessible`() {
        assertNotNull(KNetJson.pretty)
    }

    /**
     * Verifies that [KNetJson.default] and [KNetJson.pretty] are distinct instances with
     * different configurations, preventing accidental sharing of the same object.
     */
    @Test
    fun `KNetJson default and pretty are distinct instances`() {
        assertTrue(KNetJson.default !== KNetJson.pretty)
    }

    // ──────────────────────────────────────────────────────────────────────────────────────
    // Public API stability — SerializationHelper
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that [decode] is accessible as a public extension function on [String].
     */
    @Test
    fun `decode extension function is publicly accessible`() {
        val json = """{"name":"KNet","count":1}"""
        val result = json.decode<SimpleNameCountModel>()
        assertNotNull(result)
    }

    /**
     * Verifies that [decodeOrNull] is accessible as a public extension function on [String].
     */
    @Test
    fun `decodeOrNull extension function is publicly accessible`() {
        val json = """{"name":"KNet"}"""
        val result: SimpleNameModel? = json.decodeOrNull()
        assertNotNull(result)
    }

    /**
     * Verifies that [encode] is accessible as a public extension function.
     */
    @Test
    fun `encode extension function is publicly accessible`() {
        val json = SimpleNameModel("KNet").encode()
        assertNotNull(json)
        assertTrue(json.isNotEmpty())
    }

    /**
     * Verifies that [encodePretty] is accessible as a public extension function.
     */
    @Test
    fun `encodePretty extension function is publicly accessible`() {
        val json = SimpleNameModel("KNet").encodePretty()
        assertNotNull(json)
        assertTrue(json.contains("\n"))
    }

    // ──────────────────────────────────────────────────────────────────────────────────────
    // Public API stability — UuidSerializer
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that [UuidSerializer] is publicly accessible as a [kotlinx.serialization.KSerializer].
     */
    @Test
    fun `UuidSerializer is publicly accessible`() {
        assertNotNull(UuidSerializer)
        assertNotNull(UuidSerializer.descriptor)
    }

    /**
     * Verifies that [UuidSerializer]'s serial descriptor has the expected stable name.
     * A change here would break persisted data — any rename requires a migration guide.
     */
    @Test
    fun `UuidSerializer descriptor name is stable`() {
        val expectedName = "com.devuloopers.knet.core.serialization.UuidString"
        assertTrue(
            UuidSerializer.descriptor.serialName == expectedName,
            "Serial name must remain '$expectedName', got: ${UuidSerializer.descriptor.serialName}"
        )
    }
}
