package com.devuloopers.knet.ui.desktop.protectedtraffic.view

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.layout.KNetScreenLayout

/** Manager shell with compact default-policy controls; evidence and connection actions live in Traffic. */
@Composable
internal fun ProtectedTrafficWorkspace(modifier: Modifier = Modifier,
    headerActions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    KNetScreenLayout(
        title = "Protected Traffic",
        subtitle = "Manage default TLS handling and saved connection rules.",
        modifier = modifier,
        actions = headerActions,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        ManagerText("Saved rules take priority. Changes apply to new connections. View connection decisions in Traffic.", caption = true)
    }
}
