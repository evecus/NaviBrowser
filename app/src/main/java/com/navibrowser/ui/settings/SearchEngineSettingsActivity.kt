package com.navibrowser.ui.settings

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import com.navibrowser.R
import com.navibrowser.util.PrefsManager
import com.navibrowser.util.SearchEngineItem
import com.navibrowser.util.SearchEngineSwitcher

class SearchEngineSettingsActivity : AppCompatActivity() {

    private val prefs by lazy { PrefsManager(this) }
    private val engines = mutableListOf<SearchEngineItem>()
    private lateinit var engineListContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_engine_settings)
        supportActionBar?.apply { title = getString(R.string.category_quick_switch); setDisplayHomeAsUpEnabled(true) }

        engineListContainer = findViewById(R.id.engineListContainer)

        reloadEngines()

        findViewById<SwitchMaterial>(R.id.switchQuickSwitchEnabled).apply {
            isChecked = prefs.searchEngineQuickSwitchEnabled
            setOnCheckedChangeListener { _, isChecked -> prefs.searchEngineQuickSwitchEnabled = isChecked }
        }

        findViewById<View>(R.id.btnAddEngine).setOnClickListener { showAddEditDialog(null) }
        findViewById<View>(R.id.btnResetDefaults).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.quick_switch_reset_defaults)
                .setMessage(R.string.quick_switch_reset_confirm)
                .setPositiveButton("确定") { _, _ ->
                    SearchEngineSwitcher.saveEngines(prefs, SearchEngineSwitcher.DEFAULT_ENGINES)
                    reloadEngines()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun reloadEngines() {
        engines.clear()
        engines.addAll(SearchEngineSwitcher.loadEngines(prefs))
        renderEngineList()
    }

    private fun renderEngineList() {
        engineListContainer.removeAllViews()
        engines.forEachIndexed { index, engine ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_search_engine_settings, engineListContainer, false)
            row.findViewById<TextView>(R.id.tvOrder).text = "${index + 1}"
            row.findViewById<TextView>(R.id.tvEngineName).text = engine.name
            row.findViewById<TextView>(R.id.tvEngineHost).text = engine.host

            row.findViewById<View>(R.id.btnMoveUp).apply {
                isEnabled = index > 0
                setOnClickListener {
                    if (index > 0) {
                        val temp = engines[index]
                        engines[index] = engines[index - 1]
                        engines[index - 1] = temp
                        saveAndRender()
                    }
                }
            }
            row.findViewById<View>(R.id.btnMoveDown).apply {
                isEnabled = index < engines.size - 1
                setOnClickListener {
                    if (index < engines.size - 1) {
                        val temp = engines[index]
                        engines[index] = engines[index + 1]
                        engines[index + 1] = temp
                        saveAndRender()
                    }
                }
            }
            row.findViewById<View>(R.id.btnEditEngine).setOnClickListener { showAddEditDialog(index) }
            row.findViewById<View>(R.id.btnDeleteEngine).setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle(R.string.quick_switch_delete_engine)
                    .setMessage("确定删除「${engine.name}」吗？")
                    .setPositiveButton("删除") { _, _ -> engines.removeAt(index); saveAndRender() }
                    .setNegativeButton("取消", null)
                    .show()
            }

            engineListContainer.addView(row)
            val divider = LayoutInflater.from(this).inflate(R.layout.item_divider, engineListContainer, false)
            engineListContainer.addView(divider)
        }
    }

    private fun saveAndRender() {
        SearchEngineSwitcher.saveEngines(prefs, engines.toList())
        renderEngineList()
    }

    private fun showAddEditDialog(editIndex: Int?) {
        val isEdit = editIndex != null
        val existing = if (isEdit) engines[editIndex!!] else SearchEngineItem("", "", "https://")

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        container.addView(createLabel("名称"))
        val etName = EditText(this).apply {
            setText(existing.name)
            setSingleLine()
            hint = "必应"
        }
        container.addView(etName)

        container.addView(createLabel("域名"))
        val etHost = EditText(this).apply {
            setText(existing.host)
            setSingleLine()
            hint = "bing.com"
        }
        container.addView(etHost)

        container.addView(createLabel("搜索 URL"))
        val etUrl = EditText(this).apply {
            setText(existing.url)
            setSingleLine()
            hint = "https://www.bing.com/search?q={q}"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        container.addView(etUrl)

        container.addView(TextView(this).apply {
            text = "使用 {q} 代表搜索关键词"
            textSize = 12f
            setTextColor(resources.getColor(android.R.color.darker_gray, theme))
            setPadding(0, 4, 0, 0)
        })

        AlertDialog.Builder(this)
            .setTitle(if (isEdit) R.string.quick_switch_edit_engine else R.string.quick_switch_add_engine)
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val host = etHost.text.toString().trim()
                val url = etUrl.text.toString().trim()
                if (name.isEmpty() || host.isEmpty() || url.isEmpty()) {
                    Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val item = SearchEngineItem(name, host, url)
                if (isEdit) {
                    engines[editIndex!!] = item
                } else {
                    engines.add(item)
                }
                saveAndRender()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(resources.getColor(android.R.color.black, theme))
        setPadding(0, 16, 0, 4)
    }
}
