package com.devuloopers.knet.data.desktop.mapper

import com.devuloopers.knet.domain.apistudio.naming.RequestNameOrigin
import com.devuloopers.knet.domain.collection.model.ApiRequestAuth
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestBodyField
import com.devuloopers.knet.domain.collection.model.ApiRequestScripts
import com.devuloopers.knet.domain.collection.model.RequestCookie
import com.devuloopers.knet.domain.collection.model.RequestHeader
import com.devuloopers.knet.domain.collection.model.RequestQueryParameter
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.clientNetwork.model.RawBodyFormat
import com.devuloopers.knet.domain.clientNetwork.model.RequestBodyType
import com.devuloopers.knet.scripting.model.ScriptLanguage
import com.devuloopers.knet.storage.apistudio.entity.SavedRequestEntity
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Lossless mapper between Room saved-request rows and API Studio domain documents.
 *
 * JSON arrays preserve delimiters, blank values, enabled flags, cookies, and structured body fields.
 */
object RequestMapper {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Reconstructs a complete domain request from its persisted Room representation.
     *
     * @param entity Persisted request row.
     * @return Complete authored request document.
     */
    fun mapEntityToDomain(entity: SavedRequestEntity): SavedApiRequest = SavedApiRequest(
        id = entity.id,
        name = entity.name,
        nameOrigin = RequestNameOrigin.fromToken(entity.nameOrigin),
        method = HttpMethod.fromToken(entity.method),
        url = entity.url,
        queryParameters = decodeQueryParameters(entity.queryParamsJson),
        headers = decodeHeaders(entity.headersJson),
        cookies = decodeCookies(entity.cookiesJson),
        body = ApiRequestBody(
            content = entity.bodyContent,
            type = RequestBodyType.fromToken(entity.bodyType),
            rawFormat = RawBodyFormat.fromToken(entity.bodyRawSubType),
            formDataFields = decodeBodyFields(entity.bodyFormDataJson),
            urlEncodedFields = decodeBodyFields(entity.bodyUrlEncodedJson)
        ),
        auth = entity.toDomainAuth(),
        scripts = ApiRequestScripts(
            preRequest = entity.preRequestScript,
            test = entity.testScript,
            language = ScriptLanguage.entries.firstOrNull {
                it.name.equals(entity.scriptLanguage, ignoreCase = true)
            } ?: ScriptLanguage.JAVASCRIPT
        ),
        expectedStatus = entity.expectedStatus
    )

    /**
     * Converts a complete authored request into one Room row without dropping editor configuration.
     *
     * @param request Domain request document.
     * @param collectionId Owning collection identifier or the internal draft collection identifier.
     * @param folderId Owning folder identifier for saved requests.
     * @return Persistable Room entity.
     */
    fun mapDomainToEntity(
        request: SavedApiRequest,
        collectionId: String,
        folderId: String = ""
    ): SavedRequestEntity {
        val authColumns = request.auth.toEntityAuthColumns()
        return SavedRequestEntity(
            id = request.id,
            collectionId = collectionId,
            folderId = folderId,
            name = request.name,
            nameOrigin = request.nameOrigin.name,
            method = request.method.token,
            url = request.url,
            queryParamsJson = encodeQueryParameters(request.queryParameters),
            headersJson = encodeHeaders(request.headers),
            cookiesJson = encodeCookies(request.cookies),
            bodyContent = request.body.content,
            bodyType = request.body.type.name,
            bodyRawSubType = request.body.rawFormat.name,
            bodyFormDataJson = encodeBodyFields(request.body.formDataFields),
            bodyUrlEncodedJson = encodeBodyFields(request.body.urlEncodedFields),
            authType = authColumns.type,
            authToken = authColumns.token,
            authUsername = authColumns.username,
            authPassword = authColumns.password,
            apiKeyName = authColumns.apiKeyName,
            apiKeyValue = authColumns.apiKeyValue,
            apiKeyLocation = authColumns.apiKeyLocation,
            oauthHeaderPrefix = authColumns.oauthHeaderPrefix,
            awsAccessKey = authColumns.awsAccessKey,
            awsSecretKey = authColumns.awsSecretKey,
            awsRegion = authColumns.awsRegion,
            awsService = authColumns.awsService,
            preRequestScript = request.scripts.preRequest,
            testScript = request.scripts.test,
            scriptLanguage = request.scripts.language.name,
            expectedStatus = request.expectedStatus
        )
    }

    private fun encodeQueryParameters(parameters: List<RequestQueryParameter>): String = buildJsonArray {
        parameters.forEach { parameter ->
            add(buildJsonObject {
                put("name", parameter.name)
                put("value", parameter.value)
                put("enabled", parameter.isEnabled)
            })
        }
    }.toString()

    private fun decodeQueryParameters(value: String): List<RequestQueryParameter> =
        decodeArray(value) { element ->
            val item = element.jsonObject
            RequestQueryParameter(
                name = item.string("name"),
                value = item.string("value"),
                isEnabled = item.boolean("enabled", default = true)
            )
        }.orEmpty()

    private fun encodeHeaders(headers: List<RequestHeader>): String = buildJsonArray {
        headers.forEach { header ->
            add(buildJsonObject {
                put("key", header.key)
                put("value", header.value)
                put("enabled", header.isEnabled)
                put("auto", header.isAuto)
            })
        }
    }.toString()

    private fun decodeHeaders(value: String): List<RequestHeader> =
        decodeArray(value) { element ->
            val item = element.jsonObject
            RequestHeader(
                key = item.string("key"),
                value = item.string("value"),
                isEnabled = item.boolean("enabled", default = true),
                isAuto = item.boolean("auto", default = false)
            )
        }.orEmpty()

    private fun encodeCookies(cookies: List<RequestCookie>): String = buildJsonArray {
        cookies.forEach { cookie ->
            add(buildJsonObject {
                put("name", cookie.name)
                put("value", cookie.value)
                put("enabled", cookie.isEnabled)
            })
        }
    }.toString()

    private fun decodeCookies(value: String): List<RequestCookie> =
        decodeArray(value) { element ->
            val item = element.jsonObject
            RequestCookie(
                name = item.string("name"),
                value = item.string("value"),
                isEnabled = item.boolean("enabled", default = true)
            )
        }.orEmpty()

    private fun encodeBodyFields(fields: List<ApiRequestBodyField>): String = buildJsonArray {
        fields.forEach { field ->
            add(buildJsonObject {
                put("id", field.id)
                put("key", field.key)
                put("value", field.value)
                put("enabled", field.isEnabled)
            })
        }
    }.toString()

    private fun decodeBodyFields(value: String): List<ApiRequestBodyField> =
        decodeArray(value) { element ->
            val item = element.jsonObject
            ApiRequestBodyField(
                id = item.string("id"),
                key = item.string("key"),
                value = item.string("value"),
                isEnabled = item.boolean("enabled", default = true)
            )
        }.orEmpty()

    private fun <T> decodeArray(value: String, transform: (JsonElement) -> T): List<T>? = runCatching {
        (json.parseToJsonElement(value) as? JsonArray)?.map(transform)
    }.getOrNull()

    private fun kotlinx.serialization.json.JsonObject.string(key: String): String =
        get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun kotlinx.serialization.json.JsonObject.boolean(key: String, default: Boolean): Boolean =
        get(key)?.jsonPrimitive?.booleanOrNull ?: default

    private fun SavedRequestEntity.toDomainAuth(): ApiRequestAuth = when (authType.uppercase()) {
        "BEARER", "BEARER_TOKEN", "BEARER TOKEN" -> ApiRequestAuth.Bearer(authToken)
        "BASIC", "BASIC_AUTH", "BASIC AUTH" -> ApiRequestAuth.Basic(authUsername, authPassword)
        "API_KEY", "API KEY" -> ApiRequestAuth.ApiKey(apiKeyName, apiKeyValue, apiKeyLocation)
        "OAUTH2", "OAUTH 2.0" -> ApiRequestAuth.OAuth2(authToken, oauthHeaderPrefix)
        "AWS_SIGNATURE", "AWS SIGNATURE" -> ApiRequestAuth.AwsSignature(
            accessKey = awsAccessKey,
            secretKey = awsSecretKey,
            region = awsRegion,
            service = awsService
        )
        "INHERIT", "INHERIT AUTH" -> ApiRequestAuth.Inherit
        else -> ApiRequestAuth.None
    }

    private fun ApiRequestAuth.toEntityAuthColumns(): AuthColumns = when (this) {
        ApiRequestAuth.None -> AuthColumns(type = "NONE")
        ApiRequestAuth.Inherit -> AuthColumns(type = "INHERIT")
        is ApiRequestAuth.Bearer -> AuthColumns(type = "BEARER", token = token)
        is ApiRequestAuth.Basic -> AuthColumns(type = "BASIC", username = username, password = password)
        is ApiRequestAuth.ApiKey -> AuthColumns(
            type = "API_KEY",
            apiKeyName = name,
            apiKeyValue = value,
            apiKeyLocation = location
        )
        is ApiRequestAuth.OAuth2 -> AuthColumns(
            type = "OAUTH2",
            token = token,
            oauthHeaderPrefix = headerPrefix
        )
        is ApiRequestAuth.AwsSignature -> AuthColumns(
            type = "AWS_SIGNATURE",
            awsAccessKey = accessKey,
            awsSecretKey = secretKey,
            awsRegion = region,
            awsService = service
        )
    }

    private data class AuthColumns(
        val type: String,
        val token: String = "",
        val username: String = "",
        val password: String = "",
        val apiKeyName: String = "X-API-Key",
        val apiKeyValue: String = "",
        val apiKeyLocation: String = "Header",
        val oauthHeaderPrefix: String = "Bearer",
        val awsAccessKey: String = "",
        val awsSecretKey: String = "",
        val awsRegion: String = "us-east-1",
        val awsService: String = "s3"
    )
}
