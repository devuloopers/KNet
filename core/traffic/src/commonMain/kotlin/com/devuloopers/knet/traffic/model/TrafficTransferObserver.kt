package com.devuloopers.knet.traffic.model

/** Optional transport-boundary observer, independent of capture, decoding and shaping. Never receives bytes. */
public fun interface TrafficTransferObserver {
    /** Opens one counter owner after ingress authentication; close is called once the admitted socket closes. */
    public fun open(ingress: IngressContext): TrafficTransferCounter
}

/** Payload-blind monotonic counter callbacks for one admitted downstream transport. */
public interface TrafficTransferCounter {
    /** Counts observed device-to-proxy bytes exactly once before protocol decoding. */
    public fun uploaded(bytes: Long)
    /** Counts successfully written proxy-to-device bytes exactly once after protocol encoding. */
    public fun downloaded(bytes: Long)
    /** Releases the connection lease without erasing cumulative counters. */
    public fun close()
}
