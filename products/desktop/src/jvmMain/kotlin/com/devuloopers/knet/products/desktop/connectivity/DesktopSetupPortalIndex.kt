package com.devuloopers.knet.products.desktop.connectivity

/** Loads the product-owned setup portal index from its packaged HTML resource. */
internal object DesktopSetupPortalIndex {
    private const val RESOURCE_PATH = "/templates/setup_portal_index.html"
    private val document: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        checkNotNull(DesktopSetupPortalIndex::class.java.getResourceAsStream(RESOURCE_PATH)) {
            "Desktop setup portal index resource is missing: $RESOURCE_PATH"
        }.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
            .also { content -> check(content.isNotBlank()) { "Desktop setup portal index resource is empty." } }
    }

    /** Returns the immutable packaged setup portal document. */
    fun render(): String = document
}
