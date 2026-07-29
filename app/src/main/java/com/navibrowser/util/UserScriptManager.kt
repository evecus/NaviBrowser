package com.navibrowser.util

import com.navibrowser.data.model.UserScript

object UserScriptManager {

    fun matchingScripts(url: String, scripts: List<UserScript>): List<UserScript> {
        if (url.isBlank() || !url.startsWith("http")) return emptyList()
        return scripts.filter { script ->
            if (!script.enabled) return@filter false
            val matches = script.matchPatterns.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val excludes = script.excludePatterns.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val matched = matches.isEmpty() || matches.any { matchUrl(url, it) }
            val excluded = excludes.any { matchUrl(url, it) }
            matched && !excluded
        }
    }

    fun buildInjectionJs(scripts: List<UserScript>): String {
        if (scripts.isEmpty()) return ""
        val parts = mutableListOf<String>()
        parts.add("(function(){")
        scripts.forEachIndexed { i, script ->
            val safeName = script.name.replace("'", "\\'")
            parts.add("try{")
            parts.add(script.code)
            parts.add("}catch(e){console.warn('[UserScript: $safeName]',e.message)}")
        }
        parts.add("})();")
        return parts.joinToString("\n")
    }

    private fun matchUrl(url: String, pattern: String): Boolean {
        val p = pattern.trim()
        if (p == "<all_urls>") return true
        if (p == "*://*/*") return url.startsWith("http")

        if (!p.contains("://")) {
            return url.contains(p)
        }

        try {
            val regex = patternToRegex(p)
            return regex.containsMatchIn(url)
        } catch (_: Exception) {
            return false
        }
    }

    private fun patternToRegex(pattern: String): Regex {
        val escaped = Regex.escape(pattern)
            .replace("\\*", ".*")
            .replace("\\?", ".")
        val anchored = "^${escaped}$"
        return Regex(anchored, RegexOption.IGNORE_CASE)
    }
}