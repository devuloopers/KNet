package com.devuloopers.knet.products.desktop.di.networkconditions

import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionRuntimeTelemetry
import com.devuloopers.knet.application.contract.networkconditions.NetworkConditionsRepository
import com.devuloopers.knet.application.contract.traffic.TrafficQuery
import com.devuloopers.knet.application.usecase.networkconditions.PrepareNetworkConditionRuleUseCase
import com.devuloopers.knet.data.desktop.networkconditions.RoomNetworkConditionsRepository
import com.devuloopers.knet.engine.simulator.NetworkConditionEngine
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.ui.desktop.networkconditions.viewmodel.NetworkConditionsViewModel
import com.devuloopers.knet.companion.model.PacketConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkConditionConfiguration
import com.devuloopers.knet.domain.networkconditions.NetworkFailureBehavior
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlinx.coroutines.CoroutineScope

internal val networkConditionsBindings: Module = module {
    single {
        RoomNetworkConditionsRepository(get<KNetDatabase>().networkConditionDao())
    } bind NetworkConditionsRepository::class
    single {
        val repository = get<NetworkConditionsRepository>()
        NetworkConditionEngine(
            configuration = repository.configuration,
            scope = get<CoroutineScope>(),
        )
    } bind NetworkConditionRuntimeTelemetry::class
    factory { PrepareNetworkConditionRuleUseCase(get<TrafficQuery>(), get()) }
    viewModel {
        NetworkConditionsViewModel(
            repository = get(),
            runtimeTelemetry = get(),
            prepareRule = get(),
        )
    }
}

/** Maps the active global profile to the companion's payload-blind UDP policy. */
internal fun NetworkConditionConfiguration.toPacketConditionConfiguration(): PacketConditionConfiguration {
    val profile = profile(globalProfileId)
    if (!enabled || profile == null || !profile.hasPacketEffects) return PacketConditionConfiguration.Disabled
    val failureLoss = when (val failure = profile.failure) {
        NetworkFailureBehavior.None -> 0
        NetworkFailureBehavior.Offline,
        NetworkFailureBehavior.Timeout,
        NetworkFailureBehavior.ResetFlow,
        -> 100
        is NetworkFailureBehavior.SeededReset -> failure.probabilityPercent
    }
    return PacketConditionConfiguration(
        enabled = true,
        uploadBitsPerSecond = profile.upload.effectiveBitsPerSecond,
        downloadBitsPerSecond = profile.download.effectiveBitsPerSecond,
        latencyMillis = profile.latencyMillis,
        jitterMillis = profile.jitterMillis,
        lossPercent = maxOf(profile.packetLossPercent, failureLoss),
        duplicationPercent = profile.packetDuplicationPercent,
        reorderingPercent = profile.packetReorderingPercent,
        seed = (profile.failure as? NetworkFailureBehavior.SeededReset)?.seed
            ?: PacketConditionConfiguration.DEFAULT_SEED,
    )
}
