package com.devuloopers.knet.ui.desktop.httppanel.model

/** Loads packaged response-editor templates owned by the HTTP panel. */
internal object ResponseBodyTemplateResources {
    private const val HTML_RESOURCE_PATH = "/templates/default_html_response.html"

    /** Default HTML document shown when the response editor has no payload. */
    val html: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        loadRequired(HTML_RESOURCE_PATH, "default HTML response")
    }

    private fun loadRequired(path: String, description: String): String =
        checkNotNull(ResponseBodyTemplateResources::class.java.getResourceAsStream(path)) {
            "$description resource is missing: $path"
        }.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            .also { content -> check(content.isNotBlank()) { "$description resource is empty." } }
}
