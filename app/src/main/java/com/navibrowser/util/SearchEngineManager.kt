package com.navibrowser.util

import com.navibrowser.data.model.SearchEngine
import com.navibrowser.data.model.SearchEngines
import org.json.JSONArray
import org.json.JSONObject

data class EditableSearchEngine(
    val name: String,
    val url: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("url", url)
    }

    fun toSearchEngine(): SearchEngine = SearchEngine(name, url)

    companion object {
        fun fromJson(obj: JSONObject): EditableSearchEngine =
            EditableSearchEngine(obj.getString("name"), obj.getString("url"))
        fun fromSearchEngine(se: SearchEngine): EditableSearchEngine =
            EditableSearchEngine(se.name, se.searchUrl)
    }
}

object SearchEngineManager {

    fun loadEngines(prefs: PrefsManager): List<EditableSearchEngine> {
        val raw = prefs.searchEngineCustomList
        if (raw.isBlank()) {
            return SearchEngines.list.map { EditableSearchEngine.fromSearchEngine(it) }
        }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { EditableSearchEngine.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) {
            SearchEngines.list.map { EditableSearchEngine.fromSearchEngine(it) }
        }
    }

    fun saveEngines(prefs: PrefsManager, engines: List<EditableSearchEngine>) {
        prefs.searchEngineCustomList = JSONArray(engines.map { it.toJson() }).toString()
    }

    fun resetToDefaults(prefs: PrefsManager) {
        prefs.searchEngineCustomList = ""
    }
}
