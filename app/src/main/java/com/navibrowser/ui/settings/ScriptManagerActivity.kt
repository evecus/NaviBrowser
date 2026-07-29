package com.navibrowser.ui.settings

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
import kotlinx.coroutines.launch

class ScriptManagerActivity : AppCompatActivity() {

    val viewModel: BrowserViewModel by viewModels()
    private lateinit var adapter: ScriptListAdapter

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

        findViewById<View>(R.id.fabAddScript).setOnClickListener { showEditDialog(null) }

        lifecycleScope.launch {
            viewModel.scriptRepo.scripts.collect { scripts ->
                adapter.update(scripts)
                findViewById<View>(R.id.tvEmpty).visibility = if (scripts.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun showEditDialog(script: UserScript?) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_script, null)
        val etName = view.findViewById<EditText>(R.id.etScriptName)
        val etMatch = view.findViewById<EditText>(R.id.etScriptMatch)
        val etExclude = view.findViewById<EditText>(R.id.etScriptExclude)
        val etCode = view.findViewById<EditText>(R.id.etScriptCode)

        if (script != null) {
            etName.setText(script.name)
            etMatch.setText(script.matchPatterns)
            etExclude.setText(script.excludePatterns)
            etCode.setText(script.code)
        } else {
            etMatch.setText("*://*/*")
        }

        AlertDialog.Builder(this)
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
                if (script != null) {
                    viewModel.updateScript(script.copy(name = name, matchPatterns = match,
                        excludePatterns = etExclude.text.toString().trim(), code = code))
                } else {
                    viewModel.addScript(name, code, match, etExclude.text.toString().trim())
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showScriptOptions(script: UserScript) {
        val items = arrayOf(if (script.enabled) "禁用" else "启用", "编辑", "删除")
        AlertDialog.Builder(this)
            .setTitle(script.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> viewModel.setScriptEnabled(script.id, !script.enabled)
                    1 -> showEditDialog(script)
                    2 -> {
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
        holder.tvMatch.text = script.matchPatterns
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