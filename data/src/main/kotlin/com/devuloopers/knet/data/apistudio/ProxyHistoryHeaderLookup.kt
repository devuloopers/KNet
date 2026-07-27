package com.devuloopers.knet.data.apistudio

import com.devuloopers.knet.storage.interception.HttpTransactionDao

/**
 * Historical Proxy Traffic Header Lookup Service.
 * Queries intercepted traffic database to extract headers used by real applications for matching domain hosts.
 *
 * @param transactionDao SQLite DAO for intercepted HTTP transactions.
 */
class ProxyHistoryHeaderLookup(
    private val transactionDao: HttpTransactionDao? = null
) {

    /**
     * Looks up past intercepted request headers for a given URL domain host.
     */
    suspend fun findHistoricalHeadersForDomain(domainHost: String): Map<String, String> {
        if (transactionDao == null || domainHost.isBlank()) return emptyMap()

        return try {
            val transactions = transactionDao.getOldestTransactions(50)
            val match = transactions.firstOrNull { it.url.contains(domainHost, ignoreCase = true) } ?: return emptyMap()

            // Simple regex parser for JSON header list
            val headersMap = mutableMapOf<String, String>()
            val headerPairRegex = Regex("\"(?:name|key)\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"value\"\\s*:\\s*\"([^\"]+)\"")

            headerPairRegex.findAll(match.requestHeadersJson).forEach { matchResult ->
                val name = matchResult.groupValues[1]
                val value = matchResult.groupValues[2]
                if (name.isNotBlank() && value.isNotBlank()) {
                    headersMap[name] = value
                }
            }

            headersMap
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
