package com.devuloopers.knet.companion.connectivity.platform

/**
 * Platform construction boundary for companion connectivity adapters.
 *
 * No constructor is declared deliberately. Android and iOS actual classes therefore accept only their own native
 * dependencies without exposing a context, an `Any` bridge, or an opaque platform handle to common code.
 */
public expect class PlatformCompanionAdapterFactory : CompanionPlatformAdapterFactory {
    override fun create(): CompanionPlatformAdapters
}
