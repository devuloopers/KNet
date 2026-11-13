package com.devuloopers.knet.products.desktop.graphql

import com.devuloopers.knet.application.contract.graphql.GraphQLManifestReader
import java.nio.file.Files
import java.nio.file.Path

/** Local filesystem adapter; callers dispatch IO and the extra byte lets the use case reject oversize files. */
class DesktopGraphQLManifestReader : GraphQLManifestReader {
    override suspend fun read(path: String, maximumBytes: Int): ByteArray {
        require(maximumBytes in 1..2_097_152)
        return Files.newInputStream(Path.of(path)).use { it.readNBytes(maximumBytes + 1) }
    }
}
