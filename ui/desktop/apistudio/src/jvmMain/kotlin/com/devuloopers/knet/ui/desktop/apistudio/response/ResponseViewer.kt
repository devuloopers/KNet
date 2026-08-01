package com.devuloopers.knet.ui.desktop.apistudio.response

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.ui.core.feedback.EmptyState
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.apistudio.model.ResponsePresentation

/**
 * Primary HTTP Response Viewer panel composable hosting ResponseStatusBar and sub-views (Body, Headers, Cookies, Metadata).
 *
 * @param presentation Formatted HTTP response model.
 * @param modifier Layout modifier.
 */
@Composable
public fun ResponseViewer(
    presentation: ResponsePresentation?,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf("Body") }

    if (presentation == null) {
        EmptyState(
            title = "No Response",
            description = "Click 'Send' to execute request and inspect response.",
            modifier = modifier
        )
        return
    }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        ResponseStatusBar(presentation = presentation, modifier = Modifier.padding(bottom = 6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KNetColors.BackgroundDark)
                .padding(vertical = 4.dp)
        ) {
            listOf("Body", "Headers", "Cookies", "Metadata").forEach { tabName ->
                val isSelected = tabName == activeSubTab
                Text(
                    text = tabName,
                    color = if (isSelected) KNetColors.ActiveBlue else KNetColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { activeSubTab = tabName }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        when (activeSubTab) {
            "Body" -> ResponseBodyView(body = presentation.body, modifier = Modifier.weight(1f))
            "Headers" -> ResponseHeadersView(headers = presentation.headers, modifier = Modifier.weight(1f))
            "Cookies" -> ResponseCookiesView(cookies = presentation.cookies, modifier = Modifier.weight(1f))
            "Metadata" -> ResponseMetadataView(presentation = presentation, modifier = Modifier.weight(1f))
        }
    }
}
