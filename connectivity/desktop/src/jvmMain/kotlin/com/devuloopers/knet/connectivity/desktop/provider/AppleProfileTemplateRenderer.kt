package com.devuloopers.knet.connectivity.desktop.provider

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64

/** Values that may vary inside the resource-backed Apple configuration profile. */
@Serializable
internal data class AppleProfileTemplateModel(
    val proxyServer: String,
    val proxyServerPort: Int,
    val certificateBase64: String,
)

/** Renders a typed model into the packaged profile template with strict placeholder validation. */
internal object AppleProfileTemplateRenderer {
    private const val RESOURCE_PATH = "/templates/apple_proxy_profile.mobileconfig.xml"
    private val unresolvedPlaceholder = Regex("\\{\\{[A-Za-z][A-Za-z0-9]*}}")
    private val template: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        checkNotNull(AppleProfileTemplateRenderer::class.java.getResourceAsStream(RESOURCE_PATH)) {
            "Apple profile template is missing: $RESOURCE_PATH"
        }.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
    }

    fun render(host: String, port: Int, certificate: ByteArray): String {
        val model = AppleProfileTemplateModel(
            proxyServer = host,
            proxyServerPort = port,
            certificateBase64 = Base64.encode(certificate),
        )
        val serializedValues = Json.encodeToJsonElement(model).jsonObject
        var rendered = template
        serializedValues.forEach { (name, element) ->
            val placeholder = "{{$name}}"
            require(rendered.indexOf(placeholder) >= 0) {
                "Apple profile template does not contain $placeholder."
            }
            require(rendered.indexOf(placeholder) == rendered.lastIndexOf(placeholder)) {
                "Apple profile template contains duplicate $placeholder values."
            }
            val primitive = element as? JsonPrimitive
                ?: error("Apple profile template value $name must be scalar.")
            val replacement = if (primitive.isString) primitive.content.xmlEscape() else primitive.content
            rendered = rendered.replace(placeholder, replacement)
        }
        require(!unresolvedPlaceholder.containsMatchIn(rendered)) {
            "Apple profile template contains an unresolved placeholder."
        }
        return rendered
    }
}

private fun String.xmlEscape(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
