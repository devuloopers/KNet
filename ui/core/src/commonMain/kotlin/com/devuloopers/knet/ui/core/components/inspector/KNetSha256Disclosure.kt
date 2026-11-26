package com.devuloopers.knet.ui.core.components.inspector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import com.devuloopers.knet.ui.core.components.button.ButtonSize
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.button.KNetCopyButton
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme

/** Shows a compact SHA-256 [hash] with an expandable, selectable full value and clipboard action. */
@Composable
fun KNetSha256Disclosure(hash: String, modifier: Modifier = Modifier) {
    var expanded by remember(hash) { mutableStateOf(false) }
    val spacing = KNetTheme.spacing
    val colors = KNetTheme.colors
    val shortened = if (hash.length > 20) "${hash.take(12)}…${hash.takeLast(8)}" else hash
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        KNetButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            variant = ButtonVariant.Ghost,
            size = ButtonSize.Compact,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Icon(if (expanded) KNetIcons.ChevronDown else KNetIcons.ChevronRight, null,
                    Modifier.size(KNetTheme.dimensions.iconSizeSmall), tint = colors.textSecondary)
                Text("SHA-256", style = KNetTheme.typography.caption, color = colors.textSecondary, maxLines = 1, softWrap = false)
                Text(shortened, style = KNetTheme.typography.codeSmall, color = colors.textPrimary,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
        if (expanded) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                SelectionContainer(Modifier.weight(1f, fill = false)) {
                    Text(hash, style = KNetTheme.typography.codeSmall, color = colors.textPrimary)
                }
                KNetCopyButton(hash, contentDescription = "Copy SHA-256 hash")
            }
        }
    }
}
