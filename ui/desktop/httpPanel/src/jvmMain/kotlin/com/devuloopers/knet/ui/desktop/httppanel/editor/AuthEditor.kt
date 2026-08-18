package com.devuloopers.knet.ui.desktop.httppanel.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import com.devuloopers.knet.ui.desktop.httppanel.model.ApiKeyLocation
import com.devuloopers.knet.ui.desktop.httppanel.model.AuthState
import com.devuloopers.knet.ui.desktop.httppanel.model.AuthType

/**
 * Modern, high-density Authorization Editor supporting No Auth, Bearer Token, Basic Auth, API Key, and Inherit Auth.
 */
@Composable
fun AuthEditor(
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
                text = "Type:",
                style = typography.caption.copy(
                    color = themeColors.textMuted,
                    fontWeight = FontWeight.Medium
                )
            )

            KNetDropdown(
                items = authTypeOptions,
                selectedItem = state.authType.label,
                onItemSelected = { selectedLabel ->
                    val selectedType = AuthType.entries.firstOrNull { it.label == selectedLabel } ?: AuthType.NO_AUTH
                    onStateChange(state.copy(authType = selectedType))
                },
                modifier = Modifier.widthIn(min = 180.dp)
            )
        }

        // Active Auth Sub-Form
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (state.authType) {
                AuthType.NO_AUTH -> {
                    AuthEmptyState(
                        icon = KNetIcons.Info,
                        message = "This request does not use any authorization."
                    )
                }

                AuthType.INHERIT -> {
                    AuthEmptyState(
                        icon = KNetIcons.Info,
                        message = "This request inherits authorization settings from its parent collection."
                    )
                }

                AuthType.BEARER_TOKEN -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Text(
                            text = "Token",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                        KNetInputField(
                            value = state.bearerToken,
                            onValueChange = { onStateChange(state.copy(bearerToken = it)) },
                            placeholder = "Enter Bearer Token...",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "The authorization header will be automatically generated as 'Bearer <token>'.",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    }
                }

                AuthType.BASIC_AUTH -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Text(
                            text = "Username",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                        KNetInputField(
                            value = state.basicUsername,
                            onValueChange = { onStateChange(state.copy(basicUsername = it)) },
                            placeholder = "Enter username...",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Password",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                        KNetPasswordField(
                            value = state.basicPassword,
                            onValueChange = { onStateChange(state.copy(basicPassword = it)) },
                            placeholder = "Enter password...",
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            text = "Credentials will be Base64-encoded and sent in the 'Authorization: Basic <hash>' header.",
                            style = typography.caption.copy(color = themeColors.textMuted)
                        )
                    }
                }

                AuthType.API_KEY -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(spacing.md)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(spacing.xxs)
                            ) {
                                Text(
                                    text = "Key",
                                    style = typography.caption.copy(color = themeColors.textMuted)
                                )
                                KNetInputField(
                                    value = state.apiKeyName,
                                    onValueChange = { onStateChange(state.copy(apiKeyName = it)) },
                                    placeholder = "e.g. X-API-Key",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(spacing.xxs)
                            ) {
                                Text(
                                    text = "Value",
                                    style = typography.caption.copy(color = themeColors.textMuted)
                                )
                                KNetInputField(
                                    value = state.apiKeyValue,
                                    onValueChange = { onStateChange(state.copy(apiKeyValue = it)) },
                                    placeholder = "Enter API Key value...",
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = spacing.xs)
                        ) {
                            Text(
                                text = "Add to:",
                                style = typography.caption.copy(color = themeColors.textMuted)
                            )
                            val locationOptions = ApiKeyLocation.entries.map { it.label }
                            KNetDropdown(
                                items = locationOptions,
                                selectedItem = state.apiKeyLocation.label,
                                onItemSelected = { selectedLabel ->
                                    val loc = ApiKeyLocation.entries.firstOrNull { it.label == selectedLabel } ?: ApiKeyLocation.HEADER
                                    onStateChange(state.copy(apiKeyLocation = loc))
                                },
                                modifier = Modifier.width(160.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared Empty State representation for passive auth types (NO_AUTH, INHERIT).
 */
@Composable
private fun AuthEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    val themeColors = KNetTheme.colors
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeColors.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = themeColors.textMuted,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = message,
                style = typography.bodySmall.copy(color = themeColors.textMuted)
            )
        }
    }
}
