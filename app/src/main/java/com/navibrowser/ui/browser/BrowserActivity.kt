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
import com.navibrowser.databinding.ActivityBrowserBinding
import com.navibrowser.util.SearchEngineManager
import com.navibrowser.ui.download.DownloadManagerActivity
import com.navibrowser.ui.download.DownloadService
import com.navibrowser.ui.home.HomeFragment
import com.navibrowser.ui.readaloud.ReadAloudManager
import com.navibrowser.security.BiometricAuthUtil
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

    // ── 密码相关 ──
    // 当前显示的保存密码弹窗（防止同一组凭据弹多个对话框）
    private var savePasswordDialog: AlertDialog? = null
    // 已经弹过保存提示的 (domain, username, password) 三元组，避免重复弹窗
    private val promptedCredentials = mutableSetOf<Triple<String, String, String>>()
    // 当前域名已忽略保存（点过“不保存”），本次访问不再打扰
    private val dismissedDomains = mutableSetOf<String>()
    // 密码填充 FAB
    private var fillFab: FloatingActionButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)

        webViewManager = WebViewManager(this)
        setupWebViewManager()
        setupAddressBar()
        setupBottomNav()
        observeViewModel()
        applyAppearanceSettings()
        setupVideoFab()
        setupFillFab()

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

    // ── 密码填充 FAB ──────────────────────────────────────────────────────
    /** 当当前站点存在已保存凭据时显示，点击后填充用户名 / 密码。 */
    private fun setupFillFab() {
        val fab = FloatingActionButton(this).apply {
            size = FloatingActionButton.SIZE_MINI
            // 用系统钥匙图标，无需新增 drawable 资源
            setImageResource(android.R.drawable.ic_lock_lock)
            contentDescription = getString(R.string.fill_password)
            visibility = View.GONE
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            // 放在左下角，避免与右下角的视频 FAB 冲突
            gravity = Gravity.BOTTOM or Gravity.START
            setMargins(72, 0, 0, 72)
        }
        (binding.root as ViewGroup).addView(fab, lp)
        fillFab = fab
        fab.setOnClickListener { onFillFabClicked() }
        fab.setOnLongClickListener {
            // 长按跳转到密码管理器
            startActivity(Intent(this, com.navibrowser.ui.password.PasswordManagerActivity::class.java))
            true
        }
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
                // 跳转新页面时先隐藏填充按钮，等加载完成后再决定
                if (!url.startsWith("http") || isIncognito) {
                    fillFab?.visibility = View.GONE
                }
                // 切换域名时重置忽略保存标记，让用户在新站点能再次收到提示
                val newHost = UrlUtils.getDomain(url)
                if (newHost != null) dismissedDomains.retainAll(setOf(newHost))
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
                    // 刷新当前站点的已保存凭据状态，触发填充按钮显示
                    if (!isIncognito && url.startsWith("http")) {
                        viewModel.refreshSavedCredentialForCurrentUrl()
                    } else {
                        fillFab?.visibility = View.GONE
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
        webViewManager.onOpenSearchEngineSettings = {
            startActivity(Intent(this, com.navibrowser.ui.settings.SearchEngineSettingsActivity::class.java))
        }
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
                val engines = SearchEngineManager.loadEngines(viewModel.prefs)
                val idx = (viewModel.selectedSearchEngine.value ?: 0).coerceIn(0, engines.size - 1)
                val se = engines[idx].toSearchEngine()
                loadUrl(UrlUtils.processInput(input, se))
                hideKeyboard(); true
            } else false
        }
        binding.etAddress.setOnFocusChangeListener { _, hasFocus ->
            binding.etAddress.isVisible = hasFocus
            binding.tvTitle.isVisible = !hasFocus
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

    private fun observeViewModel() {
        viewModel.isBookmarked.observe(this) { bookmarked ->
            binding.btnBookmark.setImageResource(
                if (bookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline)
        }
        viewModel.pendingCredentials.observe(this) { creds ->
            creds?.let { (domain, username, password) -> showSavePasswordDialog(domain, username, password) }
        }
        // 当前站点有已保存凭据 → 显示填充按钮；否则隐藏
        viewModel.savedCredentialForSite.observe(this) { saved ->
            if (saved == null) {
                fillFab?.visibility = View.GONE
            } else {
                // 主页 / 无痕模式下不显示
                val url = viewModel.currentUrl.value ?: ""
                if (isIncognito || !url.startsWith("http")) {
                    fillFab?.visibility = View.GONE
                } else {
                    fillFab?.visibility = View.VISIBLE
                }
            }
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
        // 主页不显示填充按钮
        fillFab?.visibility = View.GONE
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
        // 切换标签后，刷新当前标签对应站点的凭据状态
        if (!isIncognito && url.startsWith("http")) viewModel.refreshSavedCredentialForCurrentUrl()
        else fillFab?.visibility = View.GONE
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
        view.findViewById<LinearLayout?>(R.id.menuSavePassword)?.setOnClickListener {
            sheet.dismiss(); manualSavePasswordForCurrentSite() }
        view.findViewById<LinearLayout?>(R.id.menuPasswordManager)?.setOnClickListener {
            sheet.dismiss(); startActivity(Intent(this, com.navibrowser.ui.password.PasswordManagerActivity::class.java)) }
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

    /**
     * 手动触发“保存此网站密码”：
     * 1. 调用注入的 __naviExtractCredentials() JS 提取页面表单中的用户名/密码。
     * 2. 弹出可编辑的保存对话框（预填提取到的值，用户可修改）。
     * 3. 若页面没有密码框，对话框留空让用户手动填。
     */
    private fun manualSavePasswordForCurrentSite() {
        val url = viewModel.currentUrl.value ?: return
        if (url == "navi://home" || !url.startsWith("http")) {
            Toast.makeText(this, "请先打开一个网页", Toast.LENGTH_SHORT).show()
            return
        }
        val wv = webViewManager.getCurrentWebView() ?: return
        wv.evaluateJavascript("(function(){try{return JSON.stringify(window.__naviExtractCredentials&&window.__naviExtractCredentials());}catch(e){return null;}})()") { result ->
            val creds = parseExtractedCredentials(result)
            val domain = UrlUtils.getDomain(url)
            if (domain == null) {
                Toast.makeText(this, "无法识别当前网站", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }
            // 总是用可编辑对话框，让用户确认 / 修改后再保存
            showManualAddPasswordDialog(domain, creds?.first ?: "", creds?.second ?: "")
        }
    }

    /** 解析 __naviExtractCredentials() 返回的 JSON 字符串 */
    private fun parseExtractedCredentials(result: String?): Pair<String, String>? {
        if (result.isNullOrBlank() || result == "null") return null
        val json = result.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
        if (json.isBlank() || json == "null") return null
        // 期望格式 {"username":"...","password":"..."}
        val userRegex = Regex("\"username\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val pwdRegex = Regex("\"password\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val u = userRegex.find(json)?.groupValues?.getOrNull(1)?.unescapeJson() ?: return null
        val p = pwdRegex.find(json)?.groupValues?.getOrNull(1)?.unescapeJson() ?: return null
        if (u.isBlank() && p.isBlank()) return null
        return Pair(u, p)
    }

    private fun String.unescapeJson(): String =
        replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n").replace("\\r", "\r").replace("\\/", "/")

    /** 手动添加密码对话框（无密码框的页面也允许保存） */
    private fun showManualAddPasswordDialog(domain: String, username: String = "", password: String = "") {
        com.navibrowser.ui.password.showPasswordDialog(
            context = this,
            title = getString(R.string.save_password_for_site),
            initialDomain = domain,
            initialUsername = username,
            initialPassword = password
        ) { d, u, p ->
            viewModel.savePassword(d, u, p)
            Snackbar.make(binding.root, "已保存", Snackbar.LENGTH_SHORT).show()
            viewModel.refreshSavedCredentialForCurrentUrl()
        }
    }

    private fun showSavePasswordDialog(domain: String, username: String, password: String) {
        if (!viewModel.prefs.savePasswordPromptEnabled) return
        if (isIncognito) return
        // 同一组凭据已弹过 → 不再打扰
        val key = Triple(domain, username, password)
        if (key in promptedCredentials) return
        // 当前域名用户已选“不保存” → 不再打扰
        if (domain in dismissedDomains) return
        promptedCredentials.add(key)

        // 关闭上一个未处理的弹窗，避免堆叠
        savePasswordDialog?.dismiss()
        savePasswordDialog = AlertDialog.Builder(this)
            .setTitle("保存密码")
            .setMessage("是否保存 $domain 的登录凭据？\n账号：$username")
            .setPositiveButton("保存") { _, _ ->
                viewModel.savePassword(domain, username, password)
                Snackbar.make(binding.root, "已保存到密码管理器", Snackbar.LENGTH_SHORT)
                    .setAction("管理") { startActivity(Intent(this, com.navibrowser.ui.password.PasswordManagerActivity::class.java)) }
                    .show()
                // 保存后立刻刷新填充按钮状态
                viewModel.refreshSavedCredentialForCurrentUrl()
            }
            .setNegativeButton("不保存") { _, _ ->
                viewModel.dismissPasswordPrompt()
                dismissedDomains.add(domain)
            }
            .setNeutralButton("永不") { _, _ ->
                viewModel.dismissPasswordPrompt()
                dismissedDomains.add(domain)
                promptedCredentials.clear()
            }
            .setOnDismissListener {
                if (savePasswordDialog === it) savePasswordDialog = null
            }
            .show()
    }

    /** 当用户点击填充 FAB 时调用 */
    private fun onFillFabClicked() {
        val saved = viewModel.savedCredentialForSite.value ?: return
        val wv = webViewManager.getCurrentWebView() ?: return
        val doFill = { user: String, pwd: String ->
            wv.evaluateJavascript(buildAutofillJs(user, pwd), null)
            Snackbar.make(binding.root, "已填充 $user", Snackbar.LENGTH_SHORT).show()
        }
        lifecycleScope.launch {
            val (user, pwd) = viewModel.decryptSaved(saved)
            if (viewModel.prefs.passwordFillAuthEnabled) {
                BiometricAuthUtil.authenticate(this@BrowserActivity, "验证身份",
                    "验证后才能填充密码",
                    onSuccess = { doFill(user, pwd) }
                )
            } else {
                doFill(user, pwd)
            }
        }
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
