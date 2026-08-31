package com.navibrowser.util

import com.navibrowser.data.model.UserScript

object UserScriptManager {

    /** 油猴脚本元数据解析结果。 */
    data class ScriptMetadata(
        val name: String = "",
        val namespace: String = "",
        val description: String = "",
        val version: String = "",
        val matches: List<String> = emptyList(),
        val includes: List<String> = emptyList(),
        val excludes: List<String> = emptyList(),
        val runAt: String = "document-idle",
        val grants: List<String> = emptyList()
    )

    /**
     * 解析脚本顶部 `// ==UserScript== ... // ==/UserScript==` 元数据块。
     * 兼容多行与单行写法，未知字段忽略。
     */
    fun parseMetadata(code: String): ScriptMetadata {
        val blockRegex = Regex(
            """//\s*==UserScript==\s*\n([\s\S]*?)//\s*==/UserScript==\s*""",
            RegexOption.IGNORE_CASE
        )
        val block = blockRegex.find(code)?.groupValues?.getOrNull(1)
            ?: return ScriptMetadata()
        val lines = block.split('\n')
        val matches = mutableListOf<String>()
        val includes = mutableListOf<String>()
        val excludes = mutableListOf<String>()
        val grants = mutableListOf<String>()
        var name = ""
        var namespace = ""
        var description = ""
        var version = ""
        var runAt = "document-idle"
        for (raw in lines) {
            val line = raw.trim()
            val m = Regex("""^//\s*@([\w-]+)\s+(.*)$""").find(line) ?: continue
            val key = m.groupValues[1].lowercase()
            val value = m.groupValues[2].trim()
            when (key) {
                "name" -> if (name.isEmpty()) name = value
                "namespace" -> if (namespace.isEmpty()) namespace = value
                "description" -> if (description.isEmpty()) description = value
                "version" -> if (version.isEmpty()) version = value
                "match" -> matches.add(value)
                "include" -> includes.add(value)
                "exclude" -> excludes.add(value)
                "run-at" -> runAt = normalizeRunAt(value)
                "grant" -> grants.add(value)
            }
        }
        return ScriptMetadata(name, namespace, description, version, matches, includes, excludes, runAt, grants)
    }

    /** 规范化 run-at 值，非法值回退到 document-idle。 */
    fun normalizeRunAt(value: String): String {
        val v = value.trim().lowercase()
        return when {
            v.startsWith("document-start") || v.startsWith("start") -> "document-start"
            v.startsWith("document-end") || v.startsWith("end") -> "document-end"
            v.startsWith("document-idle") || v.startsWith("idle") -> "document-idle"
            else -> "document-idle"
        }
    }

    /** 从一段代码推断应使用的匹配规则（合并 @match 与 @include）。 */
    fun deriveMatchPatterns(meta: ScriptMetadata): String {
        val all = (meta.matches + meta.includes).filter { it.isNotEmpty() }
        return if (all.isEmpty()) "*://*/*" else all.joinToString(",")
    }

    fun deriveExcludePatterns(meta: ScriptMetadata): String =
        meta.excludes.filter { it.isNotEmpty() }.joinToString(",")

    /** 返回脚本中元数据块之外的代码（去掉头部的 metadata 块）。 */
    fun stripMetadataBlock(code: String): String {
        val blockRegex = Regex(
            """//\s*==UserScript==\s*\n[\s\S]*?//\s*==/UserScript==\s*\n?""",
            RegexOption.IGNORE_CASE
        )
        return blockRegex.replaceFirst(code, "").trimStart()
    }

    fun matchingScripts(url: String, scripts: List<UserScript>): List<UserScript> {
        if (url.isBlank() || !url.startsWith("http")) return emptyList()
        return scripts.filter { script ->
            if (!script.enabled) return@filter false
            // @match 用 glob 匹配；@include 允许通配或正则（以 / 包裹视为正则）
            val matches = splitPatterns(script.matchPatterns)
            val excludes = splitPatterns(script.excludePatterns)
            val matched = matches.isEmpty() || matches.any { matchUrl(url, it) }
            val excluded = excludes.any { matchUrl(url, it) }
            matched && !excluded
        }
    }

    private fun splitPatterns(s: String): List<String> =
        s.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * 构造注入脚本。按 run-at 分组返回，调用方可在不同时机注入。
     * 每个脚本包裹在独立的 IIFE 中并注入 GM_* API 上下文，互不污染。
     */
    fun buildInjectionJs(scripts: List<UserScript>): String {
        if (scripts.isEmpty()) return ""
        val parts = mutableListOf<String>()
        parts.add("(function(){")
        parts.add(GM_API_BOOTSTRAP)  // 提供公共的 GM 存储 / 工具，挂在 window.__naviGM
        scripts.forEach { script ->
            val safeName = script.name.replace("'", "\\'")
            parts.add("try{(function(scriptInfo){")
            parts.add(buildGmShim(script))
            parts.add(stripMetadataBlock(script.code))
            parts.add("})(" + buildScriptInfoJson(script) + ");}catch(e){console.warn('[UserScript: $safeName]',e,e.stack)}")
        }
        parts.add("})();")
        return parts.joinToString("\n")
    }

    /** 仅返回指定 run-at 的脚本，便于在不同注入点分别处理。 */
    fun scriptsForRunAt(scripts: List<UserScript>, runAt: String): List<UserScript> =
        scripts.filter { normalizeRunAt(it.runAt) == normalizeRunAt(runAt) }

    private fun buildScriptInfoJson(script: UserScript): String {
        val n = escapeJsonString(script.name)
        val ns = escapeJsonString(script.namespace)
        val d = escapeJsonString(script.description)
        val v = escapeJsonString(script.version)
        val m = escapeJsonString(script.matchPatterns)
        return """{"scriptHandler":"NaviBrowser","name":$n,"namespace":$ns,"description":$d,"version":$v,"matches":$m}"""
    }

    private fun escapeJsonString(s: String): String {
        val sb = StringBuilder("\"")
        for (ch in s) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    // ── URL 匹配 ──────────────────────────────────────────────────────

    private fun matchUrl(url: String, pattern: String): Boolean {
        val p = pattern.trim()
        if (p.isEmpty()) return false
        if (p == "<all_urls>") return url.startsWith("http")
        // /regex/ 形式：当作正则
        if (p.startsWith("/") && p.endsWith("/") && p.length > 2) {
            return try { Regex(p.substring(1, p.length - 1), RegexOption.IGNORE_CASE).containsMatchIn(url) }
            catch (_: Exception) { false }
        }
        if (!p.contains("://")) {
            // 纯通配片段，直接 includes 判断（兼容旧式 @include）
            return url.contains(p)
        }
        return try { patternToRegex(p).containsMatchIn(url) }
        catch (_: Exception) { false }
    }

    /**
     * 把 Tampermonkey 的 glob 规则转成正则。
     * 形如 https://*.example.com/path*?x=*
     * - scheme 中的 * 匹配 http/https
     * - host 中的 * 匹配任意子域（含点）
     * - path/query 中的 * 匹配任意字符
     */
    private fun patternToRegex(pattern: String): Regex {
        // 拆分 scheme://host/path?query
        val schemeEnd = pattern.indexOf("://")
        if (schemeEnd < 0) {
            return Regex("^" + Regex.escape(pattern).replace("\\*", ".*") + "$", RegexOption.IGNORE_CASE)
        }
        val scheme = pattern.substring(0, schemeEnd)
        val rest = pattern.substring(schemeEnd + 3)
        // host 到第一个 / 或 ? 之前
        val splitIdx = rest.indexOfAny(charArrayOf('/', '?'))
        val host = if (splitIdx < 0) rest else rest.substring(0, splitIdx)
        val pathQuery = if (splitIdx < 0) "" else rest.substring(splitIdx)

        val schemeRegex = if (scheme.contains('*')) {
            // *:// → http(s)? ; https*:// → https?
            "https?"
        } else Regex.escape(scheme).replace("\\*", ".*")
        // host：*.example.com → ([^/]*\\.)?example\\.com 兼容裸域与子域
        val hostRegex = if (host.startsWith("*.")) {
            "([^/]*\\.)?" + Regex.escape(host.substring(2))
        } else {
            Regex.escape(host).replace("\\*", "[^/]*")
        }
        val pathRegex = if (pathQuery.isEmpty()) "(/.*)?" else
            Regex.escape(pathQuery).replace("\\*", ".*")
        val anchored = "^$schemeRegex://$hostRegex$pathRegex$"
        return Regex(anchored, RegexOption.IGNORE_CASE)
    }

    // ── GM_* API 注入 ─────────────────────────────────────────────────

    /**
     * 公共引导：在 window 上挂一个 GM 存储后端 __naviGM，脚本侧通过 GM_setValue/getValue 操作它。
     * 存储以脚本 namespace+name 为命名空间隔离，持久化在 localStorage（页面维度即可，重启标签页后清空，
     * 足够多数轻量场景；如需跨会话持久化可后续接入原生 SharedPreferences）。
     */
    private const val GM_API_BOOTSTRAP = """
(function(){
    if (window.__naviGM) return;
    var store = {};
    function nsKey(info, key){ return (info && info.namespace ? info.namespace : '') + '|' + (info && info.name ? info.name : '') + '|' + key; }
    window.__naviGM = {
        getValue: function(info, key, def){
            try { var raw = localStorage.getItem('navigm:' + nsKey(info, key)); return raw === null ? def : JSON.parse(raw); }
            catch(e){ return def; }
        },
        setValue: function(info, key, val){
            try { localStorage.setItem('navigm:' + nsKey(info, key), JSON.stringify(val)); } catch(e){}
        },
        deleteValue: function(info, key){
            try { localStorage.removeItem('navigm:' + nsKey(info, key)); } catch(e){}
        },
        listValues: function(info){
            var prefix = 'navigm:' + ((info && info.namespace ? info.namespace : '') + '|' + (info && info.name ? info.name : '') + '|');
            var keys = [];
            for (var i = 0; i < localStorage.length; i++){
                var k = localStorage.key(i);
                if (k && k.indexOf(prefix) === 0) keys.push(k.substring(prefix.length));
            }
            return keys;
        },
        addStyle: function(css){
            var s = document.createElement('style');
            s.textContent = css;
            (document.head || document.documentElement).appendChild(s);
            return s;
        }
    };
})();
"""

    /**
     * 为单个脚本构造 GM_* 桥接对象，仅注入脚本声明了 @grant 的接口，
     * 同时提供 GM_info 与 unsafeWindow（指向 window）。
     */
    private fun buildGmShim(script: UserScript): String {
        val grants = script.grants.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val sb = StringBuilder()
        sb.append("var GM_info=scriptInfo;var unsafeWindow=window;var GM={};")
        // GM_info 兼容字段
        sb.append("GM_info.script={name:scriptInfo.name,description:scriptInfo.description,version:scriptInfo.version,namespace:scriptInfo.namespace};")
        sb.append("GM_info.version='1.0';")
        if (grants.isEmpty() || grants.contains("none")) {
            // 未声明 grant：仍提供 GM_info，便于脚本读取
            return sb.toString()
        }
        sb.append("if(window.__naviGM){")
        if (grants.any { it == "GM_getValue" || it == "GM.getValue" }) {
            sb.append("GM.getValue=function(k,d){return window.__naviGM.getValue(scriptInfo,k,d)};")
            sb.append("GM_getValue=function(k,d){return window.__naviGM.getValue(scriptInfo,k,d)};")
        }
        if (grants.any { it == "GM_setValue" || it == "GM.setValue" }) {
            sb.append("GM.setValue=function(k,v){return window.__naviGM.setValue(scriptInfo,k,v)};")
            sb.append("GM_setValue=function(k,v){window.__naviGM.setValue(scriptInfo,k,v)};")
        }
        if (grants.any { it == "GM_deleteValue" || it == "GM.deleteValue" }) {
            sb.append("GM.deleteValue=function(k){return window.__naviGM.deleteValue(scriptInfo,k)};")
            sb.append("GM_deleteValue=function(k){window.__naviGM.deleteValue(scriptInfo,k)};")
        }
        if (grants.any { it == "GM_listValues" || it == "GM.listValues" }) {
            sb.append("GM.listValues=function(){return window.__naviGM.listValues(scriptInfo)};")
            sb.append("GM_listValues=function(){return window.__naviGM.listValues(scriptInfo)};")
        }
        if (grants.any { it == "GM_addStyle" || it == "GM.addStyle" }) {
            sb.append("GM.addStyle=function(css){return window.__naviGM.addStyle(css)};")
            sb.append("GM_addStyle=function(css){return window.__naviGM.addStyle(css)};")
        }
        // GM_xmlhttpRequest：受 WebView 沙箱限制，无法真正跨域；提供一个基于 fetch 的兼容实现，
        // 同域可用，跨域依赖目标站点 CORS。声明了 @grant GM_xmlhttpRequest 时才注入。
        if (grants.any { it == "GM_xmlhttpRequest" || it == "GM.xmlHttpRequest" }) {
            sb.append("""
GM_xmlhttpRequest=function(opt){
    var ctrl={abort:function(){try{this._c && this._c.abort();}catch(e){}}};
    var doFetch=function(){
        try{
            fetch(opt.url,{method:opt.method||'GET',headers:opt.headers||{},body:opt.data||null,credentials:opt.withCredentials?'include':'omit'})
            .then(function(r){
                var text='';return r.text().then(function(t){text=t;return {finalUrl:r.url,status:r.status,readyState:4,responseHeaders:r.headers.get('content-type')||'',responseText:text,response:text};});
            })
            .then(function(res){
                if(opt.onload) opt.onload(Object.assign({context:ctrl,responseType:'text'},res));
            })
            .catch(function(e){ if(opt.onerror) opt.onerror({error:e.message,context:ctrl}); });
        }catch(e){ if(opt.onerror) opt.onerror({error:e.message,context:ctrl}); }
    };
    if(opt.onload||opt.onerror){ doFetch(); }
    return ctrl;
};
GM.xmlHttpRequest=GM_xmlhttpRequest;
""".trimIndent())
        }
        sb.append("}")
        return sb.toString()
    }
}
