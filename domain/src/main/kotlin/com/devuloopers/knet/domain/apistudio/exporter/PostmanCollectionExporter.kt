package com.devuloopers.knet.domain.apistudio.exporter

import com.devuloopers.knet.domain.apistudio.model.ApiCollection
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Serializer utility converting a KNet [ApiCollection] domain model into standard Postman v2.1 JSON string format.
 */
class PostmanCollectionExporter {

    /**
     * Serializes an [ApiCollection] into a Postman v2.1 JSON string.
     */
    fun exportToJson(collection: ApiCollection): String {
        val root = buildJsonObject {
            put("info", buildJsonObject {
                put("name", collection.name)
                put("schema", "https://schema.getpostman.com/json/collection/v2.1.0/collection.json")
            })

            put("item", buildJsonArray {
                collection.folders.forEach { folder ->
                    add(buildJsonObject {
                        put("name", folder.name)
                        put("item", buildJsonArray {
                            folder.requests.forEach { req ->
                                add(buildJsonObject {
                                    put("name", req.name)
                                    put("request", buildJsonObject {
                                        put("method", req.methodString)
                                        put("url", buildJsonObject {
                                            put("raw", req.url)
                                        })
                                        if (req.body.isNotBlank()) {
                                            put("body", buildJsonObject {
                                                put("mode", "raw")
                                                put("raw", req.body)
                                            })
                                        }
                                    })
                                })
                            }
                        })
                    })
                }
            })
        }

        return root.toString()
    }
}
