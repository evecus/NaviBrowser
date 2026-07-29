package com.navibrowser.ui.settings

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.switchmaterial.SwitchMaterial
import com.navibrowser.R
import com.navibrowser.ui.browser.BrowserViewModel
import com.navibrowser.ui.password.PasswordManagerActivity
import com.navibrowser.util.AdBlocker
import com.navibrowser.util.EditableSearchEngine
import com.navibrowser.util.PrefsManager
import com.navibrowser.util.SearchEngineManager

class SettingsCategoryActivity : AppCompatActivity() {
    private var currentCategoryId = CATEGORY_SEARCH

    companion object {
        const val EXTRA_CATEGORY_ID = "category_id"
        const val CATEGORY_SEARCH = 1
        const val CATEGORY_APPEARANCE = 2
        const val CATEGORY_GESTURE = 3
        const val CATEGORY_WEB = 4
        const val CATEGORY_PRIVACY = 5
        const val CATEGORY_HOME = 6
        const val CATEGORY_DOWNLOADS = 7
        const val CATEGORY_READER = 8
        const val CATEGORY_VIDEO = 9
        const val CATEGORY_READALOUD = 10
        const val CATEGORY_DATA = 11
        const val CATEGORY_ABOUT = 12
    }

    private val viewModel: BrowserViewModel by viewModels()
    private val prefs by lazy { viewModel.prefs }

    private val accentColors = listOf(
        R.color.accent_blue, R.color.accent_purple, R.color.accent_pink, R.color.accent_orange,
        R.color.accent_green, R.color.accent_teal, R.color.accent_indigo, R.color.accent_red
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_settings_category)
        } catch (e: Exception) {
            Toast.makeText(this, "布局加载失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        supportActionBar?.apply { setDisplayHomeAsUpEnabled(true) }

        currentCategoryId = intent.getIntExtra(EXTRA_CATEGORY_ID, CATEGORY_SEARCH)
        val categoryId = currentCategoryId
        val layoutRes = when (categoryId) {
            CATEGORY_SEARCH -> R.layout.settings_category_search
            CATEGORY_APPEARANCE -> R.layout.settings_category_appearance
            CATEGORY_GESTURE -> R.layout.settings_category_gesture
            CATEGORY_WEB -> R.layout.settings_category_web
            CATEGORY_PRIVACY -> R.layout.settings_category_privacy
            CATEGORY_HOME -> R.layout.settings_category_home
            CATEGORY_DOWNLOADS -> R.layout.settings_category_downloads
            CATEGORY_READER -> R.layout.settings_category_reader
            CATEGORY_VIDEO -> R.layout.settings_category_video
            CATEGORY_READALOUD -> R.layout.settings_category_readaloud
            CATEGORY_DATA -> R.layout.settings_category_data
            CATEGORY_ABOUT -> R.layout.settings_category_about
            else -> R.layout.settings_category_search
        }

        val container = findViewById<LinearLayout>(R.id.categoryContent)
        layoutInflater.inflate(layoutRes, container, true)

        supportActionBar?.title = getCategoryTitle(categoryId)

        try {
            when (categoryId) {
                CATEGORY_SEARCH -> wireSearchSection()
                CATEGORY_APPEARANCE -> wireAppearanceSection()
                CATEGORY_GESTURE -> wireGestureSection()
                CATEGORY_WEB -> wireWebSection()
                CATEGORY_PRIVACY -> wirePrivacySection()
                CATEGORY_HOME -> wireHomeSection()
                CATEGORY_DOWNLOADS -> wireDownloadSection()
                CATEGORY_READER -> wireReaderSection()
                CATEGORY_VIDEO -> wireVideoSection()
                CATEGORY_READALOUD -> wireReadAloudSection()
                CATEGORY_DATA -> wireDataSection()
                CATEGORY_ABOUT -> wireAboutSection()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "设置加载失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            findViewById<TextView>(R.id.tvBlockedAdsCount)?.text = prefs.blockedAdsCount.toString()
            val ruleCount = AdBlocker.ruleCount()
            if (ruleCount > 0)
                findViewById<TextView?>(R.id.tvAdRuleCount)?.text = "内置规则 $ruleCount 条"
        } catch (_: Exception) {}
    }

    private fun getCategoryTitle(id: Int): String = when (id) {
        CATEGORY_SEARCH -> getString(R.string.category_search)
        CATEGORY_APPEARANCE -> getString(R.string.category_appearance)
        CATEGORY_GESTURE -> getString(R.string.category_gesture)
        CATEGORY_WEB -> getString(R.string.category_web)
        CATEGORY_PRIVACY -> getString(R.string.category_privacy)
        CATEGORY_HOME -> getString(R.string.category_home)
        CATEGORY_DOWNLOADS -> getString(R.string.category_downloads)
        CATEGORY_READER -> getString(R.string.category_reader)
        CATEGORY_VIDEO -> getString(R.string.category_video)
        CATEGORY_READALOUD -> getString(R.string.category_readaloud)
        CATEGORY_DATA -> getString(R.string.category_data)
        CATEGORY_ABOUT -> getString(R.string.category_about)
        else -> getString(R.string.category_search)
    }

    // ── 搜索 ──
    private val editableEngines = mutableListOf<EditableSearchEngine>()
    private lateinit var engineListContainer: LinearLayout

    private fun wireSearchSection() {
        engineListContainer = findViewById(R.id.engineListContainer)
        reloadEditableEngines()

        refreshDefaultEngineLabel()
        findViewById<View>(R.id.rowSearchEngine).setOnClickListener { showDefaultEnginePicker() }
        findViewById<View>(R.id.btnAddEngine).setOnClickListener { showAddEngineDialog() }
        bindSwitch(R.id.switchSearchSuggestions, prefs.searchSuggestionEnabled) { prefs.searchSuggestionEnabled = it }
    }

    private fun refreshDefaultEngineLabel() {
        val list = SearchEngineManager.loadEngines(prefs)
        val idx = prefs.selectedSearchEngineIndex.coerceIn(0, list.size - 1)
        findViewById<TextView>(R.id.tvSearchEngine).text = list[idx].name
    }

    private fun showDefaultEnginePicker() {
        val list = SearchEngineManager.loadEngines(prefs)
        val names = list.map { it.name }.toTypedArray()
        val idx = prefs.selectedSearchEngineIndex.coerceIn(0, list.size - 1)
        AlertDialog.Builder(this)
            .setTitle(R.string.default_search_engine)
            .setSingleChoiceItems(names, idx) { d, w ->
                viewModel.selectSearchEngine(w)
                refreshDefaultEngineLabel()
                d.dismiss()
            }.show()
    }

    private fun reloadEditableEngines() {
        editableEngines.clear()
        editableEngines.addAll(SearchEngineManager.loadEngines(prefs))
        renderEngineList()
    }

    private fun renderEngineList() {
        engineListContainer.removeAllViews()
        editableEngines.forEachIndexed { index, engine ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_settings_search_engine_row, engineListContainer, false)
            row.findViewById<TextView>(R.id.tvEngineName).text = engine.name
            row.findViewById<TextView>(R.id.tvEngineUrl).text = engine.url

            row.setOnClickListener { showEditEngineDialog(index) }
            row.findViewById<View>(R.id.btnDeleteEngine).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("删除搜索引擎")
                    .setMessage("确定删除「${engine.name}」吗？")
                    .setPositiveButton("删除") { _, _ -> editableEngines.removeAt(index); saveAndRender() }
                    .setNegativeButton("取消", null)
                    .show()
            }

            engineListContainer.addView(row)
            val divider = LayoutInflater.from(this).inflate(R.layout.item_divider, engineListContainer, false)
            engineListContainer.addView(divider)
        }
    }

    private fun saveAndRender() {
        SearchEngineManager.saveEngines(prefs, editableEngines.toList())
        renderEngineList()
    }

    private fun showAddEngineDialog() {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        container.addView(createLabel("名称"))
        val etName = EditText(this).apply { setSingleLine(); hint = "必应" }
        container.addView(etName)
        container.addView(createLabel("搜索 URL"))
        val etUrl = EditText(this).apply { setSingleLine(); hint = "https://www.bing.com/search?q=" }
        container.addView(etUrl)
        container.addView(TextView(this).apply {
            text = "用户输入的关键词会自动拼接到此 URL 末尾"
            textSize = 12f; setTextColor(resources.getColor(android.R.color.darker_gray, theme))
        })

        AlertDialog.Builder(this).setTitle("添加搜索引擎").setView(container)
            .setPositiveButton("添加") { _, _ ->
                val name = etName.text.toString().trim()
                val url = etUrl.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) { toast("请填写所有字段"); return@setPositiveButton }
                editableEngines.add(EditableSearchEngine(name, url))
                saveAndRender()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showEditEngineDialog(index: Int) {
        val engine = editableEngines[index]
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 32, 48, 16) }
        container.addView(createLabel("名称"))
        val etName = EditText(this).apply { setText(engine.name); setSingleLine() }
        container.addView(etName)
        container.addView(createLabel("搜索 URL"))
        val etUrl = EditText(this).apply { setText(engine.url); setSingleLine() }
        container.addView(etUrl)

        AlertDialog.Builder(this).setTitle("编辑搜索引擎").setView(container)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val url = etUrl.text.toString().trim()
                if (name.isEmpty() || url.isEmpty()) { toast("请填写所有字段"); return@setPositiveButton }
                editableEngines[index] = EditableSearchEngine(name, url)
                saveAndRender()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createLabel(text: String): TextView = TextView(this).apply {
        this.text = text; textSize = 13f
        setTextColor(resources.getColor(android.R.color.black, theme))
        setPadding(0, 16, 0, 4)
    }

    // ── 外观 ──
    private fun wireAppearanceSection() {
        val nightLabels = arrayOf("始终关闭", "始终开启", "跟随系统")
        val tvNightMode = findViewById<TextView>(R.id.tvNightMode)
        tvNightMode.text = nightLabels[prefs.nightMode]
        findViewById<View>(R.id.rowNightMode).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.night_mode)
                .setSingleChoiceItems(nightLabels, prefs.nightMode) { d, w ->
                    prefs.nightMode = w; tvNightMode.text = nightLabels[w]; d.dismiss()
                }.show()
        }
        val tvNightFilter = findViewById<TextView>(R.id.tvNightFilter)
        tvNightFilter.text = "${prefs.nightFilterStrength}"
        findViewById<View>(R.id.rowNightFilter).setOnClickListener {
            val seek = SeekBar(this).apply { max = 255; progress = prefs.nightFilterStrength }
            AlertDialog.Builder(this).setTitle(R.string.night_filter_strength).setView(seek)
                .setPositiveButton("保存") { _, _ -> prefs.nightFilterStrength = seek.progress; tvNightFilter.text = seek.progress.toString() }
                .setNegativeButton("取消", null).show()
        }
        val swatch = findViewById<View>(R.id.vAccentSwatch)
        swatch.setBackgroundColor(getColor(accentColors[prefs.accentColorIndex]))
        findViewById<View>(R.id.rowAccentColor).setOnClickListener {
            val labels = arrayOf("蓝", "紫", "粉", "橙", "绿", "青", "靛", "红")
            AlertDialog.Builder(this).setTitle(R.string.accent_color)
                .setSingleChoiceItems(labels, prefs.accentColorIndex) { d, w ->
                    prefs.accentColorIndex = w; swatch.setBackgroundColor(getColor(accentColors[w])); d.dismiss()
                }.show()
        }
        val tvTextSize = findViewById<TextView>(R.id.tvTextSize)
        tvTextSize.text = "${prefs.textSize}%"
        findViewById<View>(R.id.rowTextSize).setOnClickListener {
            val seek = SeekBar(this).apply { max = 150; progress = prefs.textSize - 50 }
            val tvProg = TextView(this).apply { text = "${prefs.textSize}%" }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) { tvProg.text = "${p + 50}%" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48,32,48,16); addView(tvProg); addView(seek) }
            AlertDialog.Builder(this).setTitle(R.string.text_size).setView(c)
                .setPositiveButton("保存") { _, _ -> prefs.textSize = seek.progress + 50; tvTextSize.text = "${prefs.textSize}%" }
                .setNegativeButton("取消", null).show()
        }
        val orientLabels = arrayOf("自动", "竖屏", "横屏")
        val tvOrient = findViewById<TextView>(R.id.tvScreenOrientation)
        tvOrient.text = orientLabels[prefs.screenOrientation]
        findViewById<View>(R.id.rowScreenOrientation).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.screen_orientation)
                .setSingleChoiceItems(orientLabels, prefs.screenOrientation) { d, w ->
                    prefs.screenOrientation = w; tvOrient.text = orientLabels[w]; applyOrientation(w); d.dismiss()
                }.show()
        }
        bindSwitch(R.id.switchHideStatusBar, prefs.hideStatusBar) { prefs.hideStatusBar = it; applyImmersive(it) }
        bindSwitch(R.id.switchVolumeKeys, prefs.volumeKeyGestures) { prefs.volumeKeyGestures = it }
        bindSwitch(R.id.switchBackForwardGesture, prefs.backForwardGesture) { prefs.backForwardGesture = it }
        bindSwitch(R.id.switchPullToRefresh, prefs.pullToRefresh) { prefs.pullToRefresh = it }
    }

    // ── 手势 ──
    private fun wireGestureSection() {
        val actions = PrefsManager.GestureAction.all.toTypedArray()
        val labels = actions.map { PrefsManager.GestureAction.label(it) }.toTypedArray()
        fun makeRow(rowId: Int, tvId: Int, getter: () -> Int, setter: (Int) -> Unit, dirName: String) {
            val tv = findViewById<TextView?>(tvId) ?: return
            tv.text = PrefsManager.GestureAction.label(getter())
            findViewById<View?>(rowId)?.setOnClickListener {
                val cur = actions.indexOf(getter()).coerceAtLeast(0)
                AlertDialog.Builder(this).setTitle("$dirName 手势")
                    .setSingleChoiceItems(labels, cur) { d, w ->
                        setter(actions[w]); tv.text = labels[w]; d.dismiss()
                    }.show()
            }
        }
        makeRow(R.id.rowGestureLeft,  R.id.tvGestureLeft,  { prefs.gestureLeft },  { prefs.gestureLeft = it },  "左划")
        makeRow(R.id.rowGestureRight, R.id.tvGestureRight, { prefs.gestureRight }, { prefs.gestureRight = it }, "右划")
        makeRow(R.id.rowGestureUp,    R.id.tvGestureUp,    { prefs.gestureUp },    { prefs.gestureUp = it },    "上划")
        makeRow(R.id.rowGestureDown,  R.id.tvGestureDown,  { prefs.gestureDown },  { prefs.gestureDown = it },  "下划")
    }

    // ── 网页 ──
    private fun wireWebSection() {
        bindSwitch(R.id.switchJavascript, prefs.javascriptEnabled) { prefs.javascriptEnabled = it }
        bindSwitch(R.id.switchImages, prefs.imagesEnabled) { prefs.imagesEnabled = it }
        bindSwitch(R.id.switchCookies, prefs.cookiesEnabled) { prefs.cookiesEnabled = it }
        bindSwitch(R.id.switchBlock3rdCookies, prefs.blockThirdPartyCookies) { prefs.blockThirdPartyCookies = it }
        bindSwitch(R.id.switchBlockPopups, prefs.blockPopups) { prefs.blockPopups = it }
        bindSwitch(R.id.switchDoNotTrack, prefs.doNotTrack) { prefs.doNotTrack = it }
        bindSwitch(R.id.switchLocation, prefs.locationAccess) { prefs.locationAccess = it }
        bindSwitch(R.id.switchRequestDesktop, prefs.requestDesktopSite) { prefs.requestDesktopSite = it }
        val uaLabels = arrayOf("Android 默认", "桌面版 Chrome", "iPhone Safari", "自定义")
        val tvUa = findViewById<TextView>(R.id.tvUserAgent)
        tvUa.text = uaLabels[prefs.userAgentMode]
        findViewById<View>(R.id.rowUserAgent).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.user_agent)
                .setSingleChoiceItems(uaLabels, prefs.userAgentMode) { d, w ->
                    prefs.userAgentMode = w; tvUa.text = uaLabels[w]; toast("重新打开标签页后生效"); d.dismiss()
                }.show()
        }
        val tvCustomUa = findViewById<TextView>(R.id.tvCustomUa)
        tvCustomUa.text = prefs.customUserAgent.ifEmpty { "未设置" }
        findViewById<View>(R.id.rowCustomUa).setOnClickListener {
            val input = EditText(this).apply { setText(prefs.customUserAgent); setSingleLine(false); minLines = 2; hint = "Mozilla/5.0 ..." }
            AlertDialog.Builder(this).setTitle(R.string.custom_ua).setView(input)
                .setPositiveButton("保存") { _, _ -> val v = input.text.toString().trim(); prefs.customUserAgent = v; tvCustomUa.text = v.ifEmpty { "未设置" } }
                .setNegativeButton("取消", null).show()
        }
    }

    // ── 隐私 ──
    private fun wirePrivacySection() {
        bindSwitch(R.id.switchSavePassword, prefs.savePasswordPromptEnabled) { prefs.savePasswordPromptEnabled = it }
        bindSwitch(R.id.switchAdBlock, prefs.adBlockEnabled) { prefs.adBlockEnabled = it; AdBlocker.enabled = it }
        val tvAdList = findViewById<TextView>(R.id.tvAdBlockList)
        tvAdList.text = "${AdBlocker.getUserHosts(this).size} 项用户规则"
        findViewById<View>(R.id.rowAdBlockList).setOnClickListener {
            val current = AdBlocker.getUserHosts(this)
            val input = EditText(this).apply { setText(current.joinToString("\n")); minLines = 6; hint = "每行一个域名" }
            AlertDialog.Builder(this).setTitle("用户自定义拦截列表").setView(input)
                .setPositiveButton("保存") { _, _ ->
                    val list = input.text.toString().split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                    AdBlocker.saveUserList(this, list); tvAdList.text = "${list.size} 项用户规则"
                }
                .setNegativeButton("取消", null)
                .setNeutralButton("清除") { _, _ -> AdBlocker.saveUserList(this, emptyList()); tvAdList.text = "0 项用户规则" }
                .show()
        }
        bindSwitch(R.id.switchIgnoreSsl, prefs.ignoreSslWarnings) { prefs.ignoreSslWarnings = it }
        findViewById<View>(R.id.rowPasswordManager).setOnClickListener {
            startActivity(Intent(this, PasswordManagerActivity::class.java))
        }
        val tvClearOnExit = findViewById<TextView>(R.id.tvClearDataOnExit)
        tvClearOnExit.text = describeClearMask(prefs.clearDataOnExitMask)
        findViewById<View>(R.id.rowClearDataOnExit).setOnClickListener {
            showClearDataDialog(true) { mask -> prefs.clearDataOnExitMask = mask; tvClearOnExit.text = describeClearMask(mask) }
        }
        findViewById<View>(R.id.rowClearDataNow).setOnClickListener {
            showClearDataDialog(false) { mask -> viewModel.applyClearData(mask); toast("已清理：${describeClearMask(mask)}") }
        }
    }

    // ── 主页 ──
    private fun wireHomeSection() {
        val tvHome = findViewById<TextView>(R.id.tvHomePage)
        tvHome.text = prefs.homePage
        findViewById<View>(R.id.rowHomePage).setOnClickListener {
            val input = EditText(this).apply { setText(prefs.homePage); setSingleLine(); hint = "navi://home 或 https://..." }
            AlertDialog.Builder(this).setTitle(R.string.home_page).setView(input)
                .setPositiveButton("保存") { _, _ -> val v = input.text.toString().trim().ifEmpty { "navi://home" }; prefs.homePage = v; tvHome.text = v }
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认") { _, _ -> prefs.homePage = "navi://home"; tvHome.text = "navi://home" }
                .show()
        }
        val newTabLabels = arrayOf("打开主页", "空白页", "自定义 URL")
        val tvNewTab = findViewById<TextView>(R.id.tvNewTabBehavior)
        tvNewTab.text = newTabLabels[prefs.newTabBehavior]
        findViewById<View>(R.id.rowNewTabBehavior).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.new_tab_behavior)
                .setSingleChoiceItems(newTabLabels, prefs.newTabBehavior) { d, w ->
                    prefs.newTabBehavior = w; tvNewTab.text = newTabLabels[w]; if (w == 2) promptForCustomNewTab(); d.dismiss()
                }.show()
        }
    }

    private fun promptForCustomNewTab() {
        val input = EditText(this).apply { setText(prefs.newTabCustomUrl); setSingleLine(); hint = "https://www.bing.com" }
        AlertDialog.Builder(this).setTitle("自定义 URL").setView(input)
            .setPositiveButton("保存") { _, _ -> prefs.newTabCustomUrl = input.text.toString().trim().ifEmpty { "https://www.bing.com" } }
            .setNegativeButton("取消", null).show()
    }

    // ── 下载 ──
    private fun wireDownloadSection() {
        val tvDownloadDir = findViewById<TextView>(R.id.tvDownloadDir)
        fun refreshDir() { tvDownloadDir.text = prefs.downloadDir.ifEmpty { android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath } }
        refreshDir()
        findViewById<View>(R.id.rowDownloadDir).setOnClickListener {
            val current = prefs.downloadDir.ifEmpty { android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS).absolutePath }
            val input = EditText(this).apply { setText(current); setSingleLine(); hint = "输入目录路径" }
            AlertDialog.Builder(this).setTitle(R.string.download_dir).setView(input)
                .setPositiveButton("保存") { _, _ -> val p = input.text.toString().trim(); if (p.isNotEmpty()) { prefs.downloadDir = p; refreshDir() } }
                .setNegativeButton("取消", null)
                .setNeutralButton("恢复默认") { _, _ -> prefs.downloadDir = ""; refreshDir() }
                .show()
        }
        bindSwitch(R.id.switchWifiOnly, prefs.downloadOverWifiOnly) { prefs.downloadOverWifiOnly = it }
        bindSwitch(R.id.switchAskDownload, prefs.askBeforeDownload) { prefs.askBeforeDownload = it }
        findViewById<View>(R.id.rowDownloadCategories).setOnClickListener {
            startActivity(Intent(this, DownloadCategorySettingsActivity::class.java))
        }
    }

    // ── 阅读模式 ──
    private fun wireReaderSection() {
        val tvReaderSize = findViewById<TextView>(R.id.tvReaderTextSize)
        tvReaderSize.text = "${prefs.readerTextSize}sp"
        findViewById<View>(R.id.rowReaderTextSize).setOnClickListener {
            val seek = SeekBar(this).apply { max = 20; progress = prefs.readerTextSize - 12 }
            AlertDialog.Builder(this).setTitle(R.string.reader_text_size).setView(seek)
                .setPositiveButton("保存") { _, _ -> prefs.readerTextSize = seek.progress + 12; tvReaderSize.text = "${prefs.readerTextSize}sp" }
                .setNegativeButton("取消", null).show()
        }
        val readerThemeLabels = arrayOf("亮色", "护眼黄", "深灰", "纯黑")
        val tvReaderTheme = findViewById<TextView>(R.id.tvReaderTheme)
        tvReaderTheme.text = readerThemeLabels[prefs.readerTheme]
        findViewById<View>(R.id.rowReaderTheme).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.reader_theme)
                .setSingleChoiceItems(readerThemeLabels, prefs.readerTheme) { d, w ->
                    prefs.readerTheme = w; tvReaderTheme.text = readerThemeLabels[w]; d.dismiss()
                }.show()
        }
    }

    // ── 视频嗅探 ──
    private fun wireVideoSection() {
        bindSwitch(R.id.switchVideoSniffer, prefs.videoSnifferEnabled) { prefs.videoSnifferEnabled = it }
        val tvPlayer = findViewById<TextView?>(R.id.tvExternalPlayer) ?: return
        tvPlayer.text = prefs.externalVideoPlayer.ifEmpty { "系统默认" }
        findViewById<View?>(R.id.rowExternalPlayer)?.setOnClickListener {
            val input = EditText(this).apply { setText(prefs.externalVideoPlayer); setSingleLine(); hint = "com.mxtech.videoplayer.ad" }
            AlertDialog.Builder(this).setTitle("外部视频播放器包名")
                .setMessage("填写已安装的视频播放器包名（如 MX Player: com.mxtech.videoplayer.ad），留空使用系统默认。")
                .setView(input)
                .setPositiveButton("保存") { _, _ -> val v = input.text.toString().trim(); prefs.externalVideoPlayer = v; tvPlayer.text = v.ifEmpty { "系统默认" } }
                .setNegativeButton("取消", null)
                .setNeutralButton("清除") { _, _ -> prefs.externalVideoPlayer = ""; tvPlayer.text = "系统默认" }
                .show()
        }
    }

    // ── 朗读模式 ──
    private fun wireReadAloudSection() {
        val tvSpeed = findViewById<TextView?>(R.id.tvReadAloudSpeed) ?: return
        tvSpeed.text = "${prefs.readAloudSpeed}x"
        findViewById<View?>(R.id.rowReadAloudSpeed)?.setOnClickListener {
            val seek = SeekBar(this).apply { max = 14; progress = ((prefs.readAloudSpeed - 0.5f) / 0.125f).toInt().coerceIn(0, 14) }
            val tvProg = TextView(this).apply { text = "${prefs.readAloudSpeed}x" }
            seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, f: Boolean) { tvProg.text = "${"%.2f".format(0.5f + p * 0.125f)}x" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48,32,48,16); addView(tvProg); addView(seek) }
            AlertDialog.Builder(this).setTitle("朗读速度").setView(c)
                .setPositiveButton("保存") { _, _ ->
                    val speed = (0.5f + seek.progress * 0.125f).coerceIn(0.25f, 2.0f)
                    prefs.readAloudSpeed = speed; tvSpeed.text = "${"%.2f".format(speed)}x"
                }
                .setNegativeButton("取消", null).show()
        }
    }

    // ── 数据 ──
    private fun wireDataSection() {
        findViewById<View>(R.id.rowDefaultBrowser).setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                else startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")).addCategory(Intent.CATEGORY_BROWSABLE), "选择默认浏览器"))
            } catch (e: Exception) { toast("请在系统设置中手动设置") }
        }
        findViewById<View>(R.id.rowClearHistory).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.clear_history).setMessage("确定要清除所有历史记录吗？")
                .setPositiveButton("清除") { _, _ -> viewModel.clearHistory(); toast("历史记录已清除") }
                .setNegativeButton("取消", null).show()
        }
    }

    // ── 关于 ──
    private fun wireAboutSection() {
        try {
            val pi = packageManager.getPackageInfo(packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            findViewById<TextView>(R.id.tvVersion).text = "${pi.versionName} ($code)"
        } catch (_: Exception) {}
        AdBlocker.init(this)
        try { findViewById<TextView?>(R.id.tvAdRuleCount)?.text = "内置规则 ${AdBlocker.ruleCount()} 条" } catch (_: Exception) {}
    }

    // ── 工具 ──
    private fun bindSwitch(id: Int, current: Boolean, onChange: (Boolean) -> Unit) {
        try {
            findViewById<SwitchMaterial>(id).apply { isChecked = current; setOnCheckedChangeListener { _, c -> onChange(c) } }
        } catch (_: Exception) {}
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun describeClearMask(mask: Int): String {
        if (mask == 0) return "无"
        val parts = mutableListOf<String>()
        if (mask and PrefsManager.ClearDataFlag.HISTORY != 0) parts += "历史"
        if (mask and PrefsManager.ClearDataFlag.COOKIES != 0) parts += "Cookie"
        if (mask and PrefsManager.ClearDataFlag.CACHE != 0) parts += "缓存"
        if (mask and PrefsManager.ClearDataFlag.FORM_DATA != 0) parts += "表单"
        if (mask and PrefsManager.ClearDataFlag.PASSWORDS != 0) parts += "密码"
        if (mask and PrefsManager.ClearDataFlag.DOWNLOADS != 0) parts += "下载"
        return parts.joinToString("、")
    }

    private fun showClearDataDialog(isOnExit: Boolean, onConfirm: (Int) -> Unit) {
        val items = arrayOf("历史记录", "Cookie", "缓存", "表单数据", "已保存密码", "下载记录")
        val flags = listOf(
            PrefsManager.ClearDataFlag.HISTORY, PrefsManager.ClearDataFlag.COOKIES,
            PrefsManager.ClearDataFlag.CACHE, PrefsManager.ClearDataFlag.FORM_DATA,
            PrefsManager.ClearDataFlag.PASSWORDS, PrefsManager.ClearDataFlag.DOWNLOADS)
        val checked = BooleanArray(items.size) { i -> (prefs.clearDataOnExitMask and flags[i]) != 0 }
        AlertDialog.Builder(this)
            .setTitle(if (isOnExit) R.string.clear_data_on_exit else R.string.clear_data_now)
            .setMultiChoiceItems(items, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("确定") { _, _ ->
                var mask = 0; flags.forEachIndexed { i, f -> if (checked[i]) mask = mask or f }; onConfirm(mask)
            }
            .setNegativeButton("取消", null).show()
    }

    private fun applyOrientation(mode: Int) {
        requestedOrientation = when (mode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun applyImmersive(hide: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (hide) {
            controller.hide(WindowInsetsCompat.Type.statusBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else controller.show(WindowInsetsCompat.Type.statusBars())
        WindowCompat.setDecorFitsSystemWindows(window, !hide)
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}