package com.devuloopers.knet.ui.desktop.traffic.filter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.theme.KNetColors
import com.devuloopers.knet.ui.desktop.traffic.model.TrafficFilter

/**
 * Filter toolbar hosting method, status, protocol, domain filters, and search bar.
 */
@Composable
public fun FilterToolbar(
    filter: TrafficFilter,
    onMethodChanged: (String) -> Unit,
    onStatusChanged: (String) -> Unit,
    onProtocolChanged: (String) -> Unit,
    onSearchChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(KNetColors.BackgroundDark)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MethodFilter(selectedMethod = filter.method, onMethodSelected = onMethodChanged)
        StatusFilter(selectedStatus = filter.statusGroup, onStatusSelected = onStatusChanged)
        ProtocolFilter(selectedProtocol = filter.protocol, onProtocolSelected = onProtocolChanged)
        SearchBar(query = filter.searchQuery, onQueryChanged = onSearchChanged, modifier = Modifier.weight(1f))
    }
}
