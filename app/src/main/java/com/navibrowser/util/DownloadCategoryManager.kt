package com.navibrowser.util

import org.json.JSONArray
import org.json.JSONObject

data class DownloadCategory(
    val name: String,
    val extensions: List<String>
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("extensions", JSONArray(extensions))
    }

    companion object {
        fun fromJson(obj: JSONObject): DownloadCategory {
            val arr = obj.getJSONArray("extensions")
            val exts = (0 until arr.length()).map { arr.getString(it) }
            return DownloadCategory(obj.getString("name"), exts)
        }
    }
}

object DownloadCategoryManager {

    val DEFAULT_CATEGORIES = listOf(
        DownloadCategory("全部", emptyList()),
        DownloadCategory("文档", listOf("doc", "docx", "pdf", "txt", "xls", "xlsx", "ppt", "pptx", "odt", "rtf", "csv")),
        DownloadCategory("压缩包", listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "zst")),
        DownloadCategory("安装包", listOf("apk", "exe", "msi", "deb", "rpm", "dmg", "iso")),
        DownloadCategory("图片", listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg", "ico")),
        DownloadCategory("视频", listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "m4v")),
        DownloadCategory("音频", listOf("mp3", "wav", "aac", "flac", "ogg", "wma", "m4a"))
    )

    /** "全部" is always at index 0 */
    fun loadCategories(prefs: PrefsManager): List<DownloadCategory> {
        val raw = prefs.downloadCategoryList
        if (raw.isBlank()) return DEFAULT_CATEGORIES.toList()
        return try {
            val arr = JSONArray(raw)
            val list = (0 until arr.length()).map { DownloadCategory.fromJson(arr.getJSONObject(it)) }
            // Ensure "全部" exists at index 0
            if (list.isEmpty() || list[0].name != "全部") {
                listOf(DEFAULT_CATEGORIES[0]) + list.filter { it.name != "全部" }
            } else list
        } catch (_: Exception) { DEFAULT_CATEGORIES.toList() }
    }

    fun saveCategories(prefs: PrefsManager, categories: List<DownloadCategory>) {
        prefs.downloadCategoryList = JSONArray(categories.map { it.toJson() }).toString()
    }

    /** Get the extension from a file name (without dot, lowercase) */
    fun getExtension(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot >= 0 && dot < fileName.length - 1) fileName.substring(dot + 1).lowercase() else ""
    }

    /** Determine which category a file belongs to. Returns first matching category name, or "全部". */
    fun categorize(fileName: String, categories: List<DownloadCategory>): String {
        val ext = getExtension(fileName)
        if (ext.isEmpty()) return categories.firstOrNull()?.name ?: "全部"
        for (cat in categories) {
            if (cat.name == "全部") continue
            if (cat.extensions.any { it.equals(ext, ignoreCase = true) }) return cat.name
        }
        return categories.firstOrNull()?.name ?: "全部"
    }

    fun resetToDefaults(prefs: PrefsManager) {
        saveCategories(prefs, DEFAULT_CATEGORIES)
    }
}
