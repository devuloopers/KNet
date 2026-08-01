package com.devuloopers.knet.data.desktop.mapper

import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.ApiRequestScripts
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.collection.model.RequestHeader
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.scripting.model.ScriptLanguage
import com.devuloopers.knet.storage.apistudio.entity.SavedRequestEntity

/**
 * Maps between SQLite Room saved request entities and Domain saved request models.
 */
public object RequestMapper {

    public fun mapEntityToDomain(entity: SavedRequestEntity): SavedApiRequest {
        val methodEnum = try {
            HttpMethod.valueOf(entity.method.uppercase())
        } catch (_: Exception) {
            HttpMethod.GET
        }

        val scriptLangEnum = try {
            ScriptLanguage.valueOf(entity.scriptLanguage.uppercase())
        } catch (_: Exception) {
            ScriptLanguage.KOTLIN
        }

        val headersList = entity.headersJson
            .split(";\n")
            .filter { it.contains(":") }
            .map { headerLine ->
                val parts = headerLine.split(":", limit = 2)
                RequestHeader(key = parts[0].trim(), value = parts.getOrNull(1)?.trim() ?: "")
            }

        return SavedApiRequest(
            id = entity.id,
            name = entity.name,
            method = methodEnum,
            customMethod = entity.customMethod,
            url = entity.url,
            headers = headersList,
            body = ApiRequestBody(content = entity.bodyContent, type = entity.bodyType),
            scripts = ApiRequestScripts(
                preRequest = entity.preRequestScript,
                test = entity.testScript,
                language = scriptLangEnum
            ),
            expectedStatus = entity.expectedStatus
        )
    }

    public fun mapDomainToEntity(
        request: SavedApiRequest,
        collectionId: String,
        folderId: String = ""
    ): SavedRequestEntity {
        val headersJsonStr = request.headers.joinToString(";\n") { "${it.key}:${it.value}" }

        return SavedRequestEntity(
            id = request.id,
            collectionId = collectionId,
            folderId = folderId,
            name = request.name,
            method = request.method.name,
            customMethod = request.customMethod,
            url = request.url,
            headersJson = headersJsonStr,
            bodyContent = request.body.content,
            bodyType = request.body.type,
            preRequestScript = request.scripts.preRequest,
            testScript = request.scripts.test,
            scriptLanguage = request.scripts.language.name,
            expectedStatus = request.expectedStatus
        )
    }
}
