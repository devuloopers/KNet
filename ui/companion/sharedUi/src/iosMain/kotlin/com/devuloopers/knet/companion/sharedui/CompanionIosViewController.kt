package com.devuloopers.knet.companion.sharedui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.ComposeUIViewController
import com.devuloopers.knet.companion.presentation.CompanionUiState
import kotlinx.coroutines.flow.StateFlow
import platform.UIKit.UIViewController

/**
 * Creates the UIKit host for the shared companion interface.
 *
 * The future iOS product owns the supplied state stream and its lifecycle. Keeping that composition outside this
 * UI module prevents the shared interface from constructing repositories, transports, or process-scoped services.
 */
public fun KNetCompanionViewController(state: StateFlow<CompanionUiState>): UIViewController =
    ComposeUIViewController {
        val currentState by state.collectAsState()
        KNetCompanionApp(state = currentState)
    }
