package com.navibrowser.ui.download

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.navibrowser.R
import com.navibrowser.util.DownloadCategory

class CategoryTabAdapter(
    private val categories: List<DownloadCategory>,
    private var selected: String,
    private val onSelect: (String) -> Unit
) : RecyclerView.Adapter<CategoryTabAdapter.VH>() {

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download_category_tab, parent, false) as TextView
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cat = categories[position]
        holder.tv.text = cat.name
        holder.tv.isSelected = cat.name == selected
        holder.tv.setOnClickListener {
            val prev = selected
            selected = cat.name
            notifyItemChanged(categories.indexOfFirst { it.name == prev })
            notifyItemChanged(position)
            onSelect(cat.name)
        }
    }

    override fun getItemCount() = categories.size
}
