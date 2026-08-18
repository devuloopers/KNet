package com.devuloopers.knet.connectivity.desktop.artifact

import com.devuloopers.knet.connectivity.model.ConnectivityContextVersion
import com.devuloopers.knet.connectivity.model.SetupArtifact
import com.devuloopers.knet.connectivity.model.SetupArtifactId
import java.security.MessageDigest

/** Immutable content served by the dedicated setup delivery adapter. */
public class SetupArtifactContent(
    public val artifact: SetupArtifact,
    bytes: ByteArray,
) {
    private val content: ByteArray = bytes.copyOf()

    public fun copyBytes(): ByteArray = content.copyOf()
}

/** Versioned bounded artifact cache shared by independently registered setup providers. */
public class SetupArtifactStore(
    private val deliveryBaseUrl: String,
    private val maximumEntries: Int = 32,
    private val maximumBytes: Long = 16L * 1024L * 1024L,
) {
    private data class Entry(val content: SetupArtifactContent, val size: Int)
    private val entries = object : LinkedHashMap<String, Entry>(maximumEntries, 0.75f, true) {}
    private var retainedBytes: Long = 0L

    init {
        require(deliveryBaseUrl.startsWith("http://") || deliveryBaseUrl.startsWith("https://"))
        require(maximumEntries in 1..1_024)
        require(maximumBytes in 1L..(256L * 1024L * 1024L))
    }

    /** Publishes bytes under a key that includes the complete connectivity-context version. */
    public fun put(
        providerId: String,
        version: ConnectivityContextVersion,
        mediaType: String,
        extension: String,
        bytes: ByteArray,
    ): SetupArtifact {
        require(providerId.matches(SAFE_TOKEN)) { "Artifact provider ID is unsafe." }
        require(extension.matches(SAFE_TOKEN)) { "Artifact extension is unsafe." }
        require(bytes.size.toLong() <= maximumBytes) { "Artifact exceeds cache capacity." }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val id = "$providerId-v${version.value}-${digest.take(16)}.$extension"
        val artifact = SetupArtifact(
            id = SetupArtifactId(id),
            mediaType = mediaType,
            downloadUrl = "${deliveryBaseUrl.trimEnd('/')}/artifacts/$id",
            digest = "sha256:$digest",
        )
        synchronized(entries) {
            entries.remove(id)?.let { retainedBytes -= it.size }
            val content = SetupArtifactContent(artifact, bytes.copyOf())
            entries[id] = Entry(content, bytes.size)
            retainedBytes += bytes.size
            evict()
        }
        return artifact
    }

    public fun get(id: SetupArtifactId): SetupArtifactContent? = synchronized(entries) {
        entries[id.value]?.content?.let { content ->
            SetupArtifactContent(content.artifact, content.copyBytes())
        }
    }

    private fun evict() {
        val iterator = entries.entries.iterator()
        while ((entries.size > maximumEntries || retainedBytes > maximumBytes) && iterator.hasNext()) {
            val removed = iterator.next().value
            retainedBytes -= removed.size
            iterator.remove()
        }
    }

    private companion object {
        val SAFE_TOKEN: Regex = Regex("[A-Za-z0-9._-]+")
    }
}
