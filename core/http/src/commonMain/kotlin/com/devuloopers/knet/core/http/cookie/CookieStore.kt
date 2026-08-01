package com.devuloopers.knet.core.http.cookie

import io.ktor.http.Cookie

/**
 * Abstraction interface for HTTP cookie storage and management.
 */
interface CookieStore {
    /** Stores a cookie for a given host domain. */
    fun storeCookie(host: String, cookie: Cookie)

    /** Retrieves active cookies for a given host domain. */
    fun getCookies(host: String): List<Cookie>

    /** Clears all stored cookies. */
    fun clear()
}

/**
 * Default in-memory implementation of [CookieStore].
 */
class MemoryCookieStore : CookieStore {
    private val cookiesMap = mutableMapOf<String, MutableList<Cookie>>()

    override fun storeCookie(host: String, cookie: Cookie) {
        val list = cookiesMap.getOrPut(host) { mutableListOf() }
        list.removeAll { it.name == cookie.name }
        list.add(cookie)
    }

    override fun getCookies(host: String): List<Cookie> {
        return cookiesMap[host]?.toList() ?: emptyList()
    }

    override fun clear() {
        cookiesMap.clear()
    }
}
