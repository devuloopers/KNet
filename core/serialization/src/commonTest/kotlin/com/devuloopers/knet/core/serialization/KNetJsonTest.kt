package com.devuloopers.knet.core.serialization

import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [KNetJson].
 *
 * Verifies that [KNetJson.default] and [KNetJson.pretty] behave exactly as specified in the
 * `:core:serialization` plan:
 * - `ignoreUnknownKeys = true`
 * - `encodeDefaults = true`
 * - `coerceInputValues = true`
 * - `isLenient = true`
 * - `explicitNulls = false`
 * - `prettyPrint = true` (pretty instance only)
 *
 * Tests verify KNet's configuration — not `kotlinx.serialization` internals.
 *
 * Test models ([SampleModel], [WithStatus], [Status]) are declared at the top level
 * in `TestModels.kt` to avoid serialization plugin companion generation issues.
 */
class KNetJsonTest {

    // ──────────────────────────────────────────────────────────────────────────────────────
    // ignoreUnknownKeys = true
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that [KNetJson.default] silently ignores unknown keys in the JSON input,
     * enabling safe consumption of forward-compatible payloads.
     */
    @Test
    fun `default ignores unknown keys`() {
        val json = """{"name":"KNet","count":1,"unknownField":"ignored"}"""
        val result = KNetJson.default.decodeFromString<SampleModel>(json)
        assertEquals("KNet", result.name)
        assertEquals(1, result.count)
    }

    // ──────────────────────────────────────────────────────────────────────────────────────
    // encodeDefaults = true
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that [KNetJson.default] includes properties with default values in the
     * serialized output.
     */
    @Test
    fun `default encodes default values`() {
        val model = SampleModel(name = "KNet")
        val json = KNetJson.default.encodeToString(model)
        assertTrue(json.contains("\"count\":42"), "Expected count:42 in output, got: $json")
    }

    // ──────────────────────────────────────────────────────────────────────────────────────
    // explicitNulls = false
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that [KNetJson.default] omits null-valued fields from the serialized output,
     * keeping payloads compact and clean.
     */
    @Test
    fun `default omits null fields`() {
        val model = SampleModel(name = "KNet", tag = null)
        val json = KNetJson.default.encodeToString(model)
        assertFalse(json.contains("tag"), "Null field 'tag' must be omitted, got: $json")
    }

    /**
     * Verifies that [KNetJson.default] correctly includes non-null fields.
     */
    @Test
    fun `default includes non-null fields`() {
        val model = SampleModel(name = "KNet", tag = "proxy")
        val json = KNetJson.default.encodeToString(model)
        assertTrue(json.contains("\"tag\":\"proxy\""), "Expected tag:proxy in output, got: $json")
    }

    // ──────────────────────────────────────────────────────────────────────────────────────
    // coerceInputValues = true
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that [KNetJson.default] coerces an invalid enum value to the default instead
     * of throwing a [kotlinx.serialization.SerializationException].
     */
    @Test
    fun `default coerces invalid enum to default`() {
        val json = """{"status":"UNKNOWN_STATUS"}"""
        val result = KNetJson.default.decodeFromString<WithStatus>(json)
        assertEquals(Status.ACTIVE, result.status)
    }

    // ──────────────────────────────────────────────────────────────────────────────────────
    // isLenient = true
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that [KNetJson.default] accepts unquoted string values, tolerating minor
     * JSON deviations from loosely-formatted external sources.
     */
    @Test
    fun `default is lenient and accepts unquoted strings`() {
        val lenientJson = """{name: KNet, count: 5}"""
        val result = KNetJson.default.decodeFromString<SampleModel>(lenientJson)
        assertEquals("KNet", result.name)
        assertEquals(5, result.count)
    }

    // ──────────────────────────────────────────────────────────────────────────────────────
    // KNetJson.pretty
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that [KNetJson.pretty] produces human-readable output with newlines.
     */
    @Test
    fun `pretty produces formatted output with newlines`() {
        val model = SampleModel(name = "KNet")
        val json = KNetJson.pretty.encodeToString(model)
        assertTrue(json.contains("\n"), "Expected pretty-printed newlines, got: $json")
    }

    /**
     * Verifies that [KNetJson.pretty] uses 4-space indentation as specified.
     */
    @Test
    fun `pretty uses 4-space indentation`() {
        val model = SampleModel(name = "KNet")
        val json = KNetJson.pretty.encodeToString(model)
        assertTrue(json.contains("    "), "Expected 4-space indent, got: $json")
    }

    /**
     * Verifies that [KNetJson.pretty] output can be decoded back by [KNetJson.default],
     * confirming round-trip compatibility between the two instances.
     */
    @Test
    fun `pretty output is decodable by default`() {
        val model = SampleModel(name = "KNet", count = 10)
        val prettyJson = KNetJson.pretty.encodeToString(model)
        val decoded = KNetJson.default.decodeFromString<SampleModel>(prettyJson)
        assertEquals(model, decoded)
    }

    // ──────────────────────────────────────────────────────────────────────────────────────
    // Singleton identity
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that [KNetJson.default] is a stable singleton — the same instance is
     * returned on every access to prevent inadvertent re-configuration drift.
     */
    @Test
    fun `default is a singleton`() {
        assertTrue(KNetJson.default === KNetJson.default)
    }

    /**
     * Verifies that [KNetJson.pretty] is a stable singleton.
     */
    @Test
    fun `pretty is a singleton`() {
        assertTrue(KNetJson.pretty === KNetJson.pretty)
    }
}
