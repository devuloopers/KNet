package com.devuloopers.knet.engine.portal

import java.util.concurrent.ConcurrentHashMap

/**
 * Utility for loading template resources from the application classpath.
 *
 * Caches loaded template contents in memory to eliminate redundant disk/JAR read operations.
 */
object TemplateLoader {

    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Loads the string content of a classpath resource file.
     *
     * @param resourcePath The relative resource path (e.g., "templates/setup_portal.html.template").
     * @return The string content of the requested template.
     * @throws IllegalStateException if the resource file cannot be located on the classpath.
     */
    fun load(resourcePath: String): String {
        return cache.computeIfAbsent(resourcePath) { path ->
            val inputStream = TemplateLoader::class.java.classLoader.getResourceAsStream(path)
                ?: throw IllegalStateException("[TEMPLATE LOADER] Resource not found on classpath: $path")

            inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readText()
            }
        }
    }

    /**
     * Clears the in-memory template cache (primarily used for unit testing).
     */
    fun clearCache() {
        cache.clear()
    }
}
