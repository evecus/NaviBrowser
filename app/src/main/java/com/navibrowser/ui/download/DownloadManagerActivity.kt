package com.navibrowser.ui.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.navibrowser.R
import com.navibrowser.data.db.AppDatabase
import com.navibrowser.data.model.DownloadItem
import com.navibrowser.util.DownloadCategory
import com.navibrowser.util.DownloadCategoryManager
import com.navibrowser.util.PrefsManager
import kotlinx.coroutines.launch

class DownloadManagerActivity : AppCompatActivity() {

    private val prefs by lazy { PrefsManager(this) }
    private var categories = listOf<DownloadCategory>()
    private var allItems = listOf<DownloadItem>()
    private var filteredItems = listOf<DownloadItem>()
    private var selectedCategory = "全部"
    private var searchQuery = ""

    private lateinit var rvCategories: RecyclerView
    private lateinit var rvDownloads: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var etSearch: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_manager)

        supportActionBar?.apply {
            title = "下载管理"
            setDisplayHomeAsUpEnabled(true)
        }

        rvCategories = findViewById(R.id.rvCategories)
        rvDownloads = findViewById(R.id.rvDownloads)
        tvEmpty = findViewById(R.id.tvEmpty)
        etSearch = findViewById(R.id.etSearch)

        rvCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvDownloads.layoutManager = LinearLayoutManager(this)

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                applyFilters()
                true
            } else false
        }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim() ?: ""
                applyFilters()
            }
        })

        loadData()
    }

    private fun loadData() {
        categories = DownloadCategoryManager.loadCategories(prefs)

        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            db.downloadDao().getAllFlow().collect { list ->
                allItems = list
                applyFilters()
            }
        }
    }

    private fun applyFilters() {
        var items = allItems

        // Filter by search query
        if (searchQuery.isNotEmpty()) {
            items = items.filter { it.fileName.contains(searchQuery, ignoreCase = true) }
        }

        // Filter by category
        if (selectedCategory != "全部") {
            items = items.filter {
                DownloadCategoryManager.categorize(it.fileName, categories) == selectedCategory
            }
        }

        filteredItems = items
        updateUI()
    }

    private fun updateUI() {
        val hasData = filteredItems.isNotEmpty()
        tvEmpty.visibility = if (hasData) View.GONE else View.VISIBLE
        rvDownloads.visibility = if (hasData) View.VISIBLE else View.GONE

        rvCategories.adapter = CategoryTabAdapter(categories, selectedCategory) { cat ->
            selectedCategory = cat
            applyFilters()
        }

        rvDownloads.adapter = DownloadListAdapter(filteredItems,
            onRetry = { item ->
                retryDownload(item.url, item.fileName)
            },
            onDelete = { item ->
                lifecycleScope.launch {
                    AppDatabase.getInstance(this@DownloadManagerActivity).downloadDao().deleteById(item.id)
                }
            }
        )
    }

    private fun retryDownload(url: String, fileName: String) {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("正在下载")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
        }
        dm.enqueue(request)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
