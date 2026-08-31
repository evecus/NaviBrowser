package com.navibrowser.util

import org.json.JSONArray
import org.json.JSONObject

data class SearchEngineItem(
    val name: String,
    val host: String,
    val url: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("host", host)
        put("url", url)
    }

    companion object {
        fun fromJson(obj: JSONObject): SearchEngineItem =
            SearchEngineItem(obj.getString("name"), obj.getString("host"), obj.getString("url"))
    }
}

object SearchEngineSwitcher {

    val DEFAULT_ENGINES = listOf(
        SearchEngineItem("必应", "bing.com", "https://www.bing.com/search?q={q}"),
        SearchEngineItem("谷歌", "google.com", "https://www.google.com/search?q={q}"),
        SearchEngineItem("百度", "baidu.com", "https://www.baidu.com/s?wd={q}"),
        SearchEngineItem("搜狗", "sogou.com", "https://www.sogou.com/web?query={q}"),
        SearchEngineItem("Yandex", "yandex.com", "https://yandex.com/search/?text={q}"),
        SearchEngineItem("DuckDuckGo", "duckduckgo.com", "https://duckduckgo.com/?q={q}")
    )

    fun loadEngines(prefs: PrefsManager): List<SearchEngineItem> {
        val raw = prefs.searchEngineQuickSwitchList
        if (raw.isBlank()) return DEFAULT_ENGINES.toList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { SearchEngineItem.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { DEFAULT_ENGINES.toList() }
    }

    fun saveEngines(prefs: PrefsManager, engines: List<SearchEngineItem>) {
        prefs.searchEngineQuickSwitchList = JSONArray(engines.map { it.toJson() }).toString()
    }

    fun buildInjectionJs(prefs: PrefsManager): String {
        val engines = loadEngines(prefs)
        val enginesJson = JSONArray(engines.map { it.toJson() }).toString()
        return """
(function() {
    if (window.__naviSwitcherInjected) return;
    window.__naviSwitcherInjected = true;

    var engines = $enginesJson;

    function getCurrentHost() {
        return window.location.hostname.replace(/^www\./, '');
    }

    function getQueryParam() {
        var params = new URLSearchParams(window.location.search);
        var candidates = ['q', 'wd', 'query', 'text', 'word', 'search', 'keyword', 'keys'];
        for (var i = 0; i < candidates.length; i++) {
            var v = params.get(candidates[i]);
            if (v && v.trim()) return v.trim();
        }
        return '';
    }

    function isSearchPage() {
        var host = getCurrentHost();
        for (var i = 0; i < engines.length; i++) {
            var eHost = engines[i].host.replace(/^www\./, '');
            if (host.indexOf(eHost) !== -1 || eHost.indexOf(host) !== -1) return true;
        }
        return false;
    }

    function getCurrentEngineIndex() {
        var host = getCurrentHost();
        for (var i = 0; i < engines.length; i++) {
            var eHost = engines[i].host.replace(/^www\./, '');
            if (host.indexOf(eHost) !== -1 || eHost.indexOf(host) !== -1) return i;
        }
        return -1;
    }

    function renderBar() {
        var q = encodeURIComponent(getQueryParam());
        if (!q) return;

        var old = document.getElementById('__navi_switcher_bar');
        if (old) old.remove();

        var currentIdx = getCurrentEngineIndex();

        var bar = document.createElement('div');
        bar.id = '__navi_switcher_bar';
        bar.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:99999;background:#fff;border-top:1px solid #ddd;display:flex;align-items:center;padding:6px 8px;box-shadow:0 -2px 8px rgba(0,0,0,0.1);overflow-x:auto;white-space:nowrap;font-family:sans-serif;';

        for (var i = 0; i < engines.length; i++) {
            var engine = engines[i];
            var url = engine.url.replace('{q}', q);
            var btn = document.createElement('a');
            btn.href = url;
            btn.textContent = engine.name;
            if (i === currentIdx) {
                btn.style.cssText = 'display:inline-block;padding:5px 10px;margin:0 3px;border-radius:14px;text-decoration:none;font-size:13px;color:#fff;background:#1a73e8;flex-shrink:0;';
            } else {
                btn.style.cssText = 'display:inline-block;padding:5px 10px;margin:0 3px;border-radius:14px;text-decoration:none;font-size:13px;color:#333;background:#f0f0f0;flex-shrink:0;';
            }
            btn.addEventListener('click', function(e) {
                e.preventDefault();
                window.location.href = this.href;
            });
            bar.appendChild(btn);
        }

        var gear = document.createElement('a');
        gear.href = '#';
        gear.textContent = '\u2699';
        gear.title = '\u8bbe\u7f6e';
        gear.style.cssText = 'display:inline-flex;align-items:center;justify-content:center;width:32px;height:32px;margin-left:auto;flex-shrink:0;border-radius:50%;text-decoration:none;font-size:18px;color:#666;';
        gear.addEventListener('click', function(e) {
            e.preventDefault();
            window.location.href = 'navi://switcher-settings';
        });
        bar.appendChild(gear);

        document.body.appendChild(bar);

        var existingPad = document.body.style.paddingBottom;
        if (!existingPad || parseInt(existingPad) < 50) {
            document.body.style.paddingBottom = '50px';
        }
    }

    if (isSearchPage() && getQueryParam()) {
        renderBar();
    }
})();
        """.trim()
    }
}
