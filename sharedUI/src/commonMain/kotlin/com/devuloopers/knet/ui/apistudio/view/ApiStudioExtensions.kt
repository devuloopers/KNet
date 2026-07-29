package com.devuloopers.knet.ui.apistudio.view

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.editor.KNetCodeEditor
import com.devuloopers.knet.editor.model.EditorMode
import com.devuloopers.knet.scriptengine.api.ScriptLanguage
import com.devuloopers.knet.ui.apistudio.model.ApiStudioUiState

/** The body content type string (e.g. "json", "raw-text", "none"). */
internal val SavedApiRequest.bodyType: String
    get() = body.type

/** The raw body payload string content. */
internal val SavedApiRequest.bodyPayload: String
    get() = body.content

/** The pre-request script code string. */
internal val SavedApiRequest.preRequestScript: String
    get() = scripts.preRequest

/** The test script code string. */
internal val SavedApiRequest.testScript: String
    get() = scripts.test

/** The scripting language configured for this request's scripts. */
internal val SavedApiRequest.scriptLanguage: ScriptLanguage
    get() = scripts.language

/** The display label of the currently active auth type. */
internal val ApiStudioUiState.authType: String
    get() = (selectedRequest ?: draftRequest).auth.type

/** The bearer / OAuth2 token of the currently active auth config. */
internal val ApiStudioUiState.authToken: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is ApiRequestAuth.Bearer -> a.token
        is ApiRequestAuth.OAuth2 -> a.token
        else -> ""
    }

/** The Basic Auth username of the currently active auth config. */
internal val ApiStudioUiState.authUsername: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is ApiRequestAuth.Basic -> a.username
        else -> ""
    }

/** The Basic Auth password of the currently active auth config. */
internal val ApiStudioUiState.authPassword: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is ApiRequestAuth.Basic -> a.password
        else -> ""
    }

/** The API Key header/param name of the currently active auth config. */
internal val ApiStudioUiState.apiKeyName: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is ApiRequestAuth.ApiKey -> a.name
        else -> "X-API-Key"
    }

/** The API Key value of the currently active auth config. */
internal val ApiStudioUiState.apiKeyValue: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is ApiRequestAuth.ApiKey -> a.value
        else -> ""
    }

/** The location (Header or Query Params) of the API key. */
internal val ApiStudioUiState.apiKeyLocation: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is ApiRequestAuth.ApiKey -> a.location
        else -> "Header"
    }

/** The OAuth2 header prefix (typically "Bearer"). */
internal val ApiStudioUiState.oauthHeaderPrefix: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is ApiRequestAuth.OAuth2 -> a.headerPrefix
        else -> "Bearer"
    }

/** The AWS Signature access key ID. */
internal val ApiStudioUiState.awsAccessKey: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is ApiRequestAuth.AwsSignature -> a.accessKey
        else -> ""
    }

/** The AWS Signature secret access key. */
internal val ApiStudioUiState.awsSecretKey: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is ApiRequestAuth.AwsSignature -> a.secretKey
        else -> ""
    }

/** The AWS Signature region (e.g. "us-east-1"). */
internal val ApiStudioUiState.awsRegion: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is ApiRequestAuth.AwsSignature -> a.region
        else -> "us-east-1"
    }

/** The AWS Signature service name (e.g. "s3", "execute-api"). */
internal val ApiStudioUiState.awsService: String
    get() = when (val a = (selectedRequest ?: draftRequest).auth) {
        is ApiRequestAuth.AwsSignature -> a.service
        else -> "s3"
    }

/** The scripting language configured in the currently active request's scripts. */
internal val ApiStudioUiState.scriptLanguage: ScriptLanguage
    get() = (selectedRequest ?: draftRequest).scripts.language

/** The pre-request script text from the currently active request. */
internal val ApiStudioUiState.preRequestScript: String
    get() = (selectedRequest ?: draftRequest).scripts.preRequest

/** The test script text from the currently active request. */
internal val ApiStudioUiState.testScript: String
    get() = (selectedRequest ?: draftRequest).scripts.test

/**
 * Thin composable bridge wrapping [KNetCodeEditor] in editable mode.
 *
 * @param code The current code string to display.
 * @param onCodeChange Callback invoked whenever the user edits the code.
 * @param placeholder Placeholder hint shown when the editor is empty.
 * @param textColor The color used to render the code text.
 * @param modifier Layout modifier applied to the editor.
 */
@Composable
internal fun CodeEditorWidget(
    code: String,
    onCodeChange: (String) -> Unit = {},
    placeholder: String = "",
    textColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    KNetCodeEditor(
        code = code,
        mode = EditorMode.Editable(
            onCodeChange = onCodeChange,
            placeholder = placeholder,
            textColor = textColor
        ),
        modifier = modifier
    )
}
