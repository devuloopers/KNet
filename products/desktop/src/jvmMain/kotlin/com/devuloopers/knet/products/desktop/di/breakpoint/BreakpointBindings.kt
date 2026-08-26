package com.devuloopers.knet.products.desktop.di.breakpoint

import com.devuloopers.knet.application.contract.breakpoint.BreakpointControl
import com.devuloopers.knet.application.coordinator.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.contract.breakpoint.BreakpointCaptureAvailability
import com.devuloopers.knet.application.contract.breakpoint.BreakpointGate
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolExtension
import com.devuloopers.knet.application.contract.breakpoint.BreakpointProtocolRegistry
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointGate
import com.devuloopers.knet.application.contract.breakpoint.ProtocolMessageBreakpointControl
import com.devuloopers.knet.application.usecase.breakpoint.ClearPendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.DropMatchingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingProtocolMessageBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ResolveBreakpointUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ResolveProtocolMessageBreakpointUseCase
import com.devuloopers.knet.application.usecase.breakpoint.BreakpointProtocolRuleUseCase
import com.devuloopers.knet.application.usecase.breakpoint.PrepareBreakpointRuleDraftUseCase
import com.devuloopers.knet.data.desktop.rules.repository.RulesRepositoryImpl
import com.devuloopers.knet.domain.rules.repository.RulesRepository
import com.devuloopers.knet.domain.rules.usecase.DeleteRuleUseCase
import com.devuloopers.knet.domain.rules.usecase.GetRulesUseCase
import com.devuloopers.knet.domain.rules.usecase.ObserveGlobalInterceptionUseCase
import com.devuloopers.knet.domain.rules.usecase.ObserveRulesUseCase
import com.devuloopers.knet.domain.rules.usecase.SaveRuleUseCase
import com.devuloopers.knet.domain.rules.usecase.ToggleGlobalInterceptionUseCase
import com.devuloopers.knet.domain.rules.usecase.ToggleRuleUseCase
import com.devuloopers.knet.storage.database.KNetDatabase
import com.devuloopers.knet.ui.desktop.breakpointmanager.viewmodel.BreakpointManagerViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/** Breakpoint gate, rule persistence, active sessions, workflows, and presentation. */
internal val breakpointBindings: Module = module {
    single { BreakpointProtocolRegistry(getAll<BreakpointProtocolExtension>()) }
    single { BreakpointCoordinator(protocolRegistry = get()) }
    single<BreakpointGate> { get<BreakpointCoordinator>() }
    single<BreakpointControl> { get<BreakpointCoordinator>() }
    single<BreakpointCaptureAvailability> { get<BreakpointCoordinator>() }
    single<ProtocolMessageBreakpointGate> { get<BreakpointCoordinator>() }
    single<ProtocolMessageBreakpointControl> { get<BreakpointCoordinator>() }

    single {
        RulesRepositoryImpl(
            breakpointRuleDao = get<KNetDatabase>().breakpointRuleDao(),
            breakpointControl = get(),
            coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }
    single<RulesRepository> { get<RulesRepositoryImpl>() }
    factory { GetRulesUseCase(get()) }
    factory { ObserveRulesUseCase(get()) }
    factory { SaveRuleUseCase(get()) }
    factory { ToggleRuleUseCase(get()) }
    factory { DeleteRuleUseCase(get()) }
    factory { ObserveGlobalInterceptionUseCase(get()) }
    factory { ToggleGlobalInterceptionUseCase(get()) }
    factory { ObservePendingBreakpointsUseCase(get()) }
    factory { ObservePendingProtocolMessageBreakpointsUseCase(get()) }
    factory { ResolveBreakpointUseCase(get()) }
    factory { ResolveProtocolMessageBreakpointUseCase(get()) }
    factory { DropMatchingBreakpointsUseCase(get()) }
    factory { ClearPendingBreakpointsUseCase(get()) }
    factory { BreakpointProtocolRuleUseCase(get()) }
    factory { PrepareBreakpointRuleDraftUseCase(get(), get()) }

    viewModel {
        BreakpointManagerViewModel(
            getRulesUseCase = get(),
            observeGlobalInterceptionUseCase = get(),
            observePendingBreakpointsUseCase = get(),
            observePendingProtocolMessageBreakpointsUseCase = get(),
            saveRuleUseCase = get(),
            toggleRuleUseCase = get(),
            deleteRuleUseCase = get(),
            toggleGlobalInterceptionUseCase = get(),
            resolveBreakpointUseCase = get(),
            clearPendingBreakpointsUseCase = get(),
            resolveProtocolMessageBreakpointUseCase = get(),
            breakpointProtocolRuleUseCase = get(),
            describeRequestUseCase = get(),
            ioDispatcher = Dispatchers.Default,
        )
    }
}
