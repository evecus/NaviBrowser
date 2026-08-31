package com.navibrowser.data.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val faviconUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_passwords")
data class SavedPassword(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val username: String,
    val encryptedPassword: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val filePath: String,
    val mimeType: String? = null,
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    PENDING, DOWNLOADING, PAUSED, COMPLETED, FAILED
}

@Entity(tableName = "shortcuts")
data class HomeShortcut(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val faviconUrl: String? = null,
    val position: Int = 0
)

data class TabInfo(
    val id: String,
    val title: String = "新标签页",
    val url: String = "",
    val isIncognito: Boolean = false,
    val favicon: android.graphics.Bitmap? = null
)

@Entity(tableName = "user_scripts")
data class UserScript(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val namespace: String = "",
    val description: String = "",
    val version: String = "1.0",
    @ColumnInfo(name = "match_patterns") val matchPatterns: String = "*://*/*",
    @ColumnInfo(name = "exclude_patterns") val excludePatterns: String = "",
    val code: String,
    val enabled: Boolean = true,
    /** 运行时机：document-start / document-end / document-idle（默认 document-idle）。 */
    @ColumnInfo(name = "run_at") val runAt: String = "document-idle",
    /** @grant 声明列表，逗号分隔（如 "GM_setValue,GM_getValue,GM_addStyle"）。 */
    @ColumnInfo(name = "grants") val grants: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis()
) {
    /** 是否声明了某个 GM 权限。 */
    fun hasGrant(grant: String): Boolean =
        grants.split(",").map { it.trim() }.any { it == grant || it == "none" }
}


data class SearchEngine(
    val name: String,
    val searchUrl: String,
    val iconRes: Int = 0
) {
    fun buildSearchUrl(query: String): String =
        searchUrl + java.net.URLEncoder.encode(query, "UTF-8")
}

object SearchEngines {
    val list = listOf(
        SearchEngine("必应", "https://www.bing.com/search?q="),
        SearchEngine("谷歌", "https://www.google.com/search?q="),
        SearchEngine("百度", "https://www.baidu.com/s?wd="),
        SearchEngine("搜狗", "https://www.sogou.com/web?query="),
        SearchEngine("Yandex", "https://yandex.com/search/?text="),
        SearchEngine("DuckDuckGo", "https://duckduckgo.com/?q=")
    )
}
