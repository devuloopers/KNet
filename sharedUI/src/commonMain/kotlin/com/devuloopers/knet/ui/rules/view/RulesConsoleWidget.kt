package com.devuloopers.knet.ui.rules.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.domain.rules.model.RuleModel
import com.devuloopers.knet.domain.rules.model.RulesIntent
import com.devuloopers.knet.domain.rules.model.RulesUiState
import com.devuloopers.knet.theme.KNetColors

/**
 * Pure layout Composable view representing the Rules & Breakpoints Console.
 * Adheres strictly to Clean Architecture by rendering pre-formatted UI states
 * and emitting user actions as [RulesIntent]s.
 *
 * @param state Immutable [RulesUiState] emitted by ViewModel.
 * @param onIntent Callback lambda emitting user intents.
 * @param modifier Layout modifiers.
 */
@Composable
fun RulesConsoleWidget(
    state: RulesUiState,
    onIntent: (RulesIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTab = when (state) {
        is RulesUiState.Success -> state.activeTab
        else -> "Rules"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KNetColors.BackgroundDark)
    ) {
        // --- Header Tabs ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(KNetColors.SurfaceDark)
                .padding(horizontal = 8.dp)
                .horizontalScroll(rememberScrollState())
                .clipToBounds(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf("Rules", "Breakpoints", "Rewrite", "Map Local")
            tabs.forEach { tab ->
                val isSelected = tab == activeTab
                Box(
                    modifier = Modifier
                        .background(
                            color = if (isSelected) KNetColors.ActiveBlue else KNetColors.SurfaceDark,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable { onIntent(RulesIntent.SelectTab(tab)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.White else KNetColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // --- Body Table Content ---
        Box(modifier = Modifier.weight(1f)) {
            when (state) {
                is RulesUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(24.dp),
                        color = KNetColors.ActiveBlue,
                        strokeWidth = 2.dp
                    )
                }
                is RulesUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(KNetColors.SurfaceDark)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Name", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                            Text("Type", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(80.dp))
                            Text("Action", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                            Text("Enabled", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(60.dp))
                            Text("Hits", color = KNetColors.TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.width(50.dp))
                        }

                        // Table Rows
                        state.rules.forEach { rule ->
                            RuleRow(rule = rule, onToggle = { enabled -> onIntent(RulesIntent.ToggleRule(rule.id, enabled)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleRow(rule: RuleModel, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = rule.name, color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(2f))
        Text(text = rule.type, color = KNetColors.TextSecondary, fontSize = 11.sp, modifier = Modifier.width(80.dp))
        Text(text = rule.action, color = KNetColors.ActiveBlue, fontSize = 11.sp, modifier = Modifier.width(100.dp))
        Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.Center) {
            Switch(
                checked = rule.enabled,
                onCheckedChange = { onToggle(it) }
            )
        }
        Text(text = rule.hitCount.toString(), color = KNetColors.TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.width(50.dp))
    }
}
