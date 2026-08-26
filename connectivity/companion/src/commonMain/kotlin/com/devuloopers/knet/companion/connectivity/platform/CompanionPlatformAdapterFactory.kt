package com.devuloopers.knet.companion.connectivity.platform

/** Creates one independently owned bundle of native companion connectivity adapters. */
public fun interface CompanionPlatformAdapterFactory {
    /**
     * Creates a platform adapter bundle whose lifecycle belongs to the caller.
     *
     * @return a new bundle that must eventually be closed by the product composition root.
     */
    public fun create(): CompanionPlatformAdapters
}
