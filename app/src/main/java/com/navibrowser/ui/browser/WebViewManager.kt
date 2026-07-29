package com.navibrowser.ui.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.*
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.navibrowser.data.model.TabInfo
import com.navibrowser.util.AdBlocker
import com.navibrowser.util.PrefsManager
import com.navibrowser.util.UrlUtils
import com.navibrowser.util.VideoSniffer
import java.util.UUID

class WebViewManager(private val context: Context) {

    data class Tab(
        val id: String = UUID.randomUUID().toString(),
        var info: TabInfo = TabInfo(id = ""),
        var webView: WebView? = null,
        val isIncognito: Boolean = false
    ) { init { info = info.copy(id = id) } }

    private val tabs = mutableListOf<Tab>()
    private var currentTabId: String? = null
    val prefs = PrefsManager(context)

    val tabCount get() = tabs.size
    val currentTab get() = tabs.find { it.id == currentTabId }
    val allTabs: List<Tab> get() = tabs.toList()

    var onTitleChanged: ((String) -> Unit)? = null
    var onUrlChanged: ((String) -> Unit)? = null
    var onProgressChanged: ((Int) -> Unit)? = null
    var onFaviconReceived: ((Bitmap?) -> Unit)? = null
    var onTabsChanged: (() -> Unit)? = null
    var onCredentialsDetected: ((domain: String, username: String, password: String) -> Unit)? = null
    var onDownloadRequested: ((url: String, fileName: String, mimeType: String?) -> Unit)? = null
    var onAdBlocked: (() -> Unit)? = null
    var onVideoFound: ((VideoSniffer.SniffedVideo) -> Unit)? = null
    var onGestureAction: ((action: Int) -> Unit)? = null

    fun createTab(isIncognito: Boolean = false): Tab {
        val tab = Tab(id = UUID.randomUUID().toString(), isIncognito = isIncognito)
        tabs.add(tab); onTabsChanged?.invoke(); return tab
    }

    fun switchTo(tabId: String): WebView? {
        currentTabId = tabId
        val tab = tabs.find { it.id == tabId } ?: return null
        if (tab.webView == null) tab.webView = buildWebView(tab)
        return tab.webView
    }

    fun closeTab(tabId: String) {
        tabs.find { it.id == tabId }?.webView?.destroy()
        tabs.removeAll { it.id == tabId }
        if (currentTabId == tabId) currentTabId = tabs.lastOrNull()?.id
        onTabsChanged?.invoke()
    }

    fun getCurrentWebView(): WebView? = currentTab?.let { tab ->
        if (tab.webView == null) tab.webView = buildWebView(tab)
        tab.webView
    }

    fun destroyAll() { tabs.forEach { it.webView?.destroy() }; tabs.clear() }

    /** 切换当前标签页的桌面模式（不改全局设置） */
    fun toggleDesktopMode() {
        val wv = getCurrentWebView() ?: return
        val current = prefs.desktopModeEnabled
        prefs.desktopModeEnabled = !current
        wv.settings.userAgentString = resolveUserAgent(wv.settings)
        wv.reload()
    }

    fun isDesktopMode() = prefs.desktopModeEnabled

    fun applySettingsTo(webView: WebView?) {
        webView?.settings?.apply {
            val tab = currentTab ?: return
            javaScriptEnabled = prefs.javascriptEnabled
            loadsImagesAutomatically = prefs.imagesEnabled
            blockNetworkImage = !prefs.imagesEnabled
            domStorageEnabled = !tab.isIncognito
            databaseEnabled = !tab.isIncognito
            cacheMode = if (tab.isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            textZoom = prefs.textSize
            javaScriptCanOpenWindowsAutomatically = !prefs.blockPopups
            setSupportMultipleWindows(!prefs.blockPopups)
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = true; allowContentAccess = true
            setGeolocationEnabled(prefs.locationAccess)
            userAgentString = resolveUserAgent(this)
            CookieManager.getInstance().setAcceptCookie(prefs.cookiesEnabled && !tab.isIncognito)
            CookieManager.getInstance().setAcceptThirdPartyCookies(
                webView, prefs.cookiesEnabled && !prefs.blockThirdPartyCookies && !tab.isIncognito)
        }
    }

    private fun resolveUserAgent(settings: WebSettings): String {
        // 桌面模式开关优先级最高
        if (prefs.desktopModeEnabled) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                   "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }
        return when (prefs.userAgentMode) {
            1 -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                 "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            2 -> "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 " +
                 "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1"
            3 -> prefs.customUserAgent.ifEmpty { settings.userAgentString ?: "" }
            else -> (settings.userAgentString?.replace("wv", "")?.trim()) ?: ""
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun buildWebView(tab: Tab): WebView {
        AdBlocker.init(context)
        AdBlocker.enabled = prefs.adBlockEnabled
        AdBlocker.loadUserList(context)

        VideoSniffer.clear()
        VideoSniffer.onVideoFound = { video -> onVideoFound?.invoke(video) }

        val wv = WebView(context)
        wv.settings.apply {
            javaScriptEnabled = prefs.javascriptEnabled
            loadsImagesAutomatically = prefs.imagesEnabled
            blockNetworkImage = !prefs.imagesEnabled
            domStorageEnabled = !tab.isIncognito
            databaseEnabled = !tab.isIncognito
            cacheMode = if (tab.isIncognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
            setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
            useWideViewPort = true; loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            textZoom = prefs.textSize
            javaScriptCanOpenWindowsAutomatically = !prefs.blockPopups
            setSupportMultipleWindows(!prefs.blockPopups)
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = true; allowContentAccess = true
            setGeolocationEnabled(prefs.locationAccess)
            userAgentString = resolveUserAgent(this)
        }

        CookieManager.getInstance().setAcceptCookie(prefs.cookiesEnabled && !tab.isIncognito)
        CookieManager.getInstance().setAcceptThirdPartyCookies(
            wv, prefs.cookiesEnabled && !prefs.blockThirdPartyCookies && !tab.isIncognito)

        if (tab.isIncognito) {
            wv.clearHistory(); wv.clearCache(true)
            CookieManager.getInstance().removeAllCookies(null)
        }

        val nightMode = prefs.nightMode
        val isDark = nightMode == 1 || (nightMode == 2 &&
            (context.resources.configuration.uiMode and
             android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
             android.content.res.Configuration.UI_MODE_NIGHT_YES)
        if (isDark && WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, true)
        }

        wv.addJavascriptInterface(
            PasswordDetectorJs { username, password ->
                val domain = UrlUtils.getDomain(wv.url ?: "") ?: return@PasswordDetectorJs
                if (!tab.isIncognito) onCredentialsDetected?.invoke(domain, username, password)
            }, "PasswordDetector"
        )

        wv.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                onProgressChanged?.invoke(0)
                url?.let { onUrlChanged?.invoke(it) }
                VideoSniffer.clear()
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                onProgressChanged?.invoke(100)
                url?.let {
                    onUrlChanged?.invoke(it)
                    view.evaluateJavascript(PASSWORD_DETECTION_JS, null)
                    if (isDark) view.evaluateJavascript(NIGHT_MODE_CSS_JS, null)
                }
                val title = view.title ?: url ?: ""
                tab.info = tab.info.copy(title = title, url = url ?: "")
                onTitleChanged?.invoke(title)
                onTabsChanged?.invoke()
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http") || url.startsWith("https")) { view.loadUrl(url); return true }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                // 广告拦截
                if (AdBlocker.isBlocked(url)) { onAdBlocked?.invoke(); return emptyResponse }
                // 视频嗅探
                if (prefs.videoSnifferEnabled) {
                    val title = currentTab?.info?.title ?: ""
                    VideoSniffer.sniff(request, title)
                }
                return null
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                if (prefs.ignoreSslWarnings) handler.proceed()
                else { super.onReceivedSslError(view, handler, error); handler.cancel() }
            }

            private val emptyResponse: WebResourceResponse by lazy {
                WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { onProgressChanged?.invoke(newProgress) }
            override fun onReceivedTitle(view: WebView, title: String?) {
                val t = title ?: ""; tab.info = tab.info.copy(title = t)
                onTitleChanged?.invoke(t); onTabsChanged?.invoke()
            }
            override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                tab.info = tab.info.copy(favicon = icon)
                onFaviconReceived?.invoke(icon); onTabsChanged?.invoke()
            }
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                if (prefs.blockPopups) { result.cancel(); return true }
                return super.onJsAlert(view, url, message, result)
            }
            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                if (prefs.blockPopups) { result.cancel(); return true }
                return super.onJsConfirm(view, url, message, result)
            }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                if (prefs.blockPopups) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport
                transport?.webView = view; resultMsg.sendToTarget(); return true
            }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, prefs.locationAccess, false)
            }
        }

        wv.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            onDownloadRequested?.invoke(url, fileName, mimeType)
        }

        // 音量键快捷滚动
        if (prefs.volumeKeyGestures) {
            wv.setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) when (keyCode) {
                    KeyEvent.KEYCODE_VOLUME_UP -> { wv.pageUp(false); true }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> { wv.pageDown(false); true }
                    else -> false
                } else false
            }
        }

        // 边缘手势（左右滑动触发前进/后退等）
        if (prefs.backForwardGesture) {
            attachEdgeGesture(wv)
        }

        return wv
    }

    /** 在 WebView 左右边缘附加滑动手势 */
    private fun attachEdgeGesture(wv: WebView) {
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_MIN_DISTANCE = 120
            private val SWIPE_MIN_VELOCITY = 200

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val dx = (e2.x - (e1?.x ?: e2.x))
                val dy = (e2.y - (e1?.y ?: e2.y))
                if (Math.abs(dx) < Math.abs(dy)) return false
                if (Math.abs(dx) < SWIPE_MIN_DISTANCE || Math.abs(velocityX) < SWIPE_MIN_VELOCITY) return false
                val action = if (dx > 0) prefs.gestureRight else prefs.gestureLeft
                onGestureAction?.invoke(action)
                return true
            }
        })
        wv.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            false  // 不消费，仍传给 WebView
        }
    }
}

private const val NIGHT_MODE_CSS_JS = """
(function() {
    if (window.__naviNightInjected) return;
    window.__naviNightInjected = true;
    var s = document.createElement('style');
    s.type = 'text/css';
    s.innerHTML = 'html { filter: invert(0.92) hue-rotate(180deg) brightness(0.95) contrast(0.9); } img,video,iframe { filter: invert(1) hue-rotate(180deg); }';
    document.head.appendChild(s);
})();
"""
