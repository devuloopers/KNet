package com.devuloopers.knet.ui.desktop.traffic.di

import com.devuloopers.knet.ui.desktop.traffic.viewmodel.TrafficViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin Dependency Injection module for `:ui:desktop:traffic`.
 */
public val trafficModule = module {
    singleOf(::TrafficViewModel)
}
