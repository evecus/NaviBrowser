package com.navibrowser.ui.password

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.navibrowser.R
import com.navibrowser.data.model.SavedPassword
import com.navibrowser.security.CryptoManager

class PasswordListAdapter(
    private val items: List<SavedPassword>,
    private val onDelete: (SavedPassword) -> Unit,
    private val onEdit: (SavedPassword) -> Unit,
    private val onCopyUsername: (SavedPassword) -> Unit,
    private val onCopyPassword: (SavedPassword) -> Unit,
    private val onView: (SavedPassword) -> Unit
) : RecyclerView.Adapter<PasswordListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDomain: TextView = view.findViewById(R.id.tvDomain)
        val tvUsername: TextView = view.findViewById(R.id.tvUsername)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeletePassword)
        val btnEdit: ImageButton = view.findViewById(R.id.btnEditPassword)
        val btnCopyUser: ImageButton = view.findViewById(R.id.btnCopyUser)
        val btnCopyPwd: ImageButton = view.findViewById(R.id.btnCopyPassword)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_password, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvDomain.text = item.domain
        holder.tvUsername.text = item.username
        holder.itemView.setOnClickListener { onView(item) }
        holder.btnDelete.setOnClickListener {
            AlertDialog.Builder(holder.itemView.context)
                .setTitle("删除密码")
                .setMessage("删除 ${item.domain} 的密码？")
                .setPositiveButton("删除") { _, _ -> onDelete(item) }
                .setNegativeButton("取消", null)
                .show()
        }
        holder.btnEdit.setOnClickListener { onEdit(item) }
        holder.btnCopyUser.setOnClickListener { onCopyUsername(item) }
        holder.btnCopyPwd.setOnClickListener { onCopyPassword(item) }
    }

    override fun getItemCount() = items.size
}

/** Helper to copy text to the system clipboard and toast about it. */
fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "已复制：$label", Toast.LENGTH_SHORT).show()
}

/** Decrypt the stored password; returns "解密失败" on error. */
fun decryptPassword(saved: SavedPassword): String =
    try { CryptoManager.decrypt(saved.encryptedPassword) } catch (e: Exception) { "解密失败" }

/** Show a dialog with editable fields for the password entry; calls back with new values. */
fun showPasswordDialog(
    context: Context,
    title: String,
    initialDomain: String,
    initialUsername: String,
    initialPassword: String,
    onSave: (domain: String, username: String, password: String) -> Unit
) {
    val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_password, null)
    val etDomain = view.findViewById<EditText>(R.id.etDomain)
    val etUsername = view.findViewById<EditText>(R.id.etUsername)
    val etPassword = view.findViewById<EditText>(R.id.etPassword)

    etDomain.setText(initialDomain)
    etUsername.setText(initialUsername)
    etPassword.setText(initialPassword)

    AlertDialog.Builder(context)
        .setTitle(title)
        .setView(view)
        .setPositiveButton("保存") { _, _ ->
            val d = etDomain.text.toString().trim()
            val u = etUsername.text.toString().trim()
            val p = etPassword.text.toString()
            if (d.isNotEmpty() && p.isNotEmpty()) onSave(d, u, p)
        }
        .setNegativeButton("取消", null)
        .show()
}
