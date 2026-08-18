package com.devuloopers.knet.products.desktop.di.breakpoint

import com.devuloopers.knet.application.port.breakpoint.BreakpointControlPort
import com.devuloopers.knet.application.port.breakpoint.BreakpointCoordinator
import com.devuloopers.knet.application.port.breakpoint.BreakpointGate
import com.devuloopers.knet.application.usecase.breakpoint.ClearPendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.DropMatchingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ObservePendingBreakpointsUseCase
import com.devuloopers.knet.application.usecase.breakpoint.ResolveBreakpointUseCase
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

/** Breakpoint gate, rule persistence, active sessions, workflows, and presentation. */
internal val breakpointBindings: Module = module {
    single { BreakpointCoordinator() }
    single<BreakpointGate> { get<BreakpointCoordinator>() }
    single<BreakpointControlPort> { get<BreakpointCoordinator>() }

    single<RulesRepository> {
        RulesRepositoryImpl(get<KNetDatabase>().breakpointRuleDao(), get())
    }
    factory { GetRulesUseCase(get()) }
    factory { ObserveRulesUseCase(get()) }
    factory { SaveRuleUseCase(get()) }
    factory { ToggleRuleUseCase(get()) }
    factory { DeleteRuleUseCase(get()) }
    factory { ObserveGlobalInterceptionUseCase(get()) }
    factory { ToggleGlobalInterceptionUseCase(get()) }
    factory { ObservePendingBreakpointsUseCase(get()) }
    factory { ResolveBreakpointUseCase(get()) }
    factory { DropMatchingBreakpointsUseCase(get()) }
    factory { ClearPendingBreakpointsUseCase(get()) }

    viewModel {
        BreakpointManagerViewModel(
            getRulesUseCase = get(),
            observeGlobalInterceptionUseCase = get(),
            observePendingBreakpointsUseCase = get(),
            saveRuleUseCase = get(),
            toggleRuleUseCase = get(),
            deleteRuleUseCase = get(),
            toggleGlobalInterceptionUseCase = get(),
            resolveBreakpointUseCase = get(),
            clearPendingBreakpointsUseCase = get(),
        )
    }
}
