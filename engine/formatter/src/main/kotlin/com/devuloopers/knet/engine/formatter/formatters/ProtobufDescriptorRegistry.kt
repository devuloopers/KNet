package com.devuloopers.knet.engine.formatter.formatters

import com.google.protobuf.DescriptorProtos
import com.google.protobuf.Descriptors
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry that stores loaded Protobuf descriptors from compiled FileDescriptorSet (.desc) schemas.
 */
object ProtobufDescriptorRegistry {
    private val descriptors = ConcurrentHashMap<String, Descriptors.Descriptor>()

    /**
     * Registers schemas from a FileDescriptorSet binary input stream.
     */
    fun registerSchema(inputStream: InputStream) {
        try {
            val set = DescriptorProtos.FileDescriptorSet.parseFrom(inputStream)
            val fileDescriptors = mutableListOf<Descriptors.FileDescriptor>()

            for (fileProto in set.fileList) {
                val deps = fileDescriptors.toTypedArray()
                val fd = Descriptors.FileDescriptor.buildFrom(fileProto, deps)
                fileDescriptors.add(fd)

                for (messageType in fd.messageTypes) {
                    descriptors[messageType.fullName] = messageType
                }
            }
        } catch (_: Exception) {
            // Gracefully ignore corrupt descriptors
        }
    }

    /**
     * Finds a descriptor by its full package/message name (e.g. "com.example.User").
     */
    fun findDescriptor(messageName: String): Descriptors.Descriptor? {
        return descriptors[messageName]
    }

    /**
     * Clears all registered schemas.
     */
    fun clear() {
        descriptors.clear()
    }
}
