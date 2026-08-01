package com.devuloopers.knet.core.serialization

import kotlinx.serialization.SerializationException

/**
 * Lightweight JSON serialization and deserialization helpers built around [KNetJson].
 *
 * These functions are thin wrappers over `kotlinx.serialization` and **must not** contain
 * any business logic. All encoding uses [KNetJson.default] by default; pass `pretty = true`
 * to use [KNetJson.pretty] where human-readable output is required.
 *
 * ## Usage
 * ```kotlin
 * // Decoding
 * val model: MyModel = jsonString.decode()
 *
 * // Safe decoding (returns null on failure)
 * val model: MyModel? = jsonString.decodeOrNull()
 *
 * // Encoding
 * val json: String = myModel.encode()
 *
 * // Pretty-printed encoding
 * val readable: String = myModel.encodePretty()
 * ```
 */

/**
 * Decodes this JSON string into the reified type [T] using [KNetJson.default].
 *
 * @return A deserialized instance of [T].
 * @throws SerializationException if the string cannot be decoded into [T].
 * @throws IllegalArgumentException if the JSON structure is fundamentally invalid.
 */
inline fun <reified T> String.decode(): T =
    KNetJson.default.decodeFromString(this)

/**
 * Decodes this JSON string into the reified type [T] using [KNetJson.default],
 * returning `null` if decoding fails for any reason.
 *
 * This is the safe variant of [decode]. Use it when the input JSON comes from an
 * external, potentially untrusted source and a failure should not propagate as an
 * exception.
 *
 * @return A deserialized instance of [T], or `null` on any decoding failure.
 */
inline fun <reified T> String.decodeOrNull(): T? =
    try {
        KNetJson.default.decodeFromString(this)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

/**
 * Encodes this value to a compact JSON string using [KNetJson.default].
 *
 * The output uses the compact (non-pretty-printed) format. Null fields are omitted
 * because [KNetJson.default] sets `explicitNulls = false`.
 *
 * @return A compact JSON string representation of this value.
 */
inline fun <reified T> T.encode(): String =
    KNetJson.default.encodeToString(this)

/**
 * Encodes this value to a human-readable, pretty-printed JSON string using [KNetJson.pretty].
 *
 * The output uses 4-space indentation. Use this for export files, debug output, and
 * developer-facing tooling where readability matters more than payload size.
 *
 * @return A pretty-printed JSON string representation of this value.
 */
inline fun <reified T> T.encodePretty(): String =
    KNetJson.pretty.encodeToString(this)
