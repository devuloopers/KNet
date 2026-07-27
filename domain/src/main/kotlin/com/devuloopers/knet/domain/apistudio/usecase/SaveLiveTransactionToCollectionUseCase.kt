package com.devuloopers.knet.domain.apistudio.usecase

import com.devuloopers.knet.domain.apistudio.model.HttpMethod
import com.devuloopers.knet.domain.apistudio.model.SavedApiRequest
import com.devuloopers.knet.domain.apistudio.repository.CollectionsRepository
import com.devuloopers.knet.model.HttpTransaction
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
            headers = transaction.request.headers.associate { it.first to it.second },
            body = transaction.request.body?.let { String(it, Charsets.UTF_8) } ?: ""
        )

        repository.saveRequest(collectionId, folderId, savedRequest)
    }
}
