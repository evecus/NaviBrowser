package com.navibrowser.data.repository

import com.navibrowser.data.db.*
import com.navibrowser.data.model.*
import com.navibrowser.security.CryptoManager
import kotlinx.coroutines.flow.Flow

class BrowserRepository(
    private val bookmarkDao: BookmarkDao,
    private val historyDao: HistoryDao,
    private val shortcutDao: ShortcutDao
) {
    val bookmarks: Flow<List<Bookmark>> = bookmarkDao.getAllFlow()
    val history: Flow<List<HistoryEntry>> = historyDao.getAllFlow()
    val shortcuts: Flow<List<HomeShortcut>> = shortcutDao.getAllFlow()

    suspend fun addBookmark(title: String, url: String, faviconUrl: String? = null) =
        bookmarkDao.insert(Bookmark(title = title, url = url, faviconUrl = faviconUrl))

    suspend fun removeBookmark(bookmark: Bookmark) = bookmarkDao.delete(bookmark)

    suspend fun isBookmarked(url: String): Boolean = bookmarkDao.findByUrl(url) != null

    suspend fun addHistory(title: String, url: String) {
        historyDao.insert(HistoryEntry(title = title, url = url))
    }

    suspend fun searchHistory(query: String): List<HistoryEntry> = historyDao.search(query)

    suspend fun clearHistory() = historyDao.clearAll()

    suspend fun addShortcut(title: String, url: String, faviconUrl: String? = null) {
        val maxPos = shortcutDao.getMaxPosition() ?: -1
        shortcutDao.insert(HomeShortcut(title = title, url = url, faviconUrl = faviconUrl, position = maxPos + 1))
    }

    suspend fun removeShortcut(shortcut: HomeShortcut) = shortcutDao.delete(shortcut)
}

class PasswordRepository(private val passwordDao: PasswordDao) {
    val passwords: Flow<List<SavedPassword>> = passwordDao.getAllFlow()

    suspend fun savePassword(domain: String, username: String, password: String) {
        val encrypted = CryptoManager.encrypt(password)
        val existing = passwordDao.findByDomain(domain)
        if (existing != null) {
            passwordDao.update(existing.copy(username = username, encryptedPassword = encrypted, updatedAt = System.currentTimeMillis()))
        } else {
            passwordDao.insert(SavedPassword(domain = domain, username = username, encryptedPassword = encrypted))
        }
    }

    suspend fun updatePassword(id: Long, domain: String, username: String, password: String) {
        val existing = passwordDao.findById(id) ?: return
        val encrypted = CryptoManager.encrypt(password)
        passwordDao.update(
            existing.copy(
                domain = domain,
                username = username,
                encryptedPassword = encrypted,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getPassword(domain: String): Pair<String, String>? {
        val saved = passwordDao.findByDomain(domain) ?: return null
        return Pair(saved.username, CryptoManager.decrypt(saved.encryptedPassword))
    }

    /**
     * 按域名匹配已保存密码，支持子域名：
     * - 精确匹配 host
     * - host 的父域名匹配（如保存 example.com，访问 login.example.com 也能命中）
     * - 保存的域名是 host 的父域名（即 host 以 ".<saved>" 结尾）
     * 多条命中时优先取 updatedAt 最新的一条。
     */
    suspend fun findMatching(host: String): SavedPassword? {
        if (host.isBlank()) return null
        // 1. 精确命中
        passwordDao.findByDomain(host)?.let { return it }
        // 2. 取所有保存密码，做子域名匹配
        val all = passwordDao.getAllOnce()
        if (all.isEmpty()) return null
        val candidates = all.filter { saved ->
            val d = saved.domain
            if (d.isBlank()) false
            else if (d == host) true
            else host.endsWith(".$d", ignoreCase = true) || d.endsWith(".$host", ignoreCase = true)
        }
        return candidates.maxByOrNull { it.updatedAt }
    }

    suspend fun deletePassword(id: Long) = passwordDao.deleteById(id)

    suspend fun deleteAll() = passwordDao.clearAll()
}

class ScriptRepository(private val scriptDao: UserScriptDao) {
    val scripts: Flow<List<UserScript>> = scriptDao.getAllFlow()

    suspend fun add(name: String, code: String, matchPatterns: String, excludePatterns: String = "",
                    namespace: String = "", description: String = "", version: String = "1.0"): Long {
        return scriptDao.insert(UserScript(
            name = name, code = code, matchPatterns = matchPatterns,
            excludePatterns = excludePatterns, namespace = namespace,
            description = description, version = version
        ))
    }

    suspend fun update(script: UserScript) {
        scriptDao.update(script.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = scriptDao.deleteById(id)

    suspend fun getEnabled() = scriptDao.getEnabled()

    suspend fun findById(id: Long) = scriptDao.findById(id)

    suspend fun setEnabled(id: Long, enabled: Boolean) = scriptDao.setEnabled(id, enabled)
}
