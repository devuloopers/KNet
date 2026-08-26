package com.devuloopers.knet.companion.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devuloopers.knet.companion.presentation.CompanionUiState
import com.devuloopers.knet.companion.sharedui.KNetCompanionApp

/** Android lifecycle host for the shared Compose Multiplatform companion UI. */
class MainActivity : ComponentActivity() {
    /** Creates the UI host without starting transport or VPN resources. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as KNetCompanionApplication).graph
        setContent {
            val network by graph.network.observe().collectAsStateWithLifecycle()
            val registrations by graph.registrations.registrations.collectAsStateWithLifecycle()
            KNetCompanionApp(
                state = CompanionUiState(
                    registrations = registrations,
                    network = network,
                ),
            )
        }
    }
}
