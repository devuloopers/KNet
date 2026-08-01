package com.devuloopers.knet.ui.desktop.inspector.request

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
import com.devuloopers.knet.ui.desktop.inspector.model.RequestPresentation
import com.devuloopers.knet.ui.desktop.inspector.model.RequestSubTab

/**
 * Request Inspector view container hosting sub-tabs (Headers, Query, Cookies, Body).
 */
@Composable
public fun RequestInspector(
    request: RequestPresentation,
    modifier: Modifier = Modifier
) {
    var activeSubTab by remember { mutableStateOf(RequestSubTab.HEADERS) }

    Column(modifier = modifier.fillMaxSize().padding(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(KNetColors.BackgroundDark)
                .padding(vertical = 4.dp)
        ) {
            RequestSubTab.entries.forEach { subTab ->
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
            RequestSubTab.HEADERS -> RequestHeadersView(headers = request.headers, modifier = Modifier.weight(1f))
            RequestSubTab.PARAMS -> QueryParametersView(queryParams = request.queryParams, modifier = Modifier.weight(1f))
            RequestSubTab.COOKIES -> RequestCookiesView(cookies = request.cookies, modifier = Modifier.weight(1f))
            RequestSubTab.BODY -> RequestBodyView(body = request.body, modifier = Modifier.weight(1f))
        }
    }
}
