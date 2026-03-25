package com.aiagents.app.data.terminal

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory cookie jar for OkHttp that persists cookies across requests
 * within the same session. Used by DuckDuckGoSearchToolHandler to maintain
 * session cookies between the initial GET and the search POST.
 */
class InMemoryCookieJar : CookieJar {
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val key = url.host
        cookieStore.getOrPut(key) { mutableListOf() }.apply {
            // Remove existing cookies with same name, then add new ones
            cookies.forEach { newCookie ->
                removeAll { it.name == newCookie.name }
                add(newCookie)
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val key = url.host
        val cookies = cookieStore[key] ?: return emptyList()
        // Filter out expired cookies
        val now = System.currentTimeMillis() / 1000
        val valid = cookies.filter { it.expiresAt / 1000 > now }
        if (valid.size != cookies.size) {
            cookies.clear()
            cookies.addAll(valid)
        }
        return valid
    }
}
