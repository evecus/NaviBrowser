package com.navibrowser.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.navibrowser.R

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_settings)
        } catch (e: Exception) {
            Toast.makeText(this, "布局加载失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        supportActionBar?.apply { title = "设置"; setDisplayHomeAsUpEnabled(true) }

        wireCategoryClick(R.id.rowCategorySearch, SettingsCategoryActivity.CATEGORY_SEARCH)
        wireCategoryClick(R.id.rowCategoryAppearance, SettingsCategoryActivity.CATEGORY_APPEARANCE)
        wireCategoryClick(R.id.rowCategoryGesture, SettingsCategoryActivity.CATEGORY_GESTURE)
        wireCategoryClick(R.id.rowCategoryWeb, SettingsCategoryActivity.CATEGORY_WEB)
        wireCategoryClick(R.id.rowCategoryPrivacy, SettingsCategoryActivity.CATEGORY_PRIVACY)
        wireCategoryClick(R.id.rowCategoryHome, SettingsCategoryActivity.CATEGORY_HOME)
        wireCategoryClick(R.id.rowCategoryDownloads, SettingsCategoryActivity.CATEGORY_DOWNLOADS)
        wireCategoryClick(R.id.rowCategoryReader, SettingsCategoryActivity.CATEGORY_READER)
        wireCategoryClick(R.id.rowCategoryVideo, SettingsCategoryActivity.CATEGORY_VIDEO)
        wireCategoryClick(R.id.rowCategoryReadAloud, SettingsCategoryActivity.CATEGORY_READALOUD)
        wireCategoryClick(R.id.rowCategoryData, SettingsCategoryActivity.CATEGORY_DATA)
        wireCategoryClick(R.id.rowCategoryAbout, SettingsCategoryActivity.CATEGORY_ABOUT)
        findViewById<View>(R.id.rowCategoryScripts).setOnClickListener {
            startActivity(Intent(this, ScriptManagerActivity::class.java))
        }
        findViewById<View>(R.id.rowCategoryQuickSwitch).setOnClickListener {
            startActivity(Intent(this, SearchEngineSettingsActivity::class.java))
        }
    }

    private fun wireCategoryClick(rowId: Int, categoryId: Int) {
        findViewById<View>(rowId).setOnClickListener {
            startActivity(Intent(this, SettingsCategoryActivity::class.java).apply {
                putExtra(SettingsCategoryActivity.EXTRA_CATEGORY_ID, categoryId)
            })
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}