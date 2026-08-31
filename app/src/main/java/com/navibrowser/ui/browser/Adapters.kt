package com.navibrowser.ui.browser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.navibrowser.R

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
