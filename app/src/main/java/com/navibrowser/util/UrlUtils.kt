package com.navibrowser.util

import android.webkit.URLUtil
import java.net.URI

object UrlUtils {

    /**
     * Determines whether user input should be treated as a URL/IP (direct access)
     * or a search query.
     *
     * Direct access patterns:
     *   - Already has http:// or https:// scheme
     *   - IPv4: x.x.x.x or x.x.x.x:port
     *   - Domain-like: something.tld or something.tld/path
     */
    fun processInput(input: String, searchEngine: com.navibrowser.data.model.SearchEngine): String {
        val trimmed = input.trim()

        // Already has a scheme — use as-is
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        // IPv4 address: x.x.x.x or x.x.x.x:port (with optional path)
        if (isIpAddress(trimmed)) {
            return "http://$trimmed"
        }

        // Domain-like pattern: letters/numbers, at least one dot, valid TLD chars, optional path
        // e.g. google.com, sub.example.co.uk, localhost:8080
        if (isDomainLike(trimmed)) {
            return "https://$trimmed"
        }

        // Everything else → search
        return searchEngine.buildSearchUrl(trimmed)
    }

    /** Matches IPv4 like 192.168.1.1 or 10.0.0.1:8080 or 127.0.0.1/path */
    private fun isIpAddress(input: String): Boolean {
        val ipRegex = Regex(
            """^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(:\d+)?(/.*)?$"""
        )
        val match = ipRegex.matchEntire(input.substringBefore("/").let { input }) ?: return false
        // Quick validity: each octet 0-255
        return try {
            val path = input.substringAfter("/", "")
            val hostPort = if (path.isEmpty()) input else input.substringBefore("/")
            val host = hostPort.substringBefore(":")
            val octets = host.split(".")
            octets.size == 4 && octets.all { it.toInt() in 0..255 }
        } catch (e: Exception) { false }
    }

    /** Matches domain-like strings: e.g. example.com, sub.domain.org, localhost:3000 */
    private fun isDomainLike(input: String): Boolean {
        // Strip optional port and path for pattern matching
        val host = input.substringBefore("/").substringBefore("?")
        // Must have at least one dot, no spaces, valid chars
        return host.matches(Regex("""^[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?)+(:\d+)?$"""))
    }

    fun getDomain(url: String): String? = try {
        URI(url).host?.removePrefix("www.")
    } catch (e: Exception) { null }

    fun getFaviconUrl(url: String): String {
        val domain = getDomain(url) ?: return ""
        return "https://www.google.com/s2/favicons?domain=$domain&sz=64"
    }

    fun isSearchUrl(url: String): Boolean =
        listOf(
            "google.com/search", "bing.com/search", "baidu.com/s",
            "sogou.com/web", "yandex.com/search", "duckduckgo.com",
            "so.com/s", "shenma.com"
        ).any { url.contains(it) }

    /**
     * Returns the display text for the address bar:
     * - If it's a search page → return the query string
     * - If it's a direct URL/IP → return the URL
     * - If it's the home page → return empty
     */
    fun getAddressBarText(url: String): String {
        if (url == "navi://home" || url.isEmpty()) return ""
        val query = extractSearchQuery(url)
        return query ?: url
    }

    fun extractSearchQuery(url: String): String? {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.getQueryParameter("q")
                ?: uri.getQueryParameter("wd")
                ?: uri.getQueryParameter("query")
                ?: uri.getQueryParameter("text")
                ?: uri.getQueryParameter("keyword")
        } catch (e: Exception) { null }
    }
}
