package com.devuloopers.knet.engine.simulator

import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import com.devuloopers.knet.domain.networkconditions.EffectiveNetworkCondition
import com.devuloopers.knet.domain.networkconditions.NetworkConditionMatcher
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionHttpInspectionInput
import com.devuloopers.knet.application.usecase.networkconditions.NetworkConditionSemanticResolver
import com.devuloopers.knet.engine.proxy.pipeline.ProxyChannelAttributes
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufHolder
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import io.netty.channel.FileRegion
import io.netty.handler.codec.http.HttpContent
import io.netty.handler.codec.http.FullHttpMessage
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.LastHttpContent
import io.netty.handler.codec.http.DefaultHttpContent
import io.netty.handler.codec.http.DefaultLastHttpContent
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.PromiseCombiner
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Request-aware downstream pipeline shaper. Upload is channelRead; download is write. */
class NetworkConditionChannelHandler(
    private val engine: NetworkConditionEngine,
    private val semanticResolver: NetworkConditionSemanticResolver? = null,
) : ChannelDuplexHandler() {
    private var host: String? = null
    private var port: Int? = null
    private var sequence = 0L
    private var activeUploadSelection: RequestSelection? = null
    private var activeDownloadSelection: RequestSelection? = null
    private var activeDownloadIsFinalResponse: Boolean = false
    private val responseSelections = ArrayDeque<RequestSelection>()
    private var flow: AutoCloseable? = null
    private var configurationSubscription: AutoCloseable? = null
    private var pendingBytes = 0L
    private val uploadQueue = ArrayDeque<PendingForward>()
    private val downloadQueue = ArrayDeque<PendingForward>()
    private var uploadTask: ScheduledFuture<*>? = null
    private var downloadTask: ScheduledFuture<*>? = null
    private var uploadDeadlineNanos = 0L
    private var downloadDeadlineNanos = 0L
    private var closed = false

    override fun handlerAdded(context: ChannelHandlerContext) {
        configurationSubscription = engine.observeConfigurationChanges {
            context.executor().execute {
                if (!closed && context.channel().isActive) {
                    refreshSelections()
                    reapplyQueued(context)
                }
            }
        }
    }

    override fun channelActive(context: ChannelHandlerContext) {
        flow = engine.openFlow()
        context.fireChannelActive()
    }

    override fun channelRead(context: ChannelHandlerContext, message: Any) {
        val selection = when (message) {
            is HttpRequest -> selectRequest(context, message).also { selected ->
                activeUploadSelection = selected
                if (selected != null && !message.method().name().equals("CONNECT", ignoreCase = true)) {
                    responseSelections.addLast(selected)
                }
            }
            is HttpContent -> activeUploadSelection
            else -> null
        }
        shape(context, message, null, NetworkConditionDirection.UPLOAD, selection)
        if (message is LastHttpContent) activeUploadSelection = null
    }

    override fun write(context: ChannelHandlerContext, message: Any, promise: ChannelPromise) {
        val selection = when (message) {
            is HttpResponse -> responseSelections.firstOrNull().also {
                activeDownloadSelection = it
                activeDownloadIsFinalResponse = message.status().code() !in 100..199 || message.status().code() == 101
            }
            is HttpContent -> activeDownloadSelection
            else -> null
        }
        shape(context, message, promise, NetworkConditionDirection.DOWNLOAD, selection)
        if (message is LastHttpContent && activeDownloadIsFinalResponse) {
            responseSelections.removeFirstOrNull()
            activeDownloadSelection = null
            activeDownloadIsFinalResponse = false
        }
    }

    override fun channelInactive(context: ChannelHandlerContext) {
        closeConfigurationSubscription()
        clearQueues()
        closeFlow()
        context.fireChannelInactive()
    }

    override fun handlerRemoved(context: ChannelHandlerContext) {
        closeConfigurationSubscription()
        clearQueues()
        closeFlow()
    }

    private fun shape(
        context: ChannelHandlerContext,
        message: Any,
        promise: ChannelPromise?,
        direction: NetworkConditionDirection,
        selection: RequestSelection? = null,
    ) {
        val destinationHost = selection?.host ?: host ?: context.channel().attr(ProxyChannelAttributes.ROUTE_HOST).get()
        val destinationPort = selection?.port ?: port ?: context.channel().attr(ProxyChannelAttributes.PORT).get()
        if (destinationHost == null || destinationPort == null) {
            forward(context, message, promise, direction)
            return
        }
        context.channel().attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION).set(
            selection?.effective?.let(engine::appliedCondition)
                ?: engine.appliedCondition(destinationHost, destinationPort),
        )
        val bytes = readableBytes(message)
        val mtu = selection?.effective?.let(engine::virtualMtu)
            ?: engine.virtualMtu(destinationHost, destinationPort)
        if (mtu != null && bytes > mtu &&
            splitAndShape(context, message, promise, direction, mtu, selection)
        ) return
        val plan = selection?.effective?.let { effective ->
            engine.plan(effective, direction, bytes, ++sequence)
        } ?: engine.plan(destinationHost, destinationPort, direction, bytes, ++sequence)
        when (plan) {
            NetworkConditionPlan.PassThrough -> {
                if (!engine.conditionsEnabled() && queue(direction).isNotEmpty()) {
                    releaseQueuedImmediately(direction)
                    forward(context, message, promise, direction, flush = promise != null)
                } else if (queue(direction).isEmpty()) {
                    forward(context, message, promise, direction)
                } else {
                    schedule(context, message, promise, direction, bytes, deadline(direction), selection)
                }
            }
            is NetworkConditionPlan.Fail -> fail(context, message, promise, plan.behavior)
            is NetworkConditionPlan.Delay -> {
                val now = engine.currentTimeNanos()
                val plannedDue = if (now > Long.MAX_VALUE - plan.nanoseconds) {
                    Long.MAX_VALUE
                } else {
                    now + plan.nanoseconds
                }
                val due = maxOf(deadline(direction), plannedDue)
                setDeadline(direction, due)
                schedule(context, message, promise, direction, bytes, due, selection)
            }
        }
    }

    private fun splitAndShape(
        context: ChannelHandlerContext,
        message: Any,
        promise: ChannelPromise?,
        direction: NetworkConditionDirection,
        maximumChunkBytes: Int,
        selection: RequestSelection?,
    ): Boolean {
        // A full request/response owns its head and trailers as one object. Splitting only its ByteBuf would erase
        // that metadata; protocol-aware aggregated messages therefore remain one safe scheduling unit.
        if (message is FullHttpMessage) return false
        val chunks = when (message) {
            is LastHttpContent -> splitLastHttpContent(message, maximumChunkBytes)
            is HttpContent -> splitHttpContent(message, maximumChunkBytes)
            is ByteBuf -> splitByteBuf(message, maximumChunkBytes)
            else -> return false
        }
        if (chunks.size <= 1) {
            chunks.forEach(ReferenceCountUtil::release)
            return false
        }
        ReferenceCountUtil.release(message)
        if (promise == null) {
            chunks.forEach { chunk -> shape(context, chunk, null, direction, selection) }
        } else {
            val combiner = PromiseCombiner(context.executor())
            chunks.forEach { chunk ->
                val childPromise = context.newPromise()
                combiner.add(childPromise)
                shape(context, chunk, childPromise, direction, selection)
            }
            combiner.finish(promise)
        }
        return true
    }

    private fun splitByteBuf(buffer: ByteBuf, maximumChunkBytes: Int): List<Any> = buildList {
        var offset = buffer.readerIndex()
        var remaining = buffer.readableBytes()
        while (remaining > 0) {
            val count = minOf(remaining, maximumChunkBytes)
            add(buffer.retainedSlice(offset, count))
            offset += count
            remaining -= count
        }
    }

    private fun splitHttpContent(content: HttpContent, maximumChunkBytes: Int): List<Any> =
        splitByteBuf(content.content(), maximumChunkBytes).map { chunk ->
            DefaultHttpContent(chunk as ByteBuf)
        }

    private fun splitLastHttpContent(content: LastHttpContent, maximumChunkBytes: Int): List<Any> {
        val buffers = splitByteBuf(content.content(), maximumChunkBytes).map { it as ByteBuf }
        return buffers.mapIndexed { index, buffer ->
            if (index == buffers.lastIndex) {
                DefaultLastHttpContent(buffer).also { last ->
                    last.trailingHeaders().set(content.trailingHeaders())
                }
            } else {
                DefaultHttpContent(buffer)
            }
        }
    }

    private fun schedule(
        context: ChannelHandlerContext,
        message: Any,
        promise: ChannelPromise?,
        direction: NetworkConditionDirection,
        bytes: Int,
        dueNanos: Long,
        selection: RequestSelection?,
    ) {
        if (pendingBytes > MAXIMUM_FLOW_QUEUED_BYTES - bytes || !engine.tryQueue(bytes)) {
            engine.recordFaultedFlow()
            fail(context, message, promise, NetworkFailureBehavior.ResetFlow)
            return
        }
        pendingBytes += bytes
        val queue = queue(direction)
        queue.addLast(PendingForward(context, message, promise, bytes, dueNanos, selection))
        if (queue.size == 1) {
            scheduleHead(
                direction = direction,
                delayNanos = (dueNanos - engine.currentTimeNanos()).coerceAtLeast(0L),
            )
        }
    }

    private fun deadline(direction: NetworkConditionDirection): Long = when (direction) {
        NetworkConditionDirection.UPLOAD -> uploadDeadlineNanos
        NetworkConditionDirection.DOWNLOAD -> downloadDeadlineNanos
    }

    private fun setDeadline(direction: NetworkConditionDirection, value: Long) {
        when (direction) {
            NetworkConditionDirection.UPLOAD -> uploadDeadlineNanos = value
            NetworkConditionDirection.DOWNLOAD -> downloadDeadlineNanos = value
        }
    }

    private fun queue(direction: NetworkConditionDirection): ArrayDeque<PendingForward> = when (direction) {
        NetworkConditionDirection.UPLOAD -> uploadQueue
        NetworkConditionDirection.DOWNLOAD -> downloadQueue
    }

    private fun scheduleHead(direction: NetworkConditionDirection, delayNanos: Long) {
        val queue = queue(direction)
        val head = queue.firstOrNull() ?: return
        val task = head.context.executor().schedule({
            val completed = queue.removeFirst()
            if (completed.context.channel().isActive) {
                pendingBytes = (pendingBytes - completed.bytes).coerceAtLeast(0L)
                engine.releaseQueued(completed.bytes)
                forward(
                    completed.context,
                    completed.message,
                    completed.promise,
                    direction,
                    flush = completed.promise != null,
                )
            } else {
                abandon(completed)
            }
            val next = queue.firstOrNull()
            if (next == null) {
                setDeadline(direction, 0L)
                setTask(direction, null)
            } else {
                scheduleHead(direction, (next.dueNanos - engine.currentTimeNanos()).coerceAtLeast(0L))
            }
        }, delayNanos, TimeUnit.NANOSECONDS)
        setTask(direction, task)
    }

    private fun setTask(direction: NetworkConditionDirection, task: ScheduledFuture<*>?) {
        when (direction) {
            NetworkConditionDirection.UPLOAD -> uploadTask = task
            NetworkConditionDirection.DOWNLOAD -> downloadTask = task
        }
    }

    private fun clearQueues() {
        uploadTask?.cancel(false)
        downloadTask?.cancel(false)
        uploadTask = null
        downloadTask = null
        while (uploadQueue.isNotEmpty()) abandon(uploadQueue.removeFirst())
        while (downloadQueue.isNotEmpty()) abandon(downloadQueue.removeFirst())
        uploadDeadlineNanos = 0L
        downloadDeadlineNanos = 0L
    }

    private fun releaseQueuedImmediately(direction: NetworkConditionDirection) {
        when (direction) {
            NetworkConditionDirection.UPLOAD -> uploadTask?.cancel(false)
            NetworkConditionDirection.DOWNLOAD -> downloadTask?.cancel(false)
        }
        setTask(direction, null)
        val queue = queue(direction)
        while (queue.isNotEmpty()) {
            val pending = queue.removeFirst()
            pendingBytes = (pendingBytes - pending.bytes).coerceAtLeast(0L)
            engine.releaseQueued(pending.bytes)
            forward(
                pending.context,
                pending.message,
                pending.promise,
                direction,
                flush = pending.promise != null,
            )
        }
        setDeadline(direction, 0L)
    }

    /** Re-reserves pending bytes after enable/profile/rule edits while preserving direction order. */
    private fun reapplyQueued(context: ChannelHandlerContext) {
        if (!engine.conditionsEnabled()) {
            releaseQueuedImmediately(NetworkConditionDirection.UPLOAD)
            releaseQueuedImmediately(NetworkConditionDirection.DOWNLOAD)
            return
        }
        reapplyQueued(context, NetworkConditionDirection.UPLOAD)
        reapplyQueued(context, NetworkConditionDirection.DOWNLOAD)
    }

    private fun reapplyQueued(context: ChannelHandlerContext, direction: NetworkConditionDirection) {
        when (direction) {
            NetworkConditionDirection.UPLOAD -> uploadTask?.cancel(false)
            NetworkConditionDirection.DOWNLOAD -> downloadTask?.cancel(false)
        }
        setTask(direction, null)
        setDeadline(direction, 0L)
        val queue = queue(direction)
        val pending = buildList {
            while (queue.isNotEmpty()) add(queue.removeFirst())
        }
        pending.forEach { item ->
            pendingBytes = (pendingBytes - item.bytes).coerceAtLeast(0L)
            engine.abandon(item.bytes)
            shape(context, item.message, item.promise, direction, item.selection)
        }
    }

    private fun abandon(pending: PendingForward) {
        pendingBytes = (pendingBytes - pending.bytes).coerceAtLeast(0L)
        engine.abandon(pending.bytes)
        ReferenceCountUtil.release(pending.message)
        pending.promise?.tryFailure(IllegalStateException("Network-conditioned channel closed."))
    }

    private fun forward(
        context: ChannelHandlerContext,
        message: Any,
        promise: ChannelPromise?,
        direction: NetworkConditionDirection,
        flush: Boolean = false,
    ) {
        engine.recordForwarded(direction, readableBytes(message))
        if (promise == null) {
            context.fireChannelRead(message)
        } else if (flush) {
            context.writeAndFlush(message, promise)
        } else {
            context.write(message, promise)
        }
    }

    private fun fail(
        context: ChannelHandlerContext,
        message: Any,
        promise: ChannelPromise?,
        behavior: NetworkFailureBehavior,
    ) {
        ReferenceCountUtil.release(message)
        promise?.tryFailure(IllegalStateException("Network condition fault: ${behavior::class.simpleName}"))
        if (behavior != NetworkFailureBehavior.Timeout) context.close()
    }

    /** Resolves semantic criteria once per request and retains only bounded immutable input for live policy edits. */
    private fun selectRequest(context: ChannelHandlerContext, request: HttpRequest): RequestSelection? {
        val destination = resolveNetworkConditionDestination(context, request)
        val destinationHost = destination?.host
            ?: context.channel().attr(ProxyChannelAttributes.ROUTE_HOST).get()
        if (destinationHost == null) {
            host = null
            port = null
            context.channel().attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION).set(null)
            return null
        }
        val destinationPort = destination?.port
            ?: context.channel().attr(ProxyChannelAttributes.PORT).get()
            ?: if (context.channel().attr(ProxyChannelAttributes.IS_SSL).get() == true) 443 else 80
        host = destinationHost
        port = destinationPort
        val semanticInput = destination?.takeUnless {
            request.method().name().equals("CONNECT", ignoreCase = true)
        }?.let { resolved ->
            if (request is io.netty.handler.codec.http.FullHttpRequest) {
                networkConditionInspectionInput(context, request, resolved)
            } else {
                NetworkConditionHttpInspectionInput(
                    request = networkConditionRequestSnapshot(context, request, resolved),
                    requestBody = null,
                    requestBodyComplete = false,
                )
            }
        }
        return RequestSelection(
            host = destinationHost,
            port = destinationPort,
            semanticInput = semanticInput,
            effective = resolveSelection(destinationHost, destinationPort, semanticInput),
        ).also { selection ->
            context.channel().attr(ProxyChannelAttributes.APPLIED_NETWORK_CONDITION).set(
                engine.appliedCondition(selection.effective),
            )
        }
    }

    private fun resolveSelection(
        destinationHost: String,
        destinationPort: Int,
        semanticInput: NetworkConditionHttpInspectionInput?,
    ): EffectiveNetworkCondition? = if (semanticInput != null && semanticResolver != null) {
        semanticResolver.resolveHttp(
            configuration = engine.configurationSnapshot(),
            host = destinationHost,
            port = destinationPort,
            input = semanticInput,
        )
    } else {
        NetworkConditionMatcher.resolve(engine.configurationSnapshot(), destinationHost, destinationPort)
    }

    /** Re-evaluates semantic criteria once after a live edit before queued units are replanned. */
    private fun refreshSelections() {
        val selections = buildSet {
            activeUploadSelection?.let(::add)
            activeDownloadSelection?.let(::add)
            addAll(responseSelections)
            uploadQueue.mapNotNullTo(this) { pending -> pending.selection }
            downloadQueue.mapNotNullTo(this) { pending -> pending.selection }
        }
        selections.forEach { selection ->
            selection.effective = resolveSelection(selection.host, selection.port, selection.semanticInput)
        }
    }

    private fun closeFlow() {
        if (closed) return
        closed = true
        flow?.close()
        flow = null
    }

    private fun closeConfigurationSubscription() {
        configurationSubscription?.close()
        configurationSubscription = null
    }

    private fun readableBytes(message: Any): Int = when (message) {
        is ByteBuf -> message.readableBytes()
        is ByteBufHolder -> message.content().readableBytes()
        is FileRegion -> message.count().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        else -> 0
    }

    private data class PendingForward(
        val context: ChannelHandlerContext,
        val message: Any,
        val promise: ChannelPromise?,
        val bytes: Int,
        val dueNanos: Long,
        val selection: RequestSelection?,
    )

    /** Event-loop-confined request selection shared with its ordered response and pending units. */
    private class RequestSelection(
        val host: String,
        val port: Int,
        val semanticInput: NetworkConditionHttpInspectionInput?,
        var effective: EffectiveNetworkCondition?,
    )

    private companion object {
        const val MAXIMUM_FLOW_QUEUED_BYTES: Long = 8L * 1_024L * 1_024L
    }
}
