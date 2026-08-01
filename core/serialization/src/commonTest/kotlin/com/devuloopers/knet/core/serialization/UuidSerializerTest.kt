package com.devuloopers.knet.core.serialization

import com.devuloopers.knet.core.serialization.serializer.UuidSerializer
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit tests for [UuidSerializer].
 *
 * Verifies correct serialization and deserialization round-trips for UUID strings,
 * as well as validation rejection of invalid UUID formats on the decode path.
 *
 * [UuidSerializer] stores UUIDs as [String] in the serialized output and validates the
 * standard hyphenated format (`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`) on deserialization.
 *
 * Uses [UuidHolder] as the top-level `@Serializable` test model.
 */
class UuidSerializerTest {

    private val sampleUuidString = "550e8400-e29b-41d4-a716-446655440000"

    // ──────────────────────────────────────────────────────────────────────────────────────
    // Serialization
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that a UUID string is encoded to its standard hyphenated string representation
     * in the JSON output.
     */
    @Test
    fun `serializes UUID string to hyphenated format`() {
        val model = UuidHolder(id = sampleUuidString)
        val json = KNetJson.default.encodeToString(model)
        assertEquals("""{"id":"$sampleUuidString"}""", json)
    }

    // ──────────────────────────────────────────────────────────────────────────────────────
    // Deserialization
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that a hyphenated UUID string in JSON is deserialized back into a [String]
     * matching the original value.
     */
    @Test
    fun `deserializes hyphenated UUID string`() {
        val json = """{"id":"$sampleUuidString"}"""
        val result = KNetJson.default.decodeFromString<UuidHolder>(json)
        assertEquals(sampleUuidString, result.id)
    }

    // ──────────────────────────────────────────────────────────────────────────────────────
    // Round-trip
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that a UUID string survives a full encode → decode round-trip without data
     * loss or mutation.
     */
    @Test
    fun `UUID round-trip encode and decode`() {
        val original = UuidHolder(id = sampleUuidString)
        val json = KNetJson.default.encodeToString(original)
        val decoded = KNetJson.default.decodeFromString<UuidHolder>(json)
        assertEquals(original, decoded)
    }

    /**
     * Verifies that an uppercase UUID string is correctly accepted and preserved through
     * the round-trip (the regex accepts [a-fA-F]).
     */
    @Test
    fun `UUID round-trip with uppercase hex`() {
        val uppercaseUuid = "550E8400-E29B-41D4-A716-446655440000"
        val original = UuidHolder(id = uppercaseUuid)
        val json = KNetJson.default.encodeToString(original)
        val decoded = KNetJson.default.decodeFromString<UuidHolder>(json)
        assertEquals(original, decoded)
    }

    // ──────────────────────────────────────────────────────────────────────────────────────
    // Failure handling — invalid UUID format (decode path)
    // ──────────────────────────────────────────────────────────────────────────────────────

    /**
     * Verifies that an invalid UUID string in incoming JSON triggers an [IllegalArgumentException]
     * during deserialization. Validation is enforced on the decode path only.
     */
    @Test
    fun `invalid UUID string on decode throws IllegalArgumentException`() {
        val json = """{"id":"not-a-valid-uuid"}"""
        assertFailsWith<IllegalArgumentException> {
            KNetJson.default.decodeFromString<UuidHolder>(json)
        }
    }

    /**
     * Verifies that a UUID string with incorrect group lengths is rejected on decode.
     */
    @Test
    fun `malformed UUID with wrong segment length throws IllegalArgumentException`() {
        val json = """{"id":"550e8400-e29b-41d4"}"""
        assertFailsWith<IllegalArgumentException> {
            KNetJson.default.decodeFromString<UuidHolder>(json)
        }
    }
}
