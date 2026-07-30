package com.devuloopers.knet.data.repository

import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import com.devuloopers.knet.domain.apistudio.model.CollectionFolder
import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.model.ApiRequestBody
import com.devuloopers.knet.domain.apistudio.model.ApiRequestScripts
import com.devuloopers.knet.domain.apistudio.model.ApiRequestAuth
import com.devuloopers.knet.domain.apistudio.model.RequestHeader
import com.devuloopers.knet.domain.apistudio.model.defaultHeaders
import com.devuloopers.knet.domain.apistudio.repository.CollectionsRepository
import com.devuloopers.knet.storage.apistudio.CollectionDao
import com.devuloopers.knet.storage.apistudio.CollectionEntity
import com.devuloopers.knet.storage.apistudio.CollectionFolderEntity
import com.devuloopers.knet.storage.apistudio.SavedRequestEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Concrete data implementation of [CollectionsRepository] bridging SQLite Room entities ↔ Domain models.
 *
 * @param collectionDao The Room Data Access Object for collections data.
 */
class CollectionsRepositoryImpl(
    private val collectionDao: CollectionDao
) : CollectionsRepository {

    companion object {
        private const val UNSAVED_COLLECTION_ID = "c-unsaved"
        private const val UNSAVED_FOLDER_ID = "f-unsaved"
    }

    override fun observeCollections(): Flow<List<ApiCollection>> {
        return collectionDao.getAllCollectionsFlow().map { collectionEntities ->
            collectionEntities
                .filter { it.id != UNSAVED_COLLECTION_ID }
                .map { entity ->
                    mapCollectionEntityToDomain(entity)
                }
        }
    }

    override suspend fun getCollectionById(id: String): ApiCollection? {
        val entity = collectionDao.getCollectionById(id) ?: return null
        return mapCollectionEntityToDomain(entity)
    }

    override suspend fun saveCollection(collection: ApiCollection) {
        val entity = CollectionEntity(
            id = collection.id,
            name = collection.name,
            updatedAt = System.currentTimeMillis()
        )
        collectionDao.insertCollection(entity)

        // Save nested folders and requests
        collection.folders.forEachIndexed { index, folder ->
            saveFolder(collection.id, folder.copy())
        }
    }

    override suspend fun deleteCollection(collectionId: String) {
        collectionDao.deleteCollection(collectionId)
    }

    override suspend fun saveFolder(collectionId: String, folder: CollectionFolder) {
        val folderEntity = CollectionFolderEntity(
            id = folder.id,
            collectionId = collectionId,
            name = folder.name,
            isExpanded = folder.isExpanded
        )
        collectionDao.insertFolder(folderEntity)

        folder.requests.forEach { req ->
            saveRequest(collectionId, folder.id, req)
        }
    }

    override suspend fun deleteFolder(folderId: String) {
        collectionDao.deleteFolder(folderId)
    }    override suspend fun saveRequest(collectionId: String, folderId: String, request: SavedApiRequest) {
        val packed = packAuth(request.auth)
        val requestEntity = SavedRequestEntity(
            id = request.id,
            folderId = folderId,
            collectionId = collectionId,
            name = request.name,
            method = request.method.name,
            customMethod = request.customMethod,
            url = request.url,
            headersJson = packHeaders(request.headers),
            bodyContent = request.body.content,
            bodyType = request.body.type,
            authType = packed.authType,
            authToken = packed.authToken,
            authUsername = packed.authUsername,
            authPassword = packed.authPassword,
            apiKeyName = packed.apiKeyName,
            apiKeyValue = packed.apiKeyValue,
            apiKeyLocation = packed.apiKeyLocation,
            oauthHeaderPrefix = packed.oauthHeaderPrefix,
            awsAccessKey = packed.awsAccessKey,
            awsSecretKey = packed.awsSecretKey,
            awsRegion = packed.awsRegion,
            awsService = packed.awsService,
            preRequestScript = request.scripts.preRequest,
            testScript = request.scripts.test,
            scriptLanguage = request.scripts.language.name,
            expectedStatus = request.expectedStatus
        )
        collectionDao.insertRequest(requestEntity)
    }

    override suspend fun deleteRequest(requestId: String) {
        collectionDao.deleteRequest(requestId)
    }

    override fun observeUnsavedRequests(): Flow<List<SavedApiRequest>> {
        return collectionDao.getRequestsForCollectionFlow(UNSAVED_COLLECTION_ID).map { requests ->
            requests.map { reqEntity ->
                val methodEnum = try {
                    HttpMethod.valueOf(reqEntity.method)
                } catch (_: Exception) {
                    HttpMethod.CUSTOM
                }
                val auth = unpackAuth(
                    reqEntity.authType,
                    reqEntity.authToken,
                    reqEntity.authUsername,
                    reqEntity.authPassword,
                    reqEntity.apiKeyName,
                    reqEntity.apiKeyValue,
                    reqEntity.apiKeyLocation,
                    reqEntity.oauthHeaderPrefix,
                    reqEntity.awsAccessKey,
                    reqEntity.awsSecretKey,
                    reqEntity.awsRegion,
                    reqEntity.awsService
                )
                SavedApiRequest(
                    id = reqEntity.id,
                    name = reqEntity.name,
                    method = methodEnum,
                    customMethod = reqEntity.customMethod,
                    url = reqEntity.url,
                    headers = unpackHeaders(reqEntity.headersJson),
                    body = ApiRequestBody(content = reqEntity.bodyContent, type = reqEntity.bodyType),
                    auth = auth,
                    scripts = ApiRequestScripts(
                        preRequest = reqEntity.preRequestScript,
                        test = reqEntity.testScript,
                        language = try {
                            com.devuloopers.knet.scriptengine.api.ScriptLanguage.valueOf(reqEntity.scriptLanguage)
                        } catch (_: Exception) {
                            com.devuloopers.knet.scriptengine.api.ScriptLanguage.JAVASCRIPT
                        }
                    ),
                    expectedStatus = reqEntity.expectedStatus
                )
            }
        }
    }

    override suspend fun saveUnsavedRequest(request: SavedApiRequest) {
        val packed = packAuth(request.auth)
        val requestEntity = SavedRequestEntity(
            id = request.id,
            folderId = UNSAVED_FOLDER_ID,
            collectionId = UNSAVED_COLLECTION_ID,
            name = request.name,
            method = request.method.name,
            customMethod = request.customMethod,
            url = request.url,
            headersJson = packHeaders(request.headers),
            bodyContent = request.body.content,
            bodyType = request.body.type,
            authType = packed.authType,
            authToken = packed.authToken,
            authUsername = packed.authUsername,
            authPassword = packed.authPassword,
            apiKeyName = packed.apiKeyName,
            apiKeyValue = packed.apiKeyValue,
            apiKeyLocation = packed.apiKeyLocation,
            oauthHeaderPrefix = packed.oauthHeaderPrefix,
            awsAccessKey = packed.awsAccessKey,
            awsSecretKey = packed.awsSecretKey,
            awsRegion = packed.awsRegion,
            awsService = packed.awsService,
            preRequestScript = request.scripts.preRequest,
            testScript = request.scripts.test,
            scriptLanguage = request.scripts.language.name,
            expectedStatus = request.expectedStatus
        )
        collectionDao.insertRequest(requestEntity)
    }

    override suspend fun deleteUnsavedRequest(requestId: String) {
        collectionDao.deleteRequest(requestId)
    }

    override suspend fun saveUnsavedToNewCollectionTx(
        collection: ApiCollection,
        folder: CollectionFolder,
        request: SavedApiRequest,
        unsavedRequestIdToDelete: String
    ) {
        val colEntity = CollectionEntity(
            id = collection.id,
            name = collection.name,
            updatedAt = System.currentTimeMillis()
        )
        val folderEntity = CollectionFolderEntity(
            id = folder.id,
            collectionId = collection.id,
            name = folder.name,
            orderIndex = 0
        )
        val packed = packAuth(request.auth)
        val requestEntity = SavedRequestEntity(
            id = request.id,
            collectionId = collection.id,
            folderId = folder.id,
            name = request.name,
            method = request.method.name,
            customMethod = request.customMethod,
            url = request.url,
            headersJson = packHeaders(request.headers),
            bodyContent = request.body.content,
            bodyType = request.body.type,
            authType = packed.authType,
            authToken = packed.authToken,
            authUsername = packed.authUsername,
            authPassword = packed.authPassword,
            apiKeyName = packed.apiKeyName,
            apiKeyValue = packed.apiKeyValue,
            apiKeyLocation = packed.apiKeyLocation,
            oauthHeaderPrefix = packed.oauthHeaderPrefix,
            awsAccessKey = packed.awsAccessKey,
            awsSecretKey = packed.awsSecretKey,
            awsRegion = packed.awsRegion,
            awsService = packed.awsService,
            preRequestScript = request.scripts.preRequest,
            testScript = request.scripts.test,
            scriptLanguage = request.scripts.language.name,
            expectedStatus = request.expectedStatus
        )

        collectionDao.saveUnsavedToNewCollectionTx(colEntity, folderEntity, requestEntity, unsavedRequestIdToDelete)
    }

    private suspend fun mapCollectionEntityToDomain(entity: CollectionEntity): ApiCollection {
        val folderEntities = collectionDao.getFoldersForCollection(entity.id)
        val folders = folderEntities.map { folderEntity ->
            val requestEntities = collectionDao.getRequestsForFolder(folderEntity.id)
            val requests = requestEntities.map { reqEntity ->
                val methodEnum = try {
                    HttpMethod.valueOf(reqEntity.method)
                } catch (_: Exception) {
                    HttpMethod.CUSTOM
                }
                val auth = unpackAuth(
                    reqEntity.authType,
                    reqEntity.authToken,
                    reqEntity.authUsername,
                    reqEntity.authPassword,
                    reqEntity.apiKeyName,
                    reqEntity.apiKeyValue,
                    reqEntity.apiKeyLocation,
                    reqEntity.oauthHeaderPrefix,
                    reqEntity.awsAccessKey,
                    reqEntity.awsSecretKey,
                    reqEntity.awsRegion,
                    reqEntity.awsService
                )
                SavedApiRequest(
                    id = reqEntity.id,
                    name = reqEntity.name,
                    method = methodEnum,
                    customMethod = reqEntity.customMethod,
                    url = reqEntity.url,
                    headers = unpackHeaders(reqEntity.headersJson),
                    body = ApiRequestBody(content = reqEntity.bodyContent, type = reqEntity.bodyType),
                    auth = auth,
                    scripts = ApiRequestScripts(
                        preRequest = reqEntity.preRequestScript,
                        test = reqEntity.testScript,
                        language = try {
                            com.devuloopers.knet.scriptengine.api.ScriptLanguage.valueOf(reqEntity.scriptLanguage)
                        } catch (_: Exception) {
                            com.devuloopers.knet.scriptengine.api.ScriptLanguage.JAVASCRIPT
                        }
                    ),
                    expectedStatus = reqEntity.expectedStatus
                )
            }
            CollectionFolder(
                id = folderEntity.id,
                name = folderEntity.name,
                isExpanded = folderEntity.isExpanded,
                requests = requests
            )
        }
        return ApiCollection(
            id = entity.id,
            name = entity.name,
            folders = folders
        )
    }

    private fun packHeaders(headers: List<RequestHeader>): String {
        if (headers.isEmpty()) return "[]"
        val sb = StringBuilder("[")
        headers.forEachIndexed { index, h ->
            val escapedKey = h.key.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            val escapedVal = h.value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
            sb.append("""{"key":"$escapedKey","value":"$escapedVal","isEnabled":${h.isEnabled},"isAuto":${h.isAuto}}""")
            if (index < headers.size - 1) sb.append(",")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun unpackHeaders(json: String): List<RequestHeader> {
        if (json.isBlank() || json == "[]" || json == "{}") return defaultHeaders()
        try {
            val regex = Regex("""\{"key":"(.*?)","value":"(.*?)","isEnabled":(true|false),"isAuto":(true|false)\}""")
            val matches = regex.findAll(json).toList()
            if (matches.isEmpty()) return defaultHeaders()

            return matches.map { match ->
                val key = match.groupValues[1].replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")
                val value = match.groupValues[2].replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\")
                val isEnabled = match.groupValues[3].toBoolean()
                val isAuto = match.groupValues[4].toBoolean()
                RequestHeader(key = key, value = value, isEnabled = isEnabled, isAuto = isAuto)
            }
        } catch (_: Exception) {
            return defaultHeaders()
        }
    }

    private fun packAuth(auth: ApiRequestAuth): AuthPackedData {
        return when (auth) {
            is ApiRequestAuth.None -> AuthPackedData("No Auth", "", "", "", "", "", "", "", "", "", "", "")
            is ApiRequestAuth.Inherit -> AuthPackedData("Inherit Auth", "", "", "", "", "", "", "", "", "", "", "")
            is ApiRequestAuth.Bearer -> AuthPackedData("Bearer Token", auth.token, "", "", "", "", "", "", "", "", "", "")
            is ApiRequestAuth.Basic -> AuthPackedData("Basic Auth", "", auth.username, auth.password, "", "", "", "", "", "", "", "")
            is ApiRequestAuth.ApiKey -> AuthPackedData("API Key", "", "", "", auth.name, auth.value, auth.location, "", "", "", "", "")
            is ApiRequestAuth.OAuth2 -> AuthPackedData("OAuth 2.0", auth.token, "", "", "", "", "", auth.headerPrefix, "", "", "", "")
            is ApiRequestAuth.AwsSignature -> AuthPackedData("AWS Signature", "", "", "", "", "", "", "", auth.accessKey, auth.secretKey, auth.region, auth.service)
        }
    }

    private fun unpackAuth(
        authType: String,
        authToken: String,
        authUsername: String,
        authPassword: String,
        apiKeyName: String,
        apiKeyValue: String,
        apiKeyLocation: String,
        oauthHeaderPrefix: String,
        awsAccessKey: String,
        awsSecretKey: String,
        awsRegion: String,
        awsService: String
    ): ApiRequestAuth {
        return when (authType) {
            "Bearer Token" -> ApiRequestAuth.Bearer(authToken)
            "Basic Auth" -> ApiRequestAuth.Basic(authUsername, authPassword)
            "API Key" -> ApiRequestAuth.ApiKey(apiKeyName, apiKeyValue, apiKeyLocation)
            "OAuth 2.0" -> ApiRequestAuth.OAuth2(authToken, oauthHeaderPrefix)
            "AWS Signature" -> ApiRequestAuth.AwsSignature(awsAccessKey, awsSecretKey, awsRegion, awsService)
            "Inherit Auth" -> ApiRequestAuth.Inherit
            else -> ApiRequestAuth.None
        }
    }

    private data class AuthPackedData(
        val authType: String,
        val authToken: String,
        val authUsername: String,
        val authPassword: String,
        val apiKeyName: String,
        val apiKeyValue: String,
        val apiKeyLocation: String,
        val oauthHeaderPrefix: String,
        val awsAccessKey: String,
        val awsSecretKey: String,
        val awsRegion: String,
        val awsService: String
    )
}

