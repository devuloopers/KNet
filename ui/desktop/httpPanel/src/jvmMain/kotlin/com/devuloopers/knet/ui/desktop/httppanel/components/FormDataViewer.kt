package com.devuloopers.knet.ui.desktop.httppanel.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.devuloopers.knet.ui.core.components.keyvalue.KNetReadOnlyKeyValueViewer
import com.devuloopers.knet.ui.core.components.keyvalue.KeyValueEntry

/**
 * Dedicated structured key-value viewer for URL-encoded (`application/x-www-form-urlencoded`)
 * and multipart form-data payload parameters.
 *
 * @param pairs List of decoded form field name-to-value string pairs.
 * @param modifier Composable layout modifier.
 */
@Composable
fun FormDataViewer(
    pairs: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    val entries = remember(pairs) {
        pairs.mapIndexed { index, (key, value) ->
            KeyValueEntry("form_param_$index", key, value)
        }
    }

    KNetReadOnlyKeyValueViewer(
        entries = entries,
        keyHeader = "FIELD NAME",
        valueHeader = "VALUE",
        emptyMessage = "This form-data body contained no parameters.",
        modifier = modifier.fillMaxSize()
    )
}
