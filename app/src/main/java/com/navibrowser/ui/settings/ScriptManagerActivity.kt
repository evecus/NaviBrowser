package com.navibrowser.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.navibrowser.R
import com.navibrowser.data.model.UserScript
import com.navibrowser.ui.browser.BrowserViewModel
import com.navibrowser.util.UserScriptManager
import kotlinx.coroutines.launch

class ScriptManagerActivity : AppCompatActivity() {

    val viewModel: BrowserViewModel by viewModels()
    private lateinit var adapter: ScriptListAdapter

    private val runAtOptions = arrayOf("document-start", "document-end", "document-idle")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_script_manager)
        supportActionBar?.apply { title = "油猴脚本"; setDisplayHomeAsUpEnabled(true) }

        val rv = findViewById<RecyclerView>(R.id.rvScripts)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = ScriptListAdapter(emptyList()) { script ->
            showScriptOptions(script)
        }
        rv.adapter = adapter

        val fab = findViewById<View>(R.id.fabAddScript)
        fab.setOnClickListener { showEditDialog(null) }
        // 长按 FAB：从剪贴板导入完整油猴脚本
        fab.setOnLongClickListener {
            importFromClipboard()
            true
        }

        lifecycleScope.launch {
            viewModel.scriptRepo.scripts.collect { scripts ->
                adapter.update(scripts)
                findViewById<View>(R.id.tvEmpty).visibility = if (scripts.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    /** 从剪贴板读取脚本，自动解析元数据后入库。 */
    private fun importFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = clip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
        if (text.isBlank() || !text.contains("==UserScript==")) {
            Toast.makeText(this, "剪贴板未检测到油猴脚本（需含 // ==UserScript== 头）", Toast.LENGTH_LONG).show()
            return
        }
        val meta = UserScriptManager.parseMetadata(text)
        val name = meta.name.ifEmpty { "未命名脚本" }
        val match = UserScriptManager.deriveMatchPatterns(meta)
        val exclude = UserScriptManager.deriveExcludePatterns(meta)
        val grants = meta.grants.joinToString(",")
        viewModel.addScript(
            name = name, code = text, matchPatterns = match, excludePatterns = exclude,
            namespace = meta.namespace, description = meta.description, version = meta.version.ifEmpty { "1.0" },
            runAt = meta.runAt, grants = grants
        )
        Toast.makeText(this, "已导入：$name", Toast.LENGTH_SHORT).show()
    }

    private fun showEditDialog(script: UserScript?) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_script, null)
        val etName = view.findViewById<EditText>(R.id.etScriptName)
        val etMatch = view.findViewById<EditText>(R.id.etScriptMatch)
        val etExclude = view.findViewById<EditText>(R.id.etScriptExclude)
        val etCode = view.findViewById<EditText>(R.id.etScriptCode)
        val spinnerRunAt = view.findViewById<Spinner>(R.id.spinnerRunAt)
        val etGrants = view.findViewById<EditText>(R.id.etScriptGrants)

        spinnerRunAt.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, runAtOptions)

        if (script != null) {
            etName.setText(script.name)
            etMatch.setText(script.matchPatterns)
            etExclude.setText(script.excludePatterns)
            etCode.setText(script.code)
            etGrants.setText(script.grants)
            val idx = runAtOptions.indexOf(UserScriptManager.normalizeRunAt(script.runAt)).coerceAtLeast(0)
            spinnerRunAt.setSelection(idx)
        } else {
            etMatch.setText("*://*/*")
            spinnerRunAt.setSelection(2)  // document-idle
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (script == null) "添加脚本" else "编辑脚本")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val match = etMatch.text.toString().trim()
                val code = etCode.text.toString().trim()
                if (name.isEmpty() || code.isEmpty()) {
                    Toast.makeText(this, "名称和代码不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val runAt = runAtOptions[spinnerRunAt.selectedItemPosition]
                val grants = etGrants.text.toString().trim()
                if (script != null) {
                    viewModel.updateScript(script.copy(name = name, matchPatterns = match,
                        excludePatterns = etExclude.text.toString().trim(), code = code,
                        runAt = runAt, grants = grants))
                } else {
                    viewModel.addScript(name, code, match, etExclude.text.toString().trim(),
                        runAt = runAt, grants = grants)
                }
            }
            .setNeutralButton("从代码解析", null)  // 点击逻辑在下方拦截，避免自动关闭
            .setNegativeButton("取消", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                // 解析代码中的元数据块，回填字段（不关闭对话框）
                val code = etCode.text.toString()
                if (!code.contains("==UserScript==")) {
                    Toast.makeText(this, "代码中未找到 // ==UserScript== 元数据块", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val meta = UserScriptManager.parseMetadata(code)
                if (meta.name.isNotEmpty()) etName.setText(meta.name)
                if (meta.matches.isNotEmpty() || meta.includes.isNotEmpty())
                    etMatch.setText(UserScriptManager.deriveMatchPatterns(meta))
                if (meta.excludes.isNotEmpty())
                    etExclude.setText(UserScriptManager.deriveExcludePatterns(meta))
                if (meta.grants.isNotEmpty()) etGrants.setText(meta.grants.joinToString(","))
                spinnerRunAt.setSelection(runAtOptions.indexOf(UserScriptManager.normalizeRunAt(meta.runAt)).coerceAtLeast(0))
                Toast.makeText(this, "已解析元数据", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun showScriptOptions(script: UserScript) {
        val items = arrayOf(if (script.enabled) "禁用" else "启用", "编辑", "复制源码", "删除")
        AlertDialog.Builder(this)
            .setTitle(script.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> viewModel.setScriptEnabled(script.id, !script.enabled)
                    1 -> showEditDialog(script)
                    2 -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("script", script.code))
                        Toast.makeText(this, "源码已复制", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        AlertDialog.Builder(this)
                            .setTitle("删除脚本")
                            .setMessage("确定删除「${script.name}」？")
                            .setPositiveButton("删除") { _, _ -> viewModel.deleteScript(script.id) }
                            .setNegativeButton("取消", null).show()
                    }
                }
            }
            .show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}

class ScriptListAdapter(
    private var scripts: List<UserScript>,
    private val onClick: (UserScript) -> Unit
) : RecyclerView.Adapter<ScriptListAdapter.ViewHolder>() {

    fun update(list: List<UserScript>) { scripts = list; notifyDataSetChanged() }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_script, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val script = scripts[position]
        holder.tvName.text = script.name
        // 显示匹配规则 + 运行时机，便于一眼看清脚本何时注入
        val runAtTag = UserScriptManager.normalizeRunAt(script.runAt)
        holder.tvMatch.text = "${script.matchPatterns}  ·  $runAtTag"
        holder.switchEnabled.isChecked = script.enabled
        holder.switchEnabled.setOnCheckedChangeListener(null)
        holder.switchEnabled.isChecked = script.enabled
        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            val ctx = holder.itemView.context
            if (ctx is AppCompatActivity) {
                val activity = ctx as? ScriptManagerActivity
                activity?.viewModel?.setScriptEnabled(script.id, isChecked)
            }
        }
        holder.itemView.setOnClickListener { onClick(script) }
    }

    override fun getItemCount() = scripts.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvScriptName)
        val tvMatch: TextView = view.findViewById(R.id.tvScriptMatch)
        val switchEnabled: SwitchMaterial = view.findViewById(R.id.switchScriptEnabled)
    }
}
