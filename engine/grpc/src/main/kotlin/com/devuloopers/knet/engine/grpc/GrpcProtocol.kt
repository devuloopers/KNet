package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.traffic.model.http.HeaderField
import com.devuloopers.knet.traffic.model.http.RequestTarget

/** Stable identity of one gRPC service method. */
data class GrpcMethodIdentity(
    val serviceName: String,
    val methodName: String,
) {
    init {
        require(serviceName.isNotBlank()) { "gRPC service name must not be blank." }
        require(methodName.isNotBlank()) { "gRPC method name must not be blank." }
    }

    /** Canonical HTTP/2 path used by native gRPC. */
    val path: String = "/$serviceName/$methodName"

    companion object {
        /** Parses only the canonical two-segment native gRPC path. */
        fun fromTarget(target: RequestTarget): GrpcMethodIdentity? {
            val pathAndQuery = when (target) {
                is RequestTarget.Absolute -> target.pathAndQuery
                is RequestTarget.Origin -> target.pathAndQuery
                is RequestTarget.Custom -> target.value
                RequestTarget.Asterisk, is RequestTarget.AuthorityForm -> return null
            }
            val path = pathAndQuery.substringBefore('?')
            val parts = path.removePrefix("/").split('/')
            if (parts.size != 2 || parts.any(String::isBlank)) return null
            return GrpcMethodIdentity(parts[0], parts[1])
        }

        /** Parses a canonical gRPC path from an absolute or origin-form authored URL. */
        fun fromUrl(url: String): GrpcMethodIdentity? {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return null
            val pathAndQuery = if ("://" in trimmed) {
                trimmed.substringAfter("://").substringAfter('/', missingDelimiterValue = "")
                    .let { path -> "/$path" }
            } else {
                trimmed
            }
            val parts = pathAndQuery.substringBefore('?').removePrefix("/").split('/')
            if (parts.size != 2 || parts.any(String::isBlank)) return null
            return GrpcMethodIdentity(parts[0], parts[1])
        }
    }
}

/** Native gRPC media-type and metadata helpers. */
internal object GrpcProtocol {
    fun isNativeContentType(value: String?): Boolean {
        val mediaType = value?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return mediaType == "application/grpc" ||
            mediaType == "application/grpc+proto" ||
            mediaType == "application/grpc+json"
    }

    fun header(headers: List<HeaderField>, name: String): String? = headers
        .firstOrNull { it.name.value.equals(name, ignoreCase = true) }
        ?.value
}
