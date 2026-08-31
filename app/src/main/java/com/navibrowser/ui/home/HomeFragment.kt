package com.navibrowser.ui.home

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.navibrowser.R
import com.navibrowser.data.model.HomeShortcut
import com.navibrowser.ui.browser.BrowserActivity
import com.navibrowser.ui.browser.BrowserViewModel
import com.navibrowser.util.UrlUtils

class HomeFragment : Fragment() {

    private val viewModel: BrowserViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        val rv = root.findViewById<RecyclerView>(R.id.rvShortcuts)
        rv.layoutManager = GridLayoutManager(requireContext(), 4)

        viewModel.shortcuts.observe(viewLifecycleOwner) { shortcuts ->
            rv.adapter = ShortcutAdapter(
                shortcuts = shortcuts,
                onClick = { shortcut ->
                    (activity as? BrowserActivity)?.loadUrl(shortcut.url)
                },
                onLongClick = { shortcut ->
                    showDeleteDialog(shortcut)
                },
                onAddClick = {
                    showAddShortcutDialog()
                }
            )
        }

        return root
    }

    private fun showAddShortcutDialog() {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_shortcut, null)
        val etTitle = view.findViewById<EditText>(R.id.etShortcutTitle)
        val etUrl = view.findViewById<EditText>(R.id.etShortcutUrl)

        val currentUrl = viewModel.currentUrl.value
        val currentTitle = viewModel.currentTitle.value
        if (!currentUrl.isNullOrEmpty() && currentUrl != "navi://home") {
            etUrl.setText(currentUrl)
            etTitle.setText(currentTitle)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("添加快捷方式")
            .setView(view)
            .setPositiveButton("添加") { _, _ ->
                val title = etTitle.text.toString().trim()
                val url = etUrl.text.toString().trim()
                if (title.isNotEmpty() && url.isNotEmpty()) {
                    val fullUrl = if (url.startsWith("http")) url else "https://$url"
                    viewModel.addShortcut(title, fullUrl)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteDialog(shortcut: HomeShortcut) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除快捷方式")
            .setMessage("删除 \"${shortcut.title}\"？")
            .setPositiveButton("删除") { _, _ -> viewModel.removeShortcut(shortcut) }
            .setNegativeButton("取消", null)
            .show()
    }
}

class ShortcutAdapter(
    private val shortcuts: List<HomeShortcut>,
    private val onClick: (HomeShortcut) -> Unit,
    private val onLongClick: (HomeShortcut) -> Unit,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_SHORTCUT = 0
        private const val TYPE_ADD = 1
    }

    inner class ShortcutVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivShortcutIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvShortcutTitle)
    }

    inner class AddVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivShortcutIcon)
        val tvTitle: TextView = view.findViewById(R.id.tvShortcutTitle)
    }

    override fun getItemViewType(position: Int) =
        if (position < shortcuts.size) TYPE_SHORTCUT else TYPE_ADD

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_shortcut, parent, false)
        return if (viewType == TYPE_SHORTCUT) ShortcutVH(view) else AddVH(view)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ShortcutVH -> {
                val item = shortcuts[position]
                holder.tvTitle.text = item.title
                val faviconUrl = item.faviconUrl ?: UrlUtils.getFaviconUrl(item.url)
                Glide.with(holder.ivIcon).load(faviconUrl)
                    .placeholder(R.drawable.ic_web)
                    .error(R.drawable.ic_web)
                    .circleCrop()
                    .into(holder.ivIcon)
                holder.itemView.setOnClickListener { onClick(item) }
                holder.itemView.setOnLongClickListener { onLongClick(item); true }
            }
            is AddVH -> {
                holder.tvTitle.text = "添加"
                holder.ivIcon.setImageResource(R.drawable.ic_add_shortcut)
                holder.ivIcon.background = null
                holder.itemView.setOnClickListener { onAddClick() }
                holder.itemView.setOnLongClickListener { true }
            }
        }
    }

    override fun getItemCount() = shortcuts.size + 1 // +1 for add button
}

