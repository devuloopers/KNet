package com.devuloopers.knet.ui.desktop.traffic.inspector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.tooltip.KNetTooltipHost
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.model.GraphQLQueryAvailability
import com.devuloopers.knet.ui.core.components.divider.HorizontalDivider
import com.devuloopers.knet.ui.core.components.inspector.*
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficInspectorPresentation
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficInspectorProtocol

/** One shared scrolling body; parser diagnostics never occupy a separate fixed footer or alter HTTP results. */
@Composable
internal fun TrafficOverviewPanel(identity: String, presentation: TrafficInspectorPresentation, modifier: Modifier = Modifier,
    onViewQuery: (() -> Unit)? = null) {
    var expanded by remember(identity) { mutableStateOf(false) }
    KNetInspectorContent(modifier, contentIdentity = identity) {
        presentation.failure?.let {
            KNetInspectorSectionTitle("Request failed")
            KNetInspectorParagraph(it)
            HorizontalDivider()
        }
        if (presentation.protocolFields.isNotEmpty() || presentation.documents.isNotEmpty() || presentation.graphQLQueryAvailability != null) {
            KNetInspectorSectionTitle("Protocol details")
            if (presentation.protocolFields.isNotEmpty()) KNetInspectorFields(presentation.protocolFields)
            presentation.documents.forEach { document ->
                KNetInspectorSectionTitle(document.title)
                if (document.fields.isNotEmpty()) KNetInspectorFields(document.fields.map { KNetInspectorField(it.label, it.value, true) })
                document.summary?.takeIf(String::isNotBlank)?.let { KNetInspectorParagraph(it) }
            }
            presentation.graphQLQueryAvailability?.let { availability ->
                KNetInspectorActions {
                    KNetTooltipHost(availability.description) { interactions ->
                        KNetButton(onClick = {
                            if (availability == GraphQLQueryAvailability.AVAILABLE) onViewQuery?.invoke()
                        },
                            enabled = availability == GraphQLQueryAvailability.AVAILABLE && onViewQuery != null,
                            variant = ButtonVariant.Secondary, size = ButtonSize.Compact,
                            interactionSource = interactions,
                            modifier = Modifier.semantics { stateDescription = availability.description }) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
                                Text("View query", maxLines = 1, softWrap = false)
                                Icon(KNetIcons.ChevronRight, null, Modifier.size(KNetTheme.dimensions.iconSizeSmall))
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
        }
        if (presentation.protocol == TrafficInspectorProtocol.SSE)
            KNetInspectorParagraph("Body preview is bounded. Captured stream records are available under Events.")
        if (presentation.trailers.isNotEmpty()) {
            KNetInspectorSectionTitle("Trailers")
            KNetInspectorFields(presentation.trailers)
            HorizontalDivider()
        }
        KNetInspectorSectionTitle("Connection")
        KNetInspectorFields(presentation.connection)
        HorizontalDivider()
        KNetInspectorDisclosure("Technical details", expanded, { expanded = !expanded })
        if (expanded) {
            KNetInspectorFields(presentation.technical)
            if (presentation.analysisUnavailable) KNetInspectorParagraph("Additional protocol details could not be prepared. The captured network result is unchanged.")
        }
    }
}
