@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.devuloopers.knet.companion.packettunnel.network

import kotlinx.cinterop.*
import platform.darwin.dispatch_data_create
import platform.darwin.dispatch_data_create_map
import platform.darwin.dispatch_data_t
import platform.posix.memcpy

internal fun ByteArray.toDispatchData(): dispatch_data_t {
    if (isEmpty()) return dispatch_data_create(null, 0u, null) {}
    val nativeBytes = nativeHeap.allocArray<ByteVar>(size)
    usePinned { pinned -> memcpy(nativeBytes, pinned.addressOf(0), size.toULong()) }
    return dispatch_data_create(nativeBytes, size.toULong(), null) {
        nativeHeap.free(nativeBytes.rawValue)
    }
}

internal fun dispatch_data_t.copyBytes(): ByteArray = memScoped {
    val bytes = alloc<COpaquePointerVar>()
    val size = alloc<ULongVar>()
    val mapped = dispatch_data_create_map(this@copyBytes, bytes.ptr, size.ptr)
    if (mapped == null || bytes.value == null || size.value == 0uL) return@memScoped ByteArray(0)
    ByteArray(size.value.toInt()).also { output ->
        output.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes.value, size.value) }
    }
}
