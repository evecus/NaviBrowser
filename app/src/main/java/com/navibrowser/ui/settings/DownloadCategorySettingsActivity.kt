package com.navibrowser.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.navibrowser.R
import com.navibrowser.util.DownloadCategory
import com.navibrowser.util.DownloadCategoryManager
import com.navibrowser.util.PrefsManager

class DownloadCategorySettingsActivity : AppCompatActivity() {

    private val prefs by lazy { PrefsManager(this) }
    private val categories = mutableListOf<DownloadCategory>()
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_category_settings)
        supportActionBar?.apply { title = "下载分类自定义"; setDisplayHomeAsUpEnabled(true) }

        container = findViewById(R.id.categoryListContainer)
        reloadCategories()

        findViewById<View>(R.id.btnResetDefaults).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("恢复默认分类")
                .setMessage("确定要恢复默认分类吗？自定义内容将丢失。")
                .setPositiveButton("确定") { _, _ ->
                    DownloadCategoryManager.resetToDefaults(prefs)
                    reloadCategories()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun reloadCategories() {
        categories.clear()
        categories.addAll(DownloadCategoryManager.loadCategories(prefs))
        renderList()
    }

    private fun renderList() {
        container.removeAllViews()
        categories.forEachIndexed { index, cat ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_download_category_settings, container, false)
            row.findViewById<TextView>(R.id.tvOrder).text = "${index + 1}"
            row.findViewById<TextView>(R.id.tvCategoryName).text = cat.name
            row.findViewById<TextView>(R.id.tvCategoryExts).text =
                if (cat.extensions.isEmpty()) "（内置，无后缀过滤）"
                else cat.extensions.joinToString(", ")

            val btnEdit = row.findViewById<View>(R.id.btnEditCategory)
            btnEdit.setOnClickListener { showEditDialog(index) }

            container.addView(row)
            val divider = LayoutInflater.from(this).inflate(R.layout.item_divider, container, false)
            container.addView(divider)
        }
    }

    private fun showEditDialog(index: Int) {
        val cat = categories[index]
        val isAll = cat.name == "全部"

        val containerView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        containerView.addView(createLabel("分类名称"))
        val etName = EditText(this).apply {
            setText(cat.name)
            setSingleLine()
            isEnabled = !isAll
        }
        containerView.addView(etName)

        containerView.addView(createLabel("关联文件后缀"))
        val etExts = EditText(this).apply {
            setText(cat.extensions.joinToString(", "))
            setSingleLine()
            hint = "如: pdf, doc, txt"
            isEnabled = !isAll
        }
        containerView.addView(etExts)

        if (isAll) {
            containerView.addView(TextView(this).apply {
                text = "\"全部\"分类不可编辑"
                textSize = 12f
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
            })
        } else {
            containerView.addView(TextView(this).apply {
                text = "多个后缀用逗号分隔"
                textSize = 12f
                setTextColor(resources.getColor(android.R.color.darker_gray, theme))
            })
        }

        AlertDialog.Builder(this)
            .setTitle("编辑分类")
            .setView(containerView)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val exts = etExts.text.toString().split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (name.isEmpty()) {
                    Toast.makeText(this, "分类名称不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                categories[index] = DownloadCategory(name, exts)
                saveAndRender()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun saveAndRender() {
        DownloadCategoryManager.saveCategories(prefs, categories.toList())
        renderList()
    }

    private fun createLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(resources.getColor(android.R.color.black, theme))
        setPadding(0, 16, 0, 4)
    }
}
