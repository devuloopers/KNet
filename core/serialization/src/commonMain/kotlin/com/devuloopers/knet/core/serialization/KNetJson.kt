package com.devuloopers.knet.core.serialization

import com.devuloopers.knet.core.serialization.KNetJson.default
import com.devuloopers.knet.core.serialization.KNetJson.pretty
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Centralized, shared [Json] configuration for all KNet modules.
 *
 * Every KNet module that performs JSON serialization or deserialization **must** use one of
 * these instances instead of constructing its own `Json { ... }` builder. This ensures
 * consistent parsing behaviour—unknown key tolerance, default encoding, and null handling—
 * across Desktop, Android, iOS, CLI, and test targets.
 *
 * ## Available instances
 * - [default]: Compact, production-ready JSON. Use for network transport, data persistence,
 *   and inter-module communication.
 * - [pretty]: Human-readable JSON with 4-space indentation. Use for export files, debug
 *   output, and developer tooling.
 *
 * ## Usage
 * ```kotlin
 * // Encoding
 * val json = KNetJson.default.encodeToString(myObject)
 *
 * // Decoding
 * val obj = KNetJson.default.decodeFromString<MyModel>(json)
 *
 * // Pretty-printed export
 * val readable = KNetJson.pretty.encodeToString(myObject)
 * ```
 */
object KNetJson {

    /**
     * Compact, lenient [Json] instance for production use.
     *
     * Configuration:
     * - `ignoreUnknownKeys = true`: Tolerates forward-compatible JSON payloads containing
     *   fields not yet present in the data model. Safe for consuming external APIs.
     * - `encodeDefaults = true`: Includes properties with default values in the serialized
     *   output, ensuring recipients with older models can still deserialize correctly.
     * - `coerceInputValues = true`: Coerces invalid enum values and non-nullable fields to
     *   their defaults instead of throwing a runtime exception.
     * - `isLenient = true`: Accepts unquoted string values and other minor JSON deviations,
     *   improving robustness when consuming loosely-formatted external data.
     * - `explicitNulls = false`: Omits null fields from the serialized output, producing
     *   cleaner, smaller payloads.
     */
    val default: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * Human-readable [Json] instance derived from [default].
     *
     * Identical configuration to [default] but with:
     * - `prettyPrint = true`: Formats output across multiple lines.
     * - `prettyPrintIndent = "    "`: Uses 4-space indentation for readability.
     *
     * Use this instance for export files, HAR archives, log output, and developer tooling
     * where human readability matters more than payload size.
     */
    @OptIn(ExperimentalSerializationApi::class)
    val pretty: Json = Json(default) {
        prettyPrint = true
        prettyPrintIndent = "    "
    }
}
