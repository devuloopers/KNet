package com.devuloopers.knet.modifier

/**
 * Defines which part of an HTTP transaction should be targeted by a modifier rule.
 */
enum class RuleTarget {
    /** Modify a request header. */
    REQUEST_HEADER,
    /** Modify a response header. */
    RESPONSE_HEADER,
    /** Modify a request query parameter. */
    REQUEST_QUERY,
    /** Modify the request body text. */
    REQUEST_BODY,
    /** Modify the response body text. */
    RESPONSE_BODY,
    /** Override the HTTP response status code. */
    RESPONSE_STATUS
}

/**
 * Defines the type of modification to perform when a rule matches.
 */
enum class RuleAction {
    /** Add a new header, query parameter, or body field. */
    ADD,
    /** Modify an existing header, query parameter, or body text using regex. */
    MODIFY,
    /** Remove an existing header or query parameter. */
    REMOVE
}

/**
 * Describes a traffic modifier rule that matches an HTTP request URL and modifies
 * headers, query parameters, body contents, or response status codes automatically.
 *
 * @property id Unique identifier for the rule.
 * @property name Human-readable display name.
 * @property urlPattern Regex pattern to match the full request URL.
 * @property target Which part of the HTTP transaction to modify.
 * @property action The type of modification to perform.
 * @property matchValue For MODIFY/REMOVE actions: the existing key name (header name, query name).
 * @property newValue For ADD/MODIFY actions: the replacement or new value to inject.
 * @property enabled Whether this rule is active.
 */
data class ModifierRule(
    val id: String,
    val name: String,
    val urlPattern: String,
    val target: RuleTarget,
    val action: RuleAction,
    val matchValue: String? = null,
    val newValue: String? = null,
    val enabled: Boolean = true
)

/**
 * Describes a Map Local rule that intercepts matching HTTP requests and responds
 * directly from a local file on disk, bypassing upstream network calls entirely.
 *
 * @property id Unique identifier for the rule.
 * @property name Human-readable display name.
 * @property urlPattern Regex pattern to match the full request URL.
 * @property localFilePath Absolute path to the local file to serve as the response body.
 * @property mimeType Optional MIME type override for the Content-Type header. If null, inferred from file extension.
 * @property enabled Whether this rule is active.
 */
data class MapLocalRule(
    val id: String,
    val name: String,
    val urlPattern: String,
    val localFilePath: String,
    val mimeType: String? = null,
    val enabled: Boolean = true
)

/**
 * Describes a Map Remote rule that transparently redirects matching HTTP requests
 * to an alternate target host, port, and protocol.
 *
 * @property id Unique identifier for the rule.
 * @property name Human-readable display name.
 * @property urlPattern Regex pattern to match the full request URL.
 * @property targetHost The remote host to redirect the request to.
 * @property targetPort The port to connect to on the remote host.
 * @property targetProtocol Either "http" or "https". Defaults to "https".
 * @property enabled Whether this rule is active.
 */
data class MapRemoteRule(
    val id: String,
    val name: String,
    val urlPattern: String,
    val targetHost: String,
    val targetPort: Int,
    val targetProtocol: String = "https",
    val enabled: Boolean = true
)
