package com.devuloopers.knet.ui.desktop.apistudio.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.input.KNetInputField
import com.devuloopers.knet.ui.core.components.input.KNetPasswordField
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.apistudio.model.ApiKeyLocation
import com.devuloopers.knet.ui.desktop.apistudio.model.AuthState
import com.devuloopers.knet.ui.desktop.apistudio.model.AuthType

/**
 * Modern, high-density Authorization Editor View supporting No Auth, Bearer Token, Basic Auth, API Key, and Inherit Auth.
 */
@Composable
public fun AuthEditorView(
    state: AuthState,
    onStateChange: (AuthState) -> Unit,
    modifier: Modifier = Modifier
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    val authTypeOptions = AuthType.entries.map { it.label }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(spacing.md)
    ) {
        // Auth Type Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Auth Type",
                style = typography.bodyMedium.copy(
                    color = themeColors.textSecondary,
                    fontWeight = FontWeight.Medium
                )
            )

            KNetDropdown(
                selectedItem = state.authType.label,
                items = authTypeOptions,
                onItemSelected = { selectedLabel ->
                    val selectedType = AuthType.entries.find { it.label == selectedLabel } ?: AuthType.NO_AUTH
                    onStateChange(state.copy(authType = selectedType))
                },
                modifier = Modifier.widthIn(min = 180.dp, max = 240.dp)
            )
        }

        // Dynamic Auth Configuration Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(themeColors.surfaceVariant, RoundedCornerShape(6.dp))
                .border(1.dp, themeColors.border, RoundedCornerShape(6.dp))
                .padding(spacing.md)
        ) {
            when (state.authType) {
                AuthType.NO_AUTH -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This request does not use any authentication.",
                            style = typography.bodyMedium.copy(color = themeColors.textMuted)
                        )
                    }
                }
                AuthType.INHERIT -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This request inherits authentication from parent collection.",
                            style = typography.bodyMedium.copy(color = themeColors.accent)
                        )
                    }
                }
                AuthType.BEARER_TOKEN -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Text(
                            text = "Token",
                            style = typography.labelMedium.copy(
                                color = themeColors.textSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        KNetInputField(
                            value = state.bearerToken,
                            onValueChange = { onStateChange(state.copy(bearerToken = it)) },
                            placeholder = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = KNetIcons.Info,
                                contentDescription = "Info",
                                modifier = Modifier.size(14.dp),
                                tint = themeColors.textMuted
                            )
                            Text(
                                text = "The authorization header will be automatically generated as 'Authorization: Bearer <token>'",
                                style = typography.caption.copy(color = themeColors.textMuted)
                            )
                        }
                    }
                }
                AuthType.BASIC_AUTH -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Text(
                            text = "Username",
                            style = typography.labelMedium.copy(
                                color = themeColors.textSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        KNetInputField(
                            value = state.basicUsername,
                            onValueChange = { onStateChange(state.copy(basicUsername = it)) },
                            placeholder = "Enter username",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Password",
                            style = typography.labelMedium.copy(
                                color = themeColors.textSecondary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        KNetPasswordField(
                            value = state.basicPassword,
                            onValueChange = { onStateChange(state.copy(basicPassword = it)) },
                            placeholder = "Enter password",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = KNetIcons.Info,
                                contentDescription = "Info",
                                modifier = Modifier.size(14.dp),
                                tint = themeColors.textMuted
                            )
                            Text(
                                text = "Credentials will be base64 encoded as 'Authorization: Basic <base64>'",
                                style = typography.caption.copy(color = themeColors.textMuted)
                            )
                        }
                    }
                }
                AuthType.API_KEY -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Key",
                                    style = typography.labelMedium.copy(
                                        color = themeColors.textSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                KNetInputField(
                                    value = state.apiKeyName,
                                    onValueChange = { onStateChange(state.copy(apiKeyName = it)) },
                                    placeholder = "X-API-Key",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Value",
                                    style = typography.labelMedium.copy(
                                        color = themeColors.textSecondary,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                KNetInputField(
                                    value = state.apiKeyValue,
                                    onValueChange = { onStateChange(state.copy(apiKeyValue = it)) },
                                    placeholder = "Enter API Key value",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Text(
                            text = "Add to",
                            style = typography.labelMedium.copy(
                                color = themeColors.textSecondary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        KNetDropdown(
                            selectedItem = state.apiKeyLocation.label,
                            items = ApiKeyLocation.entries.map { it.label },
                            onItemSelected = { selectedLabel ->
                                val loc = ApiKeyLocation.entries.find { it.label == selectedLabel } ?: ApiKeyLocation.HEADER
                                onStateChange(state.copy(apiKeyLocation = loc))
                            },
                            modifier = Modifier.widthIn(min = 140.dp, max = 200.dp)
                        )
                    }
                }
            }
        }
    }
}
