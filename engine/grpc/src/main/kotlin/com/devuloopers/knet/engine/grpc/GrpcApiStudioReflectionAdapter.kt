package com.devuloopers.knet.engine.grpc

import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolReflection
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolReflectionResult
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolReflectionTarget
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolSchemaImport
import com.devuloopers.knet.application.contract.apistudio.ApiStudioProtocolSchemaSource
import com.devuloopers.knet.domain.request.descriptor.RequestKindId
import com.google.protobuf.DescriptorProtos
import io.grpc.reflection.v1.ServerReflectionGrpc
import io.grpc.reflection.v1.ServerReflectionRequest
import io.grpc.reflection.v1.ServerReflectionResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.TimeUnit

/** Explicit gRPC server-reflection importer with bounded service and descriptor collection. */
class GrpcApiStudioReflectionAdapter(
    private val descriptors: GrpcDescriptorRegistry,
    private val channels: GrpcClientChannelFactory,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maximumServices: Int = 1_000,
    private val maximumDescriptorBytes: Int = 8 * 1_024 * 1_024,
) : ApiStudioProtocolReflection {
    init {
        require(maximumServices > 0) { "Maximum reflected service count must be positive." }
        require(maximumDescriptorBytes > 0) { "Maximum reflected descriptor bytes must be positive." }
    }

    override val kind: RequestKindId = RequestKindId.GRPC

    override suspend fun reflect(
        target: ApiStudioProtocolReflectionTarget,
    ): Result<ApiStudioProtocolReflectionResult> = try {
        Result.success(runInterruptible(ioDispatcher) { reflectBlocking(target) })
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun reflectBlocking(target: ApiStudioProtocolReflectionTarget): ApiStudioProtocolReflectionResult {
        val channel = channels.create(target.host, target.port, target.useTls, target.route)
        try {
            val call = ServerReflectionGrpc.newBlockingV2Stub(channel)
                .withDeadlineAfter(target.deadlineMillis, TimeUnit.MILLISECONDS)
                .serverReflectionInfo()
            check(call.write(ServerReflectionRequest.newBuilder().setListServices("").build())) {
                "gRPC reflection request stream closed before service discovery."
            }
            val servicesResponse = requireNotNull(call.read()) { "gRPC reflection returned no service list." }
            servicesResponse.throwIfError()
            require(servicesResponse.hasListServicesResponse()) {
                "gRPC reflection returned an unexpected response to service discovery."
            }
            val serviceNames = servicesResponse.listServicesResponse.serviceList
                .map { service -> service.name }
                .filterNot { name -> name.startsWith(REFLECTION_SERVICE_PREFIX) }
                .distinct()
                .sorted()
            require(serviceNames.isNotEmpty()) { "The server exposes no reflectable gRPC services." }
            require(serviceNames.size <= maximumServices) { "The reflected service count exceeds the limit." }

            val filesByName = LinkedHashMap<String, DescriptorProtos.FileDescriptorProto>()
            var retainedBytes = 0
            serviceNames.forEach { serviceName ->
                check(
                    call.write(
                        ServerReflectionRequest.newBuilder()
                            .setFileContainingSymbol(serviceName)
                            .build(),
                    ),
                ) { "gRPC reflection request stream closed while resolving $serviceName." }
                val response = requireNotNull(call.read()) {
                    "gRPC reflection returned no descriptor for $serviceName."
                }
                response.throwIfError()
                require(response.hasFileDescriptorResponse()) {
                    "gRPC reflection returned an unexpected descriptor response for $serviceName."
                }
                response.fileDescriptorResponse.fileDescriptorProtoList.forEach { encoded ->
                    val proto = DescriptorProtos.FileDescriptorProto.parseFrom(encoded)
                    val existing = filesByName[proto.name]
                    if (existing == null) {
                        retainedBytes += encoded.size()
                        require(retainedBytes <= maximumDescriptorBytes) {
                            "Reflected descriptors exceed the import limit."
                        }
                        filesByName[proto.name] = proto
                    } else {
                        require(existing == proto) { "Conflicting reflected descriptor file: ${proto.name}" }
                    }
                }
            }
            call.halfClose()

            val descriptorBytes = DescriptorProtos.FileDescriptorSet.newBuilder()
                .addAllFile(filesByName.values)
                .build()
                .toByteArray()
            require(descriptorBytes.size <= maximumDescriptorBytes) {
                "Reflected descriptor set exceeds the import limit."
            }
            val sourceId = GrpcDescriptorSourceId("reflection-${target.host}-${target.port}")
            val summary = descriptors.importDescriptorSet(sourceId, descriptorBytes).getOrThrow()
            return ApiStudioProtocolReflectionResult(
                source = ApiStudioProtocolSchemaSource(kind, sourceId.value, descriptorBytes),
                summary = ApiStudioProtocolSchemaImport(
                    sourceId = sourceId.value,
                    fileCount = summary.fileCount,
                    operationCount = summary.methodCount,
                ),
            )
        } finally {
            channel.shutdownNow()
        }
    }

    private fun ServerReflectionResponse.throwIfError() {
        if (!hasErrorResponse()) return
        val error = errorResponse
        throw IllegalStateException(
            "gRPC reflection failed (${error.errorCode}): ${error.errorMessage.ifBlank { "unknown error" }}",
        )
    }

    private companion object {
        const val REFLECTION_SERVICE_PREFIX = "grpc.reflection."
    }
}
