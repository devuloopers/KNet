package com.devuloopers.knet.ui.desktop.protectedtraffic.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import com.devuloopers.knet.domain.protectedtraffic.*
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.input.KNetSearchField
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.protectedtraffic.model.*
import com.devuloopers.knet.website.*
import kotlin.test.Test

/** Uses the real manager shell, default selector and virtualized rules table, without policy storage. */
class WebsiteProtectedArtworkTest {
    /** Authored exact-domain rules illustrate saved connection handling, not intercepted content. */
    @Test fun renderProtectedTraffic() {
        val rules = listOf("billing.example.com" to ProtectedTrafficAction.TUNNEL,
            "api.example.com" to ProtectedTrafficAction.INSPECT, "metrics.example.com" to ProtectedTrafficAction.BLOCK)
            .mapIndexed { index, (host, action) -> ProtectedTrafficRule(ProtectedTrafficRuleId("website-$index"),
                action = action, destination = ProtectedDestinationSelector.Exact(host, false), port = 443,
                transport = ProtectedTransportProtocol.TCP) }
        val state = ProtectedTrafficState(configuration = ProtectedTrafficConfiguration(rules = rules, configurationReady = true))
        renderArtwork(ArtworkScene.PROTECTED_TRAFFIC, listOf("Protected Traffic", "billing.example.com")) {
            ProtectedTrafficWorkspace(headerActions = {
                Column(verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.xs)) {
                    ManagerText("Default TLS handling", caption = true)
                    KNetDropdown(state.configuration.defaultHandling, ProtectedTrafficDefaultHandling.entries,
                        itemText = { it.displayLabel }, onItemSelected = {}) }
            }) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.md)) {
                        KNetSearchField("", {}, Modifier.weight(1f), placeholder = "Search destination, application, port or action…")
                        KNetButton({}) { Text("Add rule") }
                    }
                    ProtectedRulesTable(state, { _, _ -> }, {}, {}, {}, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}
