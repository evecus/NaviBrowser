package com.navibrowser.ui.password

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.navibrowser.R
import com.navibrowser.data.model.SavedPassword
import com.navibrowser.ui.browser.BrowserViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PasswordManagerActivity : AppCompatActivity() {

    private val viewModel: BrowserViewModel by viewModels()
    private val queryFlow = MutableStateFlow("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_manager)

        supportActionBar?.apply {
            title = "密码管理器"
            setDisplayHomeAsUpEnabled(true)
        }

        val rv = findViewById<RecyclerView>(R.id.recyclerViewPasswords)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        val etSearch = findViewById<EditText>(R.id.etSearch)
        val fabAdd = findViewById<FloatingActionButton>(R.id.fabAdd)

        rv.layoutManager = LinearLayoutManager(this)

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                queryFlow.value = s?.toString()?.trim().orEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Use repeatOnLifecycle to avoid leaking collectors across recreation.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.passwordRepo.passwords.combine(queryFlow) { list, q ->
                    if (q.isEmpty()) list
                    else list.filter {
                        it.domain.contains(q, ignoreCase = true) ||
                            it.username.contains(q, ignoreCase = true)
                    }
                }.collect { filtered ->
                    tvEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
                    rv.adapter = PasswordListAdapter(
                        items = filtered,
                        onDelete = { viewModel.deletePassword(it.id) },
                        onEdit = { showEditDialog(it) },
                        onCopyUsername = { copyToClipboard(this@PasswordManagerActivity, "账号", it.username) },
                        onCopyPassword = {
                            val pwd = decryptPassword(it)
                            copyToClipboard(this@PasswordManagerActivity, "密码", pwd)
                        },
                        onView = { showViewDialog(it) }
                    )
                }
            }
        }

        fabAdd.setOnClickListener { showAddDialog() }
    }

    private fun showAddDialog() {
        showPasswordDialog(
            context = this,
            title = getString(R.string.add_password),
            initialDomain = "",
            initialUsername = "",
            initialPassword = ""
        ) { d, u, p ->
            viewModel.savePassword(d, u, p)
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditDialog(item: SavedPassword) {
        val currentPassword = decryptPassword(item)
        showPasswordDialog(
            context = this,
            title = getString(R.string.edit_password),
            initialDomain = item.domain,
            initialUsername = item.username,
            initialPassword = currentPassword
        ) { d, u, p ->
            viewModel.updatePassword(item.id, d, u, p)
            Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showViewDialog(item: SavedPassword) {
        val password = decryptPassword(item)
        AlertDialog.Builder(this)
            .setTitle(item.domain)
            .setMessage("账号：${item.username}\n密码：$password")
            .setPositiveButton("复制密码") { _, _ ->
                copyToClipboard(this, "密码", password)
            }
            .setNeutralButton("编辑") { _, _ -> showEditDialog(item) }
            .setNegativeButton("关闭", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
