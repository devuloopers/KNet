package com.devuloopers.knet.engine.script.javascript

import org.graalvm.polyglot.Source
import java.util.concurrent.ConcurrentHashMap

/**
 * Loader and cache manager for pre-parsed GraalJS static runtime polyfill [Source] objects.
 * Caches static polyfill JavaScript ASTs to eliminate repeated parsing overhead across polyglot contexts.
 */
object ScriptResourceLoader {

    private val sourceCache = ConcurrentHashMap<String, Source>()

    /**
     * Reads a resource JavaScript file and returns its cached Graal [Source] object.
     *
     * @param resourcePath Path to the JavaScript resource file inside classloader resources.
     * @return Cached or newly created Graal [Source].
     */
    fun loadSource(resourcePath: String): Source {
        return sourceCache.computeIfAbsent(resourcePath) { path ->
            val content = javaClass.getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }
                ?: error("Failed to load JavaScript runtime resource at $path")
            Source.newBuilder("js", content, path).build()
        }
    }
}
