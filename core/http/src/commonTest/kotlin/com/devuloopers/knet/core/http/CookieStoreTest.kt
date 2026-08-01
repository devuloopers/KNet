package com.devuloopers.knet.core.http

import com.devuloopers.knet.core.http.cookie.MemoryCookieStore
import io.ktor.http.Cookie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CookieStoreTest {

    @Test
    fun testMemoryCookieStoreStoreAndRetrieve() {
        val store = MemoryCookieStore()
        val cookie1 = Cookie(name = "sessionid", value = "abc123xyz")
        val cookie2 = Cookie(name = "theme", value = "dark")

        store.storeCookie("api.knet.dev", cookie1)
        store.storeCookie("api.knet.dev", cookie2)

        val cookies = store.getCookies("api.knet.dev")
        assertEquals(2, cookies.size)
        assertTrue(cookies.any { it.name == "sessionid" && it.value == "abc123xyz" })
        assertTrue(cookies.any { it.name == "theme" && it.value == "dark" })
    }

    @Test
    fun testMemoryCookieStoreOverwrite() {
        val store = MemoryCookieStore()
        val cookie1 = Cookie(name = "sessionid", value = "v1")
        val cookie2 = Cookie(name = "sessionid", value = "v2")

        store.storeCookie("api.knet.dev", cookie1)
        store.storeCookie("api.knet.dev", cookie2)

        val cookies = store.getCookies("api.knet.dev")
        assertEquals(1, cookies.size)
        assertEquals("v2", cookies[0].value)
    }

    @Test
    fun testMemoryCookieStoreClear() {
        val store = MemoryCookieStore()
        store.storeCookie("api.knet.dev", Cookie(name = "token", value = "123"))
        store.clear()

        assertTrue(store.getCookies("api.knet.dev").isEmpty())
    }
}
