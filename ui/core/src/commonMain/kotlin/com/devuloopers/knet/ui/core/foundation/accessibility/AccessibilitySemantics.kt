package com.devuloopers.knet.ui.core.foundation.accessibility

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Modifier extension adding explicit accessibility content description semantics.
 */
public fun Modifier.knetSemantics(description: String): Modifier {
    return this.then(
        Modifier.semantics {
            contentDescription = description
        }
    )
}
