package com.devuloopers.knet.engine.grpc

import com.google.protobuf.AnyProto
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import com.google.protobuf.DurationProto
import com.google.protobuf.DynamicMessage
import com.google.protobuf.EmptyProto
import com.google.protobuf.FieldMaskProto
import com.google.protobuf.StructProto
import com.google.protobuf.TimestampProto
import com.google.protobuf.WrappersProto
import com.google.protobuf.util.JsonFormat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/** Stable identifier for one imported protobuf descriptor set. */
@JvmInline
value class GrpcDescriptorSourceId(val value: String) {
    init {
        require(value.isNotBlank()) { "gRPC descriptor source ID must not be blank." }
    }
}

/** Safe import summary exposed to settings and API Studio service browsers. */
data class GrpcDescriptorImportSummary(
    val sourceId: GrpcDescriptorSourceId,
    val fileCount: Int,
    val serviceCount: Int,
    val methodCount: Int,
)

/** Presentation-safe descriptor of one callable RPC. */
data class GrpcMethodSchema(
    val identity: GrpcMethodIdentity,
    val requestType: String,
    val responseType: String,
    val clientStreaming: Boolean,
    val serverStreaming: Boolean,
)

/** Descriptor-backed payload conversion result with explicit raw fallback reasons. */
sealed interface GrpcPayloadDecodeResult {
    data class DecodedJson(
        val json: String,
        val messageType: String,
    ) : GrpcPayloadDecodeResult

    data class Unavailable(val reason: String) : GrpcPayloadDecodeResult
}

/** Which protobuf type is expected for a framed message. */
enum class GrpcPayloadDirection {
    REQUEST,
    RESPONSE,
}

/**
 * Thread-safe immutable descriptor registry.
 *
 * Parsing and graph construction happen before the volatile state swap. Readers never observe a
 * partially imported descriptor set, and protobuf implementation types do not leave this module.
 */
class GrpcDescriptorRegistry(
    private val maximumSources: Int = 16,
    private val maximumDescriptorBytes: Int = 8 * 1_024 * 1_024,
    private val maximumDecodedBytes: Int = 16 * 1_024 * 1_024,
) {
    init {
        require(maximumSources > 0) { "Maximum descriptor source count must be positive." }
        require(maximumDescriptorBytes > 0) { "Maximum descriptor bytes must be positive." }
        require(maximumDecodedBytes > 0) { "Maximum decoded bytes must be positive." }
    }

    private val mutationLock = Any()

    @Volatile
    private var state: RegistryState = RegistryState.EMPTY

    /** Atomically imports or replaces one bounded binary `FileDescriptorSet`. */
    fun importDescriptorSet(
        sourceId: GrpcDescriptorSourceId,
        bytes: ByteArray,
    ): Result<GrpcDescriptorImportSummary> = runCatching {
        require(bytes.isNotEmpty()) { "Descriptor set must not be empty." }
        require(bytes.size <= maximumDescriptorBytes) { "Descriptor set exceeds the import limit." }
        val parsed = DescriptorProtos.FileDescriptorSet.parseFrom(bytes)
        require(parsed.fileCount > 0) { "Descriptor set contains no files." }
        synchronized(mutationLock) {
            val existing = state.sources
            require(sourceId in existing || existing.size < maximumSources) {
                "Descriptor source limit reached."
            }
            val sources = LinkedHashMap(existing)
            sources[sourceId] = parsed
            val rebuilt = buildState(sources)
            state = rebuilt
            rebuilt.summary(sourceId)
        }
    }

    /** Removes one source and atomically rebuilds the remaining dependency graph. */
    fun remove(sourceId: GrpcDescriptorSourceId): Boolean = synchronized(mutationLock) {
        if (sourceId !in state.sources) return@synchronized false
        val sources = LinkedHashMap(state.sources).apply { remove(sourceId) }
        state = buildState(sources)
        true
    }

    /** Lists presentation-safe RPC schemas in deterministic service/method order. */
    fun methods(): List<GrpcMethodSchema> = state.methods.values
        .map(ResolvedMethod::schema)
        .sortedWith(compareBy({ it.identity.serviceName }, { it.identity.methodName }))

    /** Resolves one method schema without exposing protobuf descriptors. */
    fun resolve(identity: GrpcMethodIdentity): GrpcMethodSchema? =
        state.methods[identity.path]?.schema

    /** Decodes one stored message with the request/response descriptor and bounded gzip support. */
    fun decode(
        identity: GrpcMethodIdentity,
        direction: GrpcPayloadDirection,
        payload: ByteArray,
        compressed: Boolean = false,
        compressionEncoding: String? = null,
    ): GrpcPayloadDecodeResult {
        val resolved = state.methods[identity.path]
            ?: return GrpcPayloadDecodeResult.Unavailable("grpc_descriptor_not_found")
        val bytes = when {
            !compressed -> payload
            compressionEncoding.equals("gzip", ignoreCase = true) -> runCatching {
                GZIPInputStream(ByteArrayInputStream(payload)).use { input ->
                    input.readBounded(maximumDecodedBytes)
                }
            }.getOrElse { return GrpcPayloadDecodeResult.Unavailable("grpc_gzip_decode_failed") }
            else -> return GrpcPayloadDecodeResult.Unavailable("grpc_compression_not_supported")
        }
        val descriptor = when (direction) {
            GrpcPayloadDirection.REQUEST -> resolved.descriptor.inputType
            GrpcPayloadDirection.RESPONSE -> resolved.descriptor.outputType
        }
        return runCatching {
            val message = DynamicMessage.parseFrom(descriptor, bytes)
            GrpcPayloadDecodeResult.DecodedJson(
                json = JsonFormat.printer().includingDefaultValueFields().print(message),
                messageType = descriptor.fullName,
            )
        }.getOrElse { GrpcPayloadDecodeResult.Unavailable("grpc_protobuf_decode_failed") }
    }

    /** Encodes strict protobuf JSON for API Studio without exposing protobuf runtime objects. */
    fun encode(
        identity: GrpcMethodIdentity,
        direction: GrpcPayloadDirection,
        json: String,
    ): Result<ByteArray> = runCatching {
        require(json.encodeToByteArray().size <= maximumDecodedBytes) {
            "gRPC JSON payload exceeds the limit."
        }
        val resolved = requireNotNull(state.methods[identity.path]) {
            "gRPC descriptor not found for ${identity.path}."
        }
        val descriptor = when (direction) {
            GrpcPayloadDirection.REQUEST -> resolved.descriptor.inputType
            GrpcPayloadDirection.RESPONSE -> resolved.descriptor.outputType
        }
        val builder = DynamicMessage.newBuilder(descriptor)
        JsonFormat.parser().merge(json, builder)
        builder.build().toByteArray().also { encoded ->
            require(encoded.size <= maximumDecodedBytes) { "Encoded gRPC payload exceeds the limit." }
        }
    }

    private fun buildState(
        sources: LinkedHashMap<GrpcDescriptorSourceId, DescriptorProtos.FileDescriptorSet>,
    ): RegistryState {
        if (sources.isEmpty()) return RegistryState.EMPTY
        val protos = LinkedHashMap<String, DescriptorProtos.FileDescriptorProto>()
        sources.values.forEach { set ->
            set.fileList.forEach { proto ->
                val existing = protos.putIfAbsent(proto.name, proto)
                require(existing == null || existing == proto) {
                    "Conflicting protobuf descriptor file: ${proto.name}"
                }
            }
        }
        val built = knownDescriptors().toMutableMap()
        val visiting = mutableSetOf<String>()

        fun build(name: String): Descriptors.FileDescriptor {
            built[name]?.let { return it }
            require(visiting.add(name)) { "Cyclic protobuf descriptor dependency: $name" }
            val proto = requireNotNull(protos[name]) { "Missing protobuf descriptor dependency: $name" }
            val dependencies = proto.dependencyList.map(::build).toTypedArray()
            return try {
                Descriptors.FileDescriptor.buildFrom(proto, dependencies).also { built[name] = it }
            } finally {
                visiting.remove(name)
            }
        }

        protos.keys.forEach(::build)
        val methods = LinkedHashMap<String, ResolvedMethod>()
        protos.keys.mapNotNull(built::get).forEach { file ->
            file.services.forEach { service ->
                service.methods.forEach { method ->
                    val identity = GrpcMethodIdentity(service.fullName, method.name)
                    require(methods.put(identity.path, ResolvedMethod(identity, method)) == null) {
                        "Duplicate gRPC method descriptor: ${identity.path}"
                    }
                }
            }
        }
        return RegistryState(sources, methods)
    }

    private fun RegistryState.summary(sourceId: GrpcDescriptorSourceId): GrpcDescriptorImportSummary {
        val source = sources.getValue(sourceId)
        val serviceCount = source.fileList.sumOf { it.serviceCount }
        val methodCount = source.fileList.sumOf { file -> file.serviceList.sumOf { it.methodCount } }
        return GrpcDescriptorImportSummary(sourceId, source.fileCount, serviceCount, methodCount)
    }

    private data class RegistryState(
        val sources: Map<GrpcDescriptorSourceId, DescriptorProtos.FileDescriptorSet>,
        val methods: Map<String, ResolvedMethod>,
    ) {
        companion object {
            val EMPTY = RegistryState(emptyMap(), emptyMap())
        }
    }

    private data class ResolvedMethod(
        val identity: GrpcMethodIdentity,
        val descriptor: Descriptors.MethodDescriptor,
    ) {
        val schema: GrpcMethodSchema = GrpcMethodSchema(
            identity = identity,
            requestType = descriptor.inputType.fullName,
            responseType = descriptor.outputType.fullName,
            clientStreaming = descriptor.isClientStreaming,
            serverStreaming = descriptor.isServerStreaming,
        )
    }

    private companion object {
        fun knownDescriptors(): Map<String, Descriptors.FileDescriptor> = listOf(
            DescriptorProtos.getDescriptor(),
            AnyProto.getDescriptor(),
            DurationProto.getDescriptor(),
            EmptyProto.getDescriptor(),
            FieldMaskProto.getDescriptor(),
            StructProto.getDescriptor(),
            TimestampProto.getDescriptor(),
            WrappersProto.getDescriptor(),
        ).associateBy { descriptor -> descriptor.name }
    }
}

private fun java.io.InputStream.readBounded(maximumBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maximumBytes, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        if (count == 0) continue
        require(total <= maximumBytes - count) { "Decoded gRPC payload exceeds the limit." }
        output.write(buffer, 0, count)
        total += count
    }
    return output.toByteArray()
}
