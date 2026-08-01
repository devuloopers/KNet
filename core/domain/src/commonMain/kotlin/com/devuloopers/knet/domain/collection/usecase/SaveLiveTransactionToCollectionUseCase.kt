package com.devuloopers.knet.domain.collection.usecase

import com.devuloopers.knet.domain.collection.model.ApiRequestBody
import com.devuloopers.knet.domain.collection.model.HttpMethod
import com.devuloopers.knet.domain.collection.model.RequestHeader
import com.devuloopers.knet.domain.collection.model.SavedApiRequest
import com.devuloopers.knet.domain.collection.model.defaultHeaders
import com.devuloopers.knet.domain.collection.repository.CollectionsRepository
import com.devuloopers.knet.domain.network.model.HttpTransaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Use case to save an intercepted [HttpTransaction] from Live Traffic into an API collection folder.
 *
 * @param repository Repository instance for persisting collections.
 */
class SaveLiveTransactionToCollectionUseCase(
    private val repository: CollectionsRepository
) {

    @OptIn(ExperimentalUuidApi::class)
    suspend operator fun invoke(
        collectionId: String,
        folderId: String,
        transaction: HttpTransaction,
        customName: String? = null
    ) {
        val methodEnum = try {
            HttpMethod.valueOf(transaction.request.method.uppercase())
        } catch (_: Exception) {
            HttpMethod.CUSTOM
        }

        val requestName = customName ?: transaction.request.url.substringAfter("://").substringAfter("/")

        val savedRequest = SavedApiRequest(
            id = Uuid.random().toString(),
            name = "/$requestName",
            method = methodEnum,
            customMethod = if (methodEnum == HttpMethod.CUSTOM) transaction.request.method else null,
            url = transaction.request.url,
            headers = defaultHeaders() + transaction.request.headers.map { (name, value) ->
                RequestHeader(key = name, value = value, isEnabled = true, isAuto = false)
            },
            body = ApiRequestBody(content = transaction.request.body?.decodeToString() ?: "")
        )

        repository.saveRequest(collectionId, folderId, savedRequest)
    }
}
