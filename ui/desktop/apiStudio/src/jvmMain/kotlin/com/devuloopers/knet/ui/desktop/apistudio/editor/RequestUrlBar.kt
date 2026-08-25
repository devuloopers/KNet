package com.devuloopers.knet.ui.desktop.apistudio.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devuloopers.knet.ui.core.components.button.ButtonVariant
import com.devuloopers.knet.ui.core.components.button.KNetButton
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdown
import com.devuloopers.knet.ui.core.components.dropdown.KNetDropdownSize
import com.devuloopers.knet.ui.core.components.input.InputFieldConfig
import com.devuloopers.knet.ui.core.components.input.KNetTextField
import com.devuloopers.knet.ui.core.foundation.icons.KNetIcons
import com.devuloopers.knet.ui.core.foundation.theme.KNetTheme
import com.devuloopers.knet.ui.desktop.httppanel.theme.HttpMethodColors
import com.devuloopers.knet.traffic.model.http.HttpMethod
import com.devuloopers.knet.domain.clientNetwork.model.HttpVersionPreference

private val httpMethods = listOf(
    HttpMethod.GET,
    HttpMethod.POST,
    HttpMethod.PUT,
    HttpMethod.PATCH,
    HttpMethod.DELETE,
    HttpMethod.HEAD,
    HttpMethod.OPTIONS
)

private val httpVersionPreferences = HttpVersionPreference.entries

/**
 * High-density request authoring bar with a standalone method selector, flexible URL field, and request actions.
 *
 * @param method Active strongly typed HTTP method.
 * @param url Target request URL string.
 * @param httpVersionPreference Requested HTTP wire-version policy.
 * @param onMethodChanged Callback when HTTP method selection changes.
 * @param onUrlChanged Callback when URL text input changes.
 * @param onSendClicked Callback when Send button is clicked.
 * @param onCancelClicked Callback when Cancel button is clicked during in-flight execution.
 * @param isExecuting Reactive execution loading toggle (renders inline spinner on Send button).
 * @param modifier Layout modifier.
 */
@Composable
fun RequestUrlBar(
    method: HttpMethod,
    url: String,
    httpVersionPreference: HttpVersionPreference,
    onMethodChanged: (HttpMethod) -> Unit,
    onHttpVersionPreferenceChanged: (HttpVersionPreference) -> Unit,
    onUrlChanged: (String) -> Unit,
    onSendClicked: () -> Unit,
    onCancelClicked: (() -> Unit)? = null,
    isExecuting: Boolean = false,
    modifier: Modifier = Modifier
) {
    val typography = KNetTheme.typography
    val spacing = KNetTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KNetDropdown(
            selectedItem = method,
            items = httpMethods,
            onItemSelected = onMethodChanged,
            defaultItem = null,
            size = KNetDropdownSize.Large,
            centeredAnchorContent = true,
            itemText = HttpMethod::token,
            itemColor = { HttpMethodColors.getMethodTextColor(it.token) }
        )

        KNetTextField(
            value = url,
            onValueChange = onUrlChanged,
            modifier = Modifier
                .weight(1f)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown &&
                        (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                    ) {
                        if (isExecuting) {
                            onCancelClicked?.invoke()
                        } else {
                            onSendClicked()
                        }
                        true
                    } else {
                        false
                    }
                },
            config = InputFieldConfig(
                placeholder = "Enter request URL",
                showHoverPopupOnOverflow = true,
                fieldHeight = 40.dp
            )
        )

        KNetDropdown(
            selectedItem = httpVersionPreference,
            items = httpVersionPreferences,
            onItemSelected = onHttpVersionPreferenceChanged,
            defaultItem = HttpVersionPreference.AUTO,
            placeholder = HttpVersionPreference.AUTO.displayName,
            size = KNetDropdownSize.Large,
            centeredAnchorContent = true,
            itemText = HttpVersionPreference::displayName,
        )

        // Modern Action Send / Cancel Button using KNetButton with native loading state support
        KNetButton(
            onClick = {
                if (isExecuting) {
                    onCancelClicked?.invoke()
                } else {
                    onSendClicked()
                }
            },
            variant = if (isExecuting) ButtonVariant.Secondary else ButtonVariant.Primary,
            loading = isExecuting,
            clickableWhileLoading = onCancelClicked != null,
            modifier = Modifier.height(40.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Text(
                    text = if (isExecuting) "Cancel" else "Send",
                    style = typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    softWrap = false
                )
                if (!isExecuting) {
                    Icon(
                        imageVector = KNetIcons.Send,
                        contentDescription = "Send Request",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
