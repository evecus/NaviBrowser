package com.navibrowser.ui.browser

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.navibrowser.R
import com.navibrowser.data.model.SearchEngine

class SearchEngineAdapter(
    private val engines: List<SearchEngine>,
    private var selected: Int,
    private val onSelect: (Int) -> Unit
) : RecyclerView.Adapter<SearchEngineAdapter.VH>() {

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context).inflate(R.layout.item_search_engine, parent, false) as TextView
        return VH(tv)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = engines[position].name
        holder.tv.isSelected = position == selected
        holder.tv.setOnClickListener {
            val prev = selected
            selected = position
            notifyItemChanged(prev)
            notifyItemChanged(position)
            onSelect(position)
        }
    }

    override fun getItemCount() = engines.size

    fun setSelected(index: Int) {
        val prev = selected
        selected = index
        notifyItemChanged(prev)
        notifyItemChanged(index)
    }
}

class SimpleUrlListAdapter(
    private val items: List<Pair<String, String>>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<SimpleUrlListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvItemTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvItemUrl)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_url_list, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val (title, url) = items[position]
        holder.tvTitle.text = title
        holder.tvUrl.text = url
        holder.itemView.setOnClickListener { onClick(url) }
    }

    override fun getItemCount() = items.size
}
