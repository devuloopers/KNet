package com.devuloopers.knet.domain.collection.import

import com.devuloopers.knet.domain.collection.model.ApiCollection
import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.CollectionFolder
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.traffic.model.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Parser utility converting Postman v2.1 JSON collection specifications into KNet [ApiCollection] domain models.
 */
@OptIn(ExperimentalUuidApi::class)
class PostmanCollectionImporter {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses a Postman v2.1 JSON string.
     */
    fun parseJson(jsonContent: String): ApiCollection {
        val root = json.parseToJsonElement(jsonContent).jsonObject

        val infoObj = root["info"]?.jsonObject
        val collectionName = infoObj?.get("name")?.jsonPrimitive?.content ?: "Imported Postman Collection"
        val collectionId = "c-postman-${Uuid.random()}"

        val itemArray = root["item"]?.jsonArray ?: emptyList()

        val folders = mutableListOf<CollectionFolder>()
        val rootRequests = mutableListOf<SavedApiRequest>()

        itemArray.forEachIndexed { index, itemElem ->
            val itemObj = itemElem.jsonObject
            val name = itemObj["name"]?.jsonPrimitive?.content ?: "Request $index"

            if (itemObj.containsKey("item")) {
                // Folder containing child requests
                val folderRequests = mutableListOf<SavedApiRequest>()
                val childItems = itemObj["item"]?.jsonArray ?: emptyList()

                childItems.forEachIndexed { cIndex, childElem ->
                    val req = parseRequest(childElem.jsonObject, cIndex)
                    if (req != null) folderRequests.add(req)
                }

                folders.add(
                    CollectionFolder(
                        id = "f-postman-$index",
                        name = name,
                        isExpanded = true,
                        requests = folderRequests
                    )
                )
            } else {
                // Root request
                val req = parseRequest(itemObj, index)
                if (req != null) rootRequests.add(req)
            }
        }

        if (rootRequests.isNotEmpty()) {
            folders.add(0, CollectionFolder(id = "f-postman-root", name = "General Requests", isExpanded = true, requests = rootRequests))
        }

        return ApiCollection(
            id = collectionId,
            name = collectionName,
            folders = folders
        )
    }

    private fun parseRequest(itemObj: kotlinx.serialization.json.JsonObject, index: Int): SavedApiRequest? {
        val requestObj = itemObj["request"]?.jsonObject ?: return null
        val name = itemObj["name"]?.jsonPrimitive?.content ?: "Request $index"

        val method = HttpMethod.fromToken(requestObj["method"]?.jsonPrimitive?.content ?: "GET")

        val urlStr = when (val urlElem = requestObj["url"]) {
            is kotlinx.serialization.json.JsonPrimitive -> urlElem.content
            is kotlinx.serialization.json.JsonObject -> urlElem["raw"]?.jsonPrimitive?.content ?: "https://httpbin.org/get"
            else -> "https://httpbin.org/get"
        }

        val bodyStr = requestObj["body"]?.jsonObject?.get("raw")?.jsonPrimitive?.content ?: ""

        return SavedApiRequest(
            id = "r-postman-${Uuid.random()}",
            name = name,
            method = method,
            url = urlStr,
            body = ApiRequestBody(content = bodyStr)
        )
    }
}
