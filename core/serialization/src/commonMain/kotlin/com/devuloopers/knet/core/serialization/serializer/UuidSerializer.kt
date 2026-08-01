package com.devuloopers.knet.core.serialization.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * KMP [KSerializer] for UUID values represented as [String].
 *
 * Serializes a UUID string to and from its standard hyphenated string representation
 * (e.g. `"550e8400-e29b-41d4-a716-446655440000"`).
 *
 * ## Design Decision
 * Rather than depending on `kotlin.uuid.Uuid` (which carries `@ExperimentalUuidApi` and has
 * JVM initialization constraints in Kotlin 2.4), this serializer treats UUID as a validated
 * [String] alias. This keeps the module fully multiplatform-stable and prevents JVM class
 * initialization failures at test time.
 *
 * ## Validation
 * The hyphenated UUID format is validated with a standard regex on deserialization, ensuring
 * that invalid UUIDs are rejected with a clear [IllegalArgumentException].
 *
 * ## Usage
 *
 * Apply this serializer explicitly where needed:
 * ```kotlin
 * @Serializable
 * data class Session(
 *     @Serializable(with = UuidSerializer::class)
 *     val id: String // UUID string e.g. "550e8400-e29b-41d4-a716-446655440000"
 * )
 * ```
 *
 * @throws IllegalArgumentException if the decoded string is not a valid hyphenated UUID.
 */
object UuidSerializer : KSerializer<String> {

    /**
     * Standard hyphenated UUID format regex pattern: 8-4-4-4-12 hex characters.
     * Uses [lazy] initialization to avoid JVM static-initializer issues in KMP contexts.
     */
    private val uuidRegex: Regex by lazy {
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    }

    /**
     * Descriptor declaring this serializer as a primitive [String] with the stable serial name
     * `"com.devuloopers.knet.core.serialization.UuidString"`.
     *
     * Note: The name `"kotlin.uuid.Uuid"` is reserved by the `kotlinx.serialization`
     * built-in descriptor registry in Kotlin 2.4+. Using that name causes a
     * `IllegalArgumentException` at class initialization time.
     */
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.devuloopers.knet.core.serialization.UuidString", PrimitiveKind.STRING)

    /**
     * Serializes [value] to a hyphenated UUID string.
     *
     * @param encoder The encoder provided by the serialization framework.
     * @param value The UUID string to serialize.
     */
    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    /**
     * Deserializes a hyphenated UUID string.
     *
     * @param decoder The decoder provided by the serialization framework.
     * @return The validated UUID string.
     * @throws IllegalArgumentException if the decoded string is not a valid UUID format.
     */
    override fun deserialize(decoder: Decoder): String {
        val value = decoder.decodeString()
        requireValidUuid(value)
        return value
    }

    /**
     * Validates that [value] matches the standard hyphenated UUID format.
     *
     * @throws IllegalArgumentException if [value] does not conform to the UUID pattern.
     */
    private fun requireValidUuid(value: String) {
        require(uuidRegex.matches(value)) {
            "Invalid UUID format: '$value'. Expected format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
        }
    }
}
