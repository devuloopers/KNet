package com.devuloopers.knet.ui.core.components.inspector

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** One captured metadata field, independent of its transport or parser. */
@Immutable
data class KNetInspectorField(val label: String, val value: String, val code: Boolean = false)

/**
 * Aligns metadata using the measured label column rather than a feature-specific fixed gutter.
 * @param fields Finite captured/display facts; long labels and values retain single-line overflow previews.
 * @param modifier Parent constraints owned by the shared inspector viewport.
 */
@Composable
fun KNetInspectorFields(fields: List<KNetInspectorField>, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelStyle = KNetTheme.typography.bodySmall
    val measuredWidth = remember(fields.map { it.label }, labelStyle, density) {
        with(density) { (fields.maxOfOrNull { measurer.measure(it.label, labelStyle, maxLines = 1).size.width } ?: 0).toDp() }
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val labelWidth = minOf(measuredWidth, maxWidth * 0.45f)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
            fields.forEach { field ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.lg)) {
                    KNetInspectorText(field.label, Modifier.width(labelWidth), color = KNetTheme.colors.textSecondary)
                    KNetInspectorText(field.value, Modifier.weight(1f), code = field.code)
                }
            }
        }
    }
}

/** Human-readable section title shared by confirmed protocol facts and ordinary connection metadata. */
@Composable
fun KNetInspectorSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, style = KNetTheme.typography.titleMedium, color = KNetTheme.colors.textPrimary,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

/** Accessible disclosure control; the feature retains expansion state keyed to its selected capture identity. */
@Composable
fun KNetInspectorDisclosure(title: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    KNetButton(onClick = onToggle, variant = ButtonVariant.Ghost, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(KNetTheme.spacing.sm)) {
            Icon(if (expanded) KNetIcons.ChevronDown else KNetIcons.ChevronRight, null,
                Modifier.size(KNetTheme.dimensions.iconSizeMedium))
            KNetInspectorText(title, Modifier.weight(1f), bold = true)
        }
    }
}
