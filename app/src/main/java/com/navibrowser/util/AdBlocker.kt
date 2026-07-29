package com.navibrowser.util

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * 增强版广告拦截器。
 * - 内置规则来自 Via 浏览器的 simple.txt（3000+ 条），assets/adblock_rules.txt
 * - 支持两种规则格式：
 *     1. /path/keyword  → 匹配 URL 路径中包含该字符串
 *     2. ||domain.com^  → ABP 标准格式，匹配域名
 * - 用户自定义列表存于 SharedPreferences
 */
object AdBlocker {

    // 两类内置规则分别存储，加快匹配速度
    private val builtinDomains = mutableListOf<String>()   // ||domain^ 类型
    private val builtinKeywords = mutableListOf<String>()  // /path 类型

    private val userHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile var enabled: Boolean = true

    private var initialized = false

    /** 从 assets 加载内置规则，只需调用一次 */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        try {
            context.assets.open("adblock_rules.txt").bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    when {
                        line.isEmpty() || line.startsWith("!") || line.startsWith("#") -> return@forEach
                        line.startsWith("||") -> {
                            // ||domain.com^ or ||domain.com/path^
                            val domain = line.removePrefix("||").substringBefore("^").substringBefore("/")
                            if (domain.isNotEmpty()) builtinDomains.add(domain)
                        }
                        line.startsWith("/") -> {
                            val kw = line.substringBefore("^").substringBefore("$")
                            if (kw.length > 2) builtinKeywords.add(kw)
                        }
                        else -> {
                            // plain domain or keyword
                            if (!line.contains(" ") && line.contains("."))
                                builtinDomains.add(line)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // assets 不存在时回退到小内置列表
            fallbackBuiltin()
        }
        if (builtinDomains.isEmpty()) fallbackBuiltin()
    }

    private fun fallbackBuiltin() {
        builtinDomains.addAll(listOf(
            "doubleclick.net", "googlesyndication.com", "googletagservices.com",
            "googleadservices.com", "google-analytics.com", "googletagmanager.com",
            "admob.com", "adservice.google.com", "adnxs.com", "pubmatic.com",
            "rubiconproject.com", "criteo.com", "taboola.com", "outbrain.com",
            "amazon-adsystem.com", "connect.facebook.net", "scorecardresearch.com"
        ))
    }

    fun loadUserList(context: Context) {
        val raw = context.getSharedPreferences("navi_prefs", Context.MODE_PRIVATE)
            .getString("ad_block_user_hosts", "") ?: ""
        userHosts.clear()
        raw.split('\n', ',').forEach {
            val h = it.trim()
            if (h.isNotEmpty()) userHosts.add(h)
        }
    }

    fun saveUserList(context: Context, hosts: List<String>) {
        context.getSharedPreferences("navi_prefs", Context.MODE_PRIVATE)
            .edit().putString("ad_block_user_hosts", hosts.joinToString("\n")).apply()
        userHosts.clear()
        userHosts.addAll(hosts.map { it.trim() }.filter { it.isNotEmpty() })
    }

    fun getUserHosts(context: Context): List<String> {
        val raw = context.getSharedPreferences("navi_prefs", Context.MODE_PRIVATE)
            .getString("ad_block_user_hosts", "") ?: ""
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun isBlocked(url: String): Boolean {
        if (!enabled) return false
        if (!url.startsWith("http")) return false

        // User list
        for (h in userHosts) { if (url.contains(h)) return true }

        // Domain rules: extract host from URL
        val host = try {
            android.net.Uri.parse(url).host ?: ""
        } catch (e: Exception) { "" }

        for (d in builtinDomains) {
            if (host == d || host.endsWith(".$d")) return true
        }

        // Keyword/path rules
        for (kw in builtinKeywords) {
            if (url.contains(kw)) return true
        }

        return false
    }

    fun ruleCount() = builtinDomains.size + builtinKeywords.size
}
