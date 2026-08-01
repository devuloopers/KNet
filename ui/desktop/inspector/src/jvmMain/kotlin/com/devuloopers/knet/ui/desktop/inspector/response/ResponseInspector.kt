package com.devuloopers.knet.ui.desktop.inspector.response

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
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.inspector.model.ResponsePresentation
import com.devuloopers.knet.ui.desktop.inspector.model.ResponseSubTab

/**
 * Response Inspector view container hosting sub-tabs (Body, Headers, Cookies, Trailers).
 */
@Composable
public fun ResponseInspector(
    response: ResponsePresentation,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf(ResponseSubTab.BODY) }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KNetColors.BackgroundDark)
                .padding(vertical = 4.dp)
        ) {
            ResponseSubTab.entries.forEach { subTab ->
                val isSelected = subTab == activeSubTab
                Text(
                    text = subTab.label,
                    color = if (isSelected) KNetColors.ActiveBlue else KNetColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { activeSubTab = subTab }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        when (activeSubTab) {
            ResponseSubTab.BODY -> ResponseBodyView(body = response.body, modifier = Modifier.weight(1f))
            ResponseSubTab.HEADERS -> ResponseHeadersView(headers = response.headers, modifier = Modifier.weight(1f))
            ResponseSubTab.COOKIES -> ResponseCookiesView(cookies = response.cookies, modifier = Modifier.weight(1f))
            ResponseSubTab.TRAILERS -> ResponseTrailersView(trailers = response.trailers, modifier = Modifier.weight(1f))
        }
    }
}
