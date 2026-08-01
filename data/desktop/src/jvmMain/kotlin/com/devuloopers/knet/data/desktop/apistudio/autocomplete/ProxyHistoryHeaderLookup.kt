package com.devuloopers.knet.data.desktop.apistudio.autocomplete

import com.devuloopers.knet.storage.database.KNetDatabase
import kotlinx.coroutines.flow.first

/**
 * Autocompletion lookup service scanning historical proxy transactions for HTTP header suggestions.
 */
public class ProxyHistoryHeaderLookup(
    private val database: KNetDatabase
) {
    /**
     * Obtains list of historical header values matching [headerName].
     */
    public suspend fun getValuesForHeader(headerName: String): List<String> {
        return database.httpTransactionDao().getAllTransactionsFlow().first()
            .flatMap { transaction ->
                val reqHeaders = transaction.requestHeadersJson
                val resHeaders = transaction.responseHeadersJson ?: ""
                val allHeaders = "$reqHeaders;\n$resHeaders"
                allHeaders.split(";\n")
                    .filter { it.contains(":") }
                    .map { line ->
                        val parts = line.split(":", limit = 2)
                        parts[0].trim() to (parts.getOrNull(1)?.trim() ?: "")
                    }
            }
            .filter { (name, _) -> name.equals(headerName, ignoreCase = true) }
            .map { (_, value) -> value }
            .distinct()
    }
}
