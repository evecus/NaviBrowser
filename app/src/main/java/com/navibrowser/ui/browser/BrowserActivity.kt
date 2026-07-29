package com.navibrowser.ui.browser

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.navibrowser.R
import com.navibrowser.data.model.SearchEngines
import com.navibrowser.databinding.ActivityBrowserBinding
import com.navibrowser.ui.download.DownloadManagerActivity
import com.navibrowser.ui.download.DownloadService
import com.navibrowser.ui.home.HomeFragment
import com.navibrowser.ui.readaloud.ReadAloudManager
import com.navibrowser.ui.settings.ScriptManagerActivity
import com.navibrowser.ui.settings.SettingsActivity
import com.navibrowser.ui.tabs.TabListFragment
import com.navibrowser.util.UserScriptManager
import com.navibrowser.util.PrefsManager
import com.navibrowser.util.UrlUtils
import com.navibrowser.util.VideoSniffer
import kotlinx.coroutines.launch

class BrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrowserBinding
    val viewModel: BrowserViewModel by viewModels()
    private lateinit var webViewManager: WebViewManager
    private var isIncognito = false

    // 朗读管理器
    private val readAloudManager by lazy { ReadAloudManager(this) }

    // 视频嗅探 FAB（动态添加，不占布局）
    private var videoFab: FloatingActionButton? = null
    private val sniffedVideos = mutableListOf<VideoSniffer.SniffedVideo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webViewManager = WebViewManager(this)
        setupWebViewManager()
        setupAddressBar()
        setupBottomNav()
        setupSearchEngineBar()
        observeViewModel()
        applyAppearanceSettings()
        setupVideoFab()

        openNewTab()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webViewManager.getCurrentWebView()
                if (wv?.canGoBack() == true) wv.goBack()
                else if (webViewManager.tabCount > 1) {
                    webViewManager.currentTab?.id?.let { webViewManager.closeTab(it) }
                    showCurrentTab()
                } else finish()
            }
        })

        intent?.data?.toString()?.let { loadUrl(it) }
    }

    override fun onResume() {
        super.onResume()
        applyAppearanceSettings()
    }

    override fun onDestroy() {
        super.onDestroy()
        readAloudManager.destroy()
        webViewManager.destroyAll()
    }

    // ── 外观设置 ─────────────────────────────────────────────────────────
    private fun applyAppearanceSettings() {
        val prefs = viewModel.prefs
        requestedOrientation = when (prefs.screenOrientation) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (prefs.hideStatusBar) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
        WindowCompat.setDecorFitsSystemWindows(window, !prefs.hideStatusBar)
    }

    // ── 视频嗅探 FAB ──────────────────────────────────────────────────────
    private fun setupVideoFab() {
        val fab = FloatingActionButton(this).apply {
            size = FloatingActionButton.SIZE_MINI
            setImageResource(android.R.drawable.ic_media_play)
            visibility = View.GONE
            contentDescription = "视频"
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            setMargins(0, 0, 72, 72)  // 紧靠右下角，不挡底栏
        }
        (binding.root as ViewGroup).addView(fab, lp)
        videoFab = fab
        fab.setOnClickListener { showVideoList() }
    }

    private fun showVideoFound(video: VideoSniffer.SniffedVideo) {
        if (!viewModel.prefs.videoSnifferEnabled) return
        if (!sniffedVideos.contains(video)) sniffedVideos.add(video)
        videoFab?.visibility = View.VISIBLE
        // 轻量提示，不打断用户
        Snackbar.make(binding.root, "检测到视频资源", Snackbar.LENGTH_SHORT)
            .setAction("播放") { showVideoList() }
            .show()
    }

    private fun showVideoList() {
        if (sniffedVideos.isEmpty()) {
            Toast.makeText(this, "当前页面暂未检测到视频", Toast.LENGTH_SHORT).show()
            return
        }
        val sheet = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }
        val title = TextView(this).apply {
            text = "检测到 ${sniffedVideos.size} 个视频资源"
            textSize = 14f
            setPadding(32, 16, 32, 8)
            setTextColor(getColor(android.R.color.darker_gray))
        }
        container.addView(title)
        sniffedVideos.forEachIndexed { i, video ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 16, 32, 16)
                isClickable = true; isFocusable = true
                background = android.util.TypedValue().also {
                    theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
                }.resourceId.let { getDrawable(it) }
            }
            val tvUrl = TextView(this).apply {
                text = video.url.take(80) + if (video.url.length > 80) "…" else ""
                textSize = 13f
                maxLines = 2
            }
            val tvMime = TextView(this).apply {
                text = video.mimeType.ifEmpty { "视频" }
                textSize = 11f
                setTextColor(getColor(android.R.color.darker_gray))
            }
            item.addView(tvUrl); item.addView(tvMime)
            item.setOnClickListener {
                sheet.dismiss()
                VideoSniffer.openVideo(this, video, viewModel.prefs.externalVideoPlayer)
            }
            item.setOnLongClickListener {
                // 长按复制链接
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("video_url", video.url))
                Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show()
                true
            }
            container.addView(item)
            if (i < sniffedVideos.size - 1) {
                View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(0x1A000000)
                }.also { container.addView(it) }
            }
        }
        val scroll = android.widget.ScrollView(this).apply { addView(container) }
        sheet.setContentView(scroll)
        sheet.show()
    }

    // ── WebViewManager 回调 ───────────────────────────────────────────────
    private fun setupWebViewManager() {
        webViewManager.onUrlChanged = { url ->
            runOnUiThread {
                viewModel.setUrl(url)
                if (url != "navi://home") {
                    binding.etAddress.setText(UrlUtils.getAddressBarText(url))
                }
                // 页面切换时清空视频列表
                sniffedVideos.clear()
                videoFab?.visibility = View.GONE
            }
        }
        webViewManager.onTitleChanged = { title ->
            runOnUiThread {
                viewModel.setTitle(title)
                val desktopTag = if (webViewManager.isDesktopMode()) " 🖥" else ""
                binding.tvTitle.text = if (isIncognito) "🕵️ $title$desktopTag" else "$title$desktopTag"
            }
        }
        webViewManager.onProgressChanged = { progress ->
            runOnUiThread {
                binding.progressBar.isVisible = progress in 1..99
                binding.progressBar.progress = progress
                if (progress == 100) {
                    val url = viewModel.currentUrl.value ?: return@runOnUiThread
                    val title = viewModel.currentTitle.value ?: return@runOnUiThread
                    if (!isIncognito && url.startsWith("http")) viewModel.addToHistory(title, url)
                    injectMatchingUserScripts(url)
                    lifecycleScope.launch {
                        val domain = UrlUtils.getDomain(url) ?: return@launch
                        val creds = viewModel.getPasswordForDomain(domain) ?: return@launch
                        webViewManager.getCurrentWebView()?.evaluateJavascript(
                            buildAutofillJs(creds.first, creds.second), null)
                    }
                }
            }
        }
        webViewManager.onTabsChanged = { runOnUiThread { updateTabCount() } }
        webViewManager.onCredentialsDetected = { domain, username, password ->
            runOnUiThread { showSavePasswordDialog(domain, username, password) }
        }
        webViewManager.onDownloadRequested = { url, fileName, mimeType ->
            runOnUiThread { startDownload(url, fileName, mimeType) }
        }
        webViewManager.onAdBlocked = {
            runOnUiThread { viewModel.prefs.blockedAdsCount = viewModel.prefs.blockedAdsCount + 1 }
        }
        webViewManager.onVideoFound = { video ->
            runOnUiThread { showVideoFound(video) }
        }
        webViewManager.onGestureAction = { action -> runOnUiThread { handleGestureAction(action) } }
    }

    /** 执行手势动作 */
    private fun handleGestureAction(action: Int) {
        val wv = webViewManager.getCurrentWebView()
        when (action) {
            PrefsManager.GestureAction.BACK -> wv?.goBack()
            PrefsManager.GestureAction.FORWARD -> wv?.goForward()
            PrefsManager.GestureAction.REFRESH -> wv?.reload()
            PrefsManager.GestureAction.NEW_TAB -> openNewTab()
            PrefsManager.GestureAction.CLOSE_TAB -> {
                webViewManager.currentTab?.id?.let { webViewManager.closeTab(it) }
                showCurrentTab()
            }
            PrefsManager.GestureAction.SCROLL_TOP ->
                wv?.evaluateJavascript("window.scrollTo(0,0);", null)
            PrefsManager.GestureAction.SCROLL_BOTTOM ->
                wv?.evaluateJavascript("window.scrollTo(0,document.body.scrollHeight);", null)
            PrefsManager.GestureAction.HOME -> loadUrl("navi://home")
        }
    }

    // ── 地址栏 ────────────────────────────────────────────────────────────
    private fun setupAddressBar() {
        binding.etAddress.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_SEARCH) {
                val input = binding.etAddress.text.toString()
                val engine = SearchEngines.list[viewModel.selectedSearchEngine.value ?: 0]
                loadUrl(UrlUtils.processInput(input, engine))
                hideKeyboard(); true
            } else false
        }
        binding.etAddress.setOnFocusChangeListener { _, hasFocus ->
            binding.etAddress.isVisible = hasFocus
            binding.tvTitle.isVisible = !hasFocus
            binding.searchEngineBar.isVisible = hasFocus
            if (hasFocus) {
                val rawUrl = viewModel.currentUrl.value ?: ""
                if (rawUrl != "navi://home" && rawUrl.isNotEmpty()) binding.etAddress.setText(rawUrl)
                binding.etAddress.selectAll()
            } else {
                val url = viewModel.currentUrl.value ?: ""
                if (url != "navi://home" && url.isNotEmpty())
                    binding.etAddress.setText(UrlUtils.getAddressBarText(url))
            }
        }
        binding.tvTitle.setOnClickListener {
            binding.etAddress.isVisible = true
            binding.tvTitle.isVisible = false
            binding.searchEngineBar.isVisible = true
            val rawUrl = viewModel.currentUrl.value ?: ""
            if (rawUrl != "navi://home" && rawUrl.isNotEmpty()) binding.etAddress.setText(rawUrl)
            else binding.etAddress.text?.clear()
            binding.etAddress.requestFocus(); binding.etAddress.selectAll()
            showKeyboardImmediately(binding.etAddress)
        }
        binding.etAddress.setOnClickListener {
            if (!binding.etAddress.hasFocus()) {
                binding.etAddress.requestFocus(); showKeyboardImmediately(binding.etAddress)
            }
        }
        binding.btnRefresh.setOnClickListener { webViewManager.getCurrentWebView()?.reload() }
        binding.btnBookmark.setOnClickListener {
            val url = viewModel.currentUrl.value ?: return@setOnClickListener
            val title = viewModel.currentTitle.value ?: url
            viewModel.toggleBookmark(title, url)
        }
    }

    private fun showKeyboardImmediately(view: android.view.View) {
        view.post {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(view, InputMethodManager.SHOW_FORCED)
        }
    }

    // ── 底部导航 ──────────────────────────────────────────────────────────
    private fun setupBottomNav() {
        binding.btnBack.setOnClickListener { webViewManager.getCurrentWebView()?.goBack() }
        binding.btnForward.setOnClickListener { webViewManager.getCurrentWebView()?.goForward() }
        binding.btnHome.setOnClickListener { loadUrl("navi://home") }
        binding.btnTabs.setOnClickListener { showTabList() }
        binding.btnMenu.setOnClickListener { showMenu() }
    }

    private fun setupSearchEngineBar() {
        val engines = SearchEngines.list
        binding.searchEngineBar.apply {
            layoutManager = LinearLayoutManager(this@BrowserActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = SearchEngineAdapter(engines, viewModel.selectedSearchEngine.value ?: 0) { index ->
                viewModel.selectSearchEngine(index)
                val url = viewModel.currentUrl.value ?: ""
                if (UrlUtils.isSearchUrl(url)) {
                    val query = UrlUtils.extractSearchQuery(url)
                    if (query != null) loadUrl(engines[index].buildSearchUrl(query))
                }
            }
        }
    }

    private fun observeViewModel() {
        viewModel.selectedSearchEngine.observe(this) { index ->
            (binding.searchEngineBar.adapter as? SearchEngineAdapter)?.setSelected(index)
        }
        viewModel.isBookmarked.observe(this) { bookmarked ->
            binding.btnBookmark.setImageResource(
                if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline)
        }
        viewModel.pendingCredentials.observe(this) { creds ->
            creds?.let { (domain, username, password) -> showSavePasswordDialog(domain, username, password) }
        }
    }

    // ── 加载页面 ──────────────────────────────────────────────────────────
    fun loadUrl(url: String) {
        if (url == "navi://home") { showHomeScreen(); return }
        hideHomeScreen()
        val wv = webViewManager.getCurrentWebView() ?: return
        binding.webViewContainer.removeAllViews()
        if (wv.parent != null) (wv.parent as ViewGroup).removeView(wv)
        binding.webViewContainer.addView(wv, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.loadUrl(url)
    }

    private fun showHomeScreen() {
        binding.webViewContainer.isVisible = false
        binding.homeContainer.isVisible = true
        val incogTag = if (isIncognito) "🕵️ " else ""
        binding.tvTitle.text = "${incogTag}主页"
        binding.etAddress.isVisible = false; binding.tvTitle.isVisible = true
        var fragment = supportFragmentManager.findFragmentByTag("home")
        if (fragment == null) {
            fragment = HomeFragment()
            supportFragmentManager.beginTransaction().replace(R.id.homeContainer, fragment, "home").commit()
        }
    }

    private fun hideHomeScreen() {
        binding.webViewContainer.isVisible = true; binding.homeContainer.isVisible = false
    }

    fun openNewTab(incognito: Boolean = this.isIncognito) {
        val tab = webViewManager.createTab(incognito)
        webViewManager.switchTo(tab.id)
        this.isIncognito = incognito
        updateTabCount(); loadUrl("navi://home")
    }

    private fun showCurrentTab() {
        val tab = webViewManager.currentTab ?: return
        isIncognito = tab.isIncognito
        val url = tab.info.url
        if (url.isEmpty() || url == "navi://home") loadUrl("navi://home") else loadUrl(url)
    }

    private fun showTabList() { TabListFragment().show(supportFragmentManager, "tabs") }

    // ── 菜单（含新功能入口）──────────────────────────────────────────────
    private fun showMenu() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.menu_bottom_sheet, null)
        sheet.setContentView(view)

        view.findViewById<LinearLayout>(R.id.menuNewTab).setOnClickListener {
            sheet.dismiss(); openNewTab(false) }
        view.findViewById<LinearLayout>(R.id.menuIncognito).setOnClickListener {
            sheet.dismiss(); openNewTab(true) }
        view.findViewById<LinearLayout>(R.id.menuBookmarks).setOnClickListener {
            sheet.dismiss(); showBookmarks() }
        view.findViewById<LinearLayout>(R.id.menuHistory).setOnClickListener {
            sheet.dismiss(); showHistory() }
        view.findViewById<LinearLayout>(R.id.menuDownloads).setOnClickListener {
            sheet.dismiss(); startActivity(Intent(this, DownloadManagerActivity::class.java)) }
        view.findViewById<LinearLayout>(R.id.menuAddShortcut).setOnClickListener {
            sheet.dismiss(); addCurrentPageToShortcuts() }
        view.findViewById<LinearLayout>(R.id.menuUserscripts).setOnClickListener {
            sheet.dismiss(); startActivity(Intent(this, ScriptManagerActivity::class.java)) }
        view.findViewById<LinearLayout>(R.id.menuSettings).setOnClickListener {
            sheet.dismiss(); startActivity(Intent(this, SettingsActivity::class.java)) }

        // ── 新增菜单项 ──
        view.findViewById<LinearLayout?>(R.id.menuDesktopMode)?.setOnClickListener {
            sheet.dismiss(); toggleDesktopMode() }
        view.findViewById<LinearLayout?>(R.id.menuReadAloud)?.setOnClickListener {
            sheet.dismiss(); toggleReadAloud() }
        view.findViewById<LinearLayout?>(R.id.menuVideoSniffer)?.setOnClickListener {
            sheet.dismiss(); showVideoList() }
        view.findViewById<LinearLayout?>(R.id.menuShare)?.setOnClickListener {
            sheet.dismiss(); shareCurrentPage() }
        view.findViewById<LinearLayout?>(R.id.menuFind)?.setOnClickListener {
            sheet.dismiss(); showFindInPage() }

        sheet.show()
    }

    // ── 桌面模式 ──────────────────────────────────────────────────────────
    private fun toggleDesktopMode() {
        webViewManager.toggleDesktopMode()
        val on = webViewManager.isDesktopMode()
        Snackbar.make(binding.root, if (on) "已切换到桌面版" else "已切换到移动版", Snackbar.LENGTH_SHORT).show()
    }

    // ── 朗读模式 ──────────────────────────────────────────────────────────
    private fun toggleReadAloud() {
        if (readAloudManager.isPlaying()) {
            readAloudManager.stop()
            Snackbar.make(binding.root, "朗读已停止", Snackbar.LENGTH_SHORT).show()
            return
        }
        val wv = webViewManager.getCurrentWebView() ?: return
        // 提取正文文本
        wv.evaluateJavascript(EXTRACT_TEXT_JS) { result ->
            val text = result?.removeSurrounding("\"")
                ?.replace("\\n", "\n")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                ?.trim() ?: ""
            if (text.isEmpty()) {
                Toast.makeText(this, "无法提取页面文本", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            readAloudManager.init()
            readAloudManager.speak(text, viewModel.prefs.readAloudSpeed, viewModel.prefs.readAloudPitch)
            Snackbar.make(binding.root, "开始朗读", Snackbar.LENGTH_LONG)
                .setAction("停止") { readAloudManager.stop() }
                .show()
        }
    }

    // ── 页内查找 ──────────────────────────────────────────────────────────
    private fun showFindInPage() {
        val wv = webViewManager.getCurrentWebView() ?: return
        val input = EditText(this).apply {
            hint = "查找内容"
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("页内查找")
            .setView(input)
            .setPositiveButton("查找下一个") { _, _ ->
                wv.findAllAsync(input.text.toString())
                wv.findNext(true)
            }
            .setNegativeButton("关闭") { _, _ -> wv.clearMatches() }
            .show()
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { wv.findAllAsync(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
    }

    // ── 分享 ──────────────────────────────────────────────────────────────
    private fun shareCurrentPage() {
        val url = viewModel.currentUrl.value ?: return
        val title = viewModel.currentTitle.value ?: url
        if (url == "navi://home") return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, url)
        }, "分享到"))
    }

    // ── 书签 / 历史 ───────────────────────────────────────────────────────
    private fun showBookmarks() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.fragment_list, null)
        dialog.setContentView(view)
        view.findViewById<TextView>(R.id.tvHeader).text = "书签"
        val rv = view.findViewById<RecyclerView>(R.id.recyclerView)
        rv.layoutManager = LinearLayoutManager(this)
        viewModel.bookmarks.value?.let { list ->
            rv.adapter = SimpleUrlListAdapter(list.map { it.title to it.url }) { url ->
                dialog.dismiss(); loadUrl(url) }
        }
        dialog.show()
    }

    private fun showHistory() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.fragment_list, null)
        dialog.setContentView(view)
        view.findViewById<TextView>(R.id.tvHeader).text = "历史记录"
        val rv = view.findViewById<RecyclerView>(R.id.recyclerView)
        rv.layoutManager = LinearLayoutManager(this)
        lifecycleScope.launch {
            viewModel.browserRepo.history.collect { list ->
                runOnUiThread {
                    rv.adapter = SimpleUrlListAdapter(list.map { it.title to it.url }) { url ->
                        dialog.dismiss(); loadUrl(url) }
                }
            }
        }
        dialog.show()
    }

    private fun addCurrentPageToShortcuts() {
        val url = viewModel.currentUrl.value ?: return
        val title = viewModel.currentTitle.value ?: url
        if (url == "navi://home") return
        viewModel.addShortcut(title, url)
        Snackbar.make(binding.root, "已添加到主页", Snackbar.LENGTH_SHORT).show()
    }

    private fun showSavePasswordDialog(domain: String, username: String, password: String) {
        AlertDialog.Builder(this)
            .setTitle("保存密码")
            .setMessage("是否保存 $domain 的登录凭据？\n账号：$username")
            .setPositiveButton("保存") { _, _ -> viewModel.savePassword(domain, username, password) }
            .setNegativeButton("不保存") { _, _ -> viewModel.dismissPasswordPrompt() }
            .show()
    }

    private fun startDownload(url: String, fileName: String, mimeType: String?) {
        val doDownload = {
            startService(Intent(this, DownloadService::class.java).apply {
                putExtra("url", url); putExtra("fileName", fileName); putExtra("mimeType", mimeType)
            })
        }
        if (viewModel.prefs.askBeforeDownload) {
            AlertDialog.Builder(this)
                .setTitle("下载文件").setMessage("是否下载：$fileName")
                .setPositiveButton("下载") { _, _ -> doDownload() }
                .setNegativeButton("取消", null).show()
        } else doDownload()
    }

    private fun updateTabCount() { viewModel.setTabs(webViewManager.allTabs) }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.etAddress.clearFocus()
        binding.searchEngineBar.isVisible = false
    }

    fun getWebViewManager() = webViewManager

    private fun injectMatchingUserScripts(url: String) {
        if (!url.startsWith("http")) return
        lifecycleScope.launch {
            val enabled = viewModel.scriptRepo.getEnabled()
            val matching = UserScriptManager.matchingScripts(url, enabled)
            if (matching.isNotEmpty()) {
                val js = UserScriptManager.buildInjectionJs(matching)
                webViewManager.getCurrentWebView()?.evaluateJavascript(js, null)
            }
        }
    }
}

// 提取页面正文的 JS
private const val EXTRACT_TEXT_JS = """
(function() {
    var article = document.querySelector('article') ||
                  document.querySelector('[role="main"]') ||
                  document.querySelector('main') ||
                  document.querySelector('.post-content') ||
                  document.querySelector('.article-body') ||
                  document.body;
    if (!article) return '';
    var clone = article.cloneNode(true);
    var scripts = clone.querySelectorAll('script,style,nav,footer,header,aside,iframe,figure');
    scripts.forEach(function(el) { el.remove(); });
    return clone.innerText || clone.textContent || '';
})()
"""
