package com.devuloopers.knet.companion.di

import org.koin.core.module.Module

/** Portable Koin definitions installed by both companion product composition roots. */
public object CompanionSharedModules {
    /** Creates a fresh deterministic module set around product-provided platform prerequisites. */
    public fun create(): List<Module> = listOf(
        companionAdapterModule(),
        companionDataModule(),
        companionApplicationModule(),
        companionPresentationModule(),
    )
}
