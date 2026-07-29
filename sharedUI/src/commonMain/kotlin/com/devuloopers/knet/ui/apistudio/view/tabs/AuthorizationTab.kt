package com.devuloopers.knet.ui.apistudio.view.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devuloopers.knet.theme.KNetColors
import com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState
import com.devuloopers.knet.ui.apistudio.view.apiKeyLocation
import com.devuloopers.knet.ui.apistudio.view.apiKeyName
import com.devuloopers.knet.ui.apistudio.view.apiKeyValue
import com.devuloopers.knet.ui.apistudio.view.authPassword
import com.devuloopers.knet.ui.apistudio.view.authToken
import com.devuloopers.knet.ui.apistudio.view.authType
import com.devuloopers.knet.ui.apistudio.view.authUsername
import com.devuloopers.knet.ui.apistudio.view.awsAccessKey
import com.devuloopers.knet.ui.apistudio.view.awsRegion
import com.devuloopers.knet.ui.apistudio.view.awsSecretKey
import com.devuloopers.knet.ui.apistudio.view.awsService
import com.devuloopers.knet.ui.apistudio.view.oauthHeaderPrefix
import com.devuloopers.knet.widgets.KNetInputField

/**
 * Authorization tab content for the Request Builder panel.
 *
 * Renders an auth-type pill selector and the corresponding sub-form for the
 * selected scheme: Inherit Auth, No Auth, Bearer Token, API Key,
 * Basic Auth, OAuth 2.0, or AWS Signature V4.
 *
 * @param uiState The current [ApiStudioUiState] containing auth-derived fields.
 * @param onAuthTypeChange Callback when the auth type selection changes.
 * @param onAuthTokenChange Callback when the Bearer / OAuth2 token value changes.
 * @param onAuthUsernameChange Callback when the Basic Auth username changes.
 * @param onAuthPasswordChange Callback when the Basic Auth password changes.
 * @param onApiKeyNameChange Callback when the API Key name changes.
 * @param onApiKeyValueChange Callback when the API Key value changes.
 * @param onApiKeyLocationChange Callback when the API Key location (Header / Query) changes.
 * @param onOauthHeaderPrefixChange Callback when the OAuth2 header prefix changes.
 * @param onAwsAccessKeyChange Callback when the AWS access key ID changes.
 * @param onAwsSecretKeyChange Callback when the AWS secret access key changes.
 * @param onAwsRegionChange Callback when the AWS region changes.
 * @param onAwsServiceChange Callback when the AWS service name changes.
 */
@Composable
internal fun AuthorizationTab(
    uiState: ApiStudioUiState,
    onAuthTypeChange: (String) -> Unit,
    onAuthTokenChange: (String) -> Unit,
    onAuthUsernameChange: (String) -> Unit,
    onAuthPasswordChange: (String) -> Unit,
    onApiKeyNameChange: (String) -> Unit,
    onApiKeyValueChange: (String) -> Unit,
    onApiKeyLocationChange: (String) -> Unit,
    onOauthHeaderPrefixChange: (String) -> Unit,
    onAwsAccessKeyChange: (String) -> Unit,
    onAwsSecretKeyChange: (String) -> Unit,
    onAwsRegionChange: (String) -> Unit,
    onAwsServiceChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
        Text("Authentication Configuration", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

        // Auth type pill selector
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Type: ", color = KNetColors.TextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(6.dp))
            listOf("Inherit Auth", "No Auth", "Bearer Token", "API Key", "Basic Auth", "OAuth 2.0", "AWS Signature").forEach { type ->
                val isSelected = type == uiState.authType
                Box(
                    modifier = Modifier
                        .background(if (isSelected) KNetColors.ActiveBlue else KNetColors.FieldDark, RoundedCornerShape(4.dp))
                        .border(1.dp, if (isSelected) KNetColors.ActiveBlue else KNetColors.BorderDark, RoundedCornerShape(4.dp))
                        .clickable { onAuthTypeChange(type) }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(type, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
        }

        // Auth sub-form switcher
        when (uiState.authType) {
            "Inherit Auth" -> {
                Box(modifier = Modifier.fillMaxWidth().background(KNetColors.FieldDark, RoundedCornerShape(6.dp)).border(1.dp, KNetColors.BorderDark, RoundedCornerShape(6.dp)).padding(12.dp)) {
                    Text("This request inherits authentication from its parent Collection or Folder.", color = KNetColors.TextSecondary, fontSize = 11.sp)
                }
            }
            "Bearer Token" -> BearerTokenForm(uiState.authToken, onAuthTokenChange)
            "API Key" -> ApiKeyForm(uiState.apiKeyName, uiState.apiKeyValue, uiState.apiKeyLocation, onApiKeyNameChange, onApiKeyValueChange, onApiKeyLocationChange)
            "Basic Auth" -> BasicAuthForm(uiState.authUsername, uiState.authPassword, onAuthUsernameChange, onAuthPasswordChange)
            "OAuth 2.0" -> OAuth2Form(uiState.oauthHeaderPrefix, uiState.authToken, onOauthHeaderPrefixChange, onAuthTokenChange)
            "AWS Signature" -> AwsSignatureForm(uiState.awsAccessKey, uiState.awsSecretKey, uiState.awsRegion, uiState.awsService, onAwsAccessKeyChange, onAwsSecretKeyChange, onAwsRegionChange, onAwsServiceChange)
            else -> Text("This request does not use authentication.", color = KNetColors.TextSecondary.copy(alpha = 0.5f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun BearerTokenForm(token: String, onTokenChange: (String) -> Unit) {
    Column {
        Text("Bearer Token:", color = KNetColors.TextSecondary, fontSize = 10.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.background(KNetColors.ActiveBlue.copy(alpha = 0.2f), RoundedCornerShape(4.dp)).border(1.dp, KNetColors.ActiveBlue, RoundedCornerShape(4.dp)).padding(horizontal = 10.dp, vertical = 7.dp)) {
                Text("Bearer", color = KNetColors.ActiveBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(6.dp))
            KNetInputField(value = token, onValueChange = onTokenChange, placeholder = "Paste your token here...", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("Note: The 'Bearer ' prefix is automatically added to the Authorization header.", color = KNetColors.TextSecondary, fontSize = 9.sp)
    }
}

@Composable
private fun ApiKeyForm(name: String, value: String, location: String, onNameChange: (String) -> Unit, onValueChange: (String) -> Unit, onLocationChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Key Name:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                KNetInputField(value = name, onValueChange = onNameChange, placeholder = "e.g. X-API-Key or api_key", modifier = Modifier.fillMaxWidth())
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Key Value:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                KNetInputField(value = value, onValueChange = onValueChange, placeholder = "e.g. secret_live_abcdef123", modifier = Modifier.fillMaxWidth())
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Add To: ", color = KNetColors.TextSecondary, fontSize = 10.sp)
            Spacer(modifier = Modifier.width(6.dp))
            listOf("Header", "Query Params").forEach { loc ->
                val isLocSelected = loc.equals(location, ignoreCase = true)
                Box(modifier = Modifier.background(if (isLocSelected) KNetColors.ActiveBlue else KNetColors.FieldDark, RoundedCornerShape(4.dp)).border(1.dp, if (isLocSelected) KNetColors.ActiveBlue else KNetColors.BorderDark, RoundedCornerShape(4.dp)).clickable { onLocationChange(loc) }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(loc, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun BasicAuthForm(username: String, password: String, onUsernameChange: (String) -> Unit, onPasswordChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text("Username:", color = KNetColors.TextSecondary, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(4.dp))
            KNetInputField(value = username, onValueChange = onUsernameChange, placeholder = "Enter username...", modifier = Modifier.fillMaxWidth())
        }
        Column {
            Text("Password:", color = KNetColors.TextSecondary, fontSize = 10.sp)
            Spacer(modifier = Modifier.height(4.dp))
            KNetInputField(value = password, onValueChange = onPasswordChange, placeholder = "Enter password...", isPassword = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun OAuth2Form(headerPrefix: String, token: String, onPrefixChange: (String) -> Unit, onTokenChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Header Prefix:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                KNetInputField(value = headerPrefix, onValueChange = onPrefixChange, placeholder = "Bearer", modifier = Modifier.fillMaxWidth())
            }
            Column(modifier = Modifier.weight(2f)) {
                Text("Access Token:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                KNetInputField(value = token, onValueChange = onTokenChange, placeholder = "Paste OAuth 2.0 Access Token...", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AwsSignatureForm(accessKey: String, secretKey: String, region: String, service: String, onAccessKeyChange: (String) -> Unit, onSecretKeyChange: (String) -> Unit, onRegionChange: (String) -> Unit, onServiceChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Access Key ID:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                KNetInputField(value = accessKey, onValueChange = onAccessKeyChange, placeholder = "AKIAIOSFODNN7EXAMPLE", modifier = Modifier.fillMaxWidth())
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Secret Access Key:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                KNetInputField(value = secretKey, onValueChange = onSecretKeyChange, placeholder = "wJalrXUtnFEMI...", isPassword = true, modifier = Modifier.fillMaxWidth())
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("AWS Region:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                KNetInputField(value = region, onValueChange = onRegionChange, placeholder = "us-east-1", modifier = Modifier.fillMaxWidth())
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Service Name:", color = KNetColors.TextSecondary, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                KNetInputField(value = service, onValueChange = onServiceChange, placeholder = "s3", modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
