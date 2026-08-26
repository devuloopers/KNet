package com.devuloopers.knet.companion.sharedui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.devuloopers.knet.companion.presentation.CompanionUiState

/** Android Studio preview for the shared companion screen. */
@Preview(showBackground = true)
@Composable
private fun CompanionAppPreview() {
    KNetCompanionApp(state = CompanionUiState())
}
