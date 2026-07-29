package com.navibrowser.ui.download

import android.content.Intent
import android.view.*
import android.widget.*
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.navibrowser.R
import com.navibrowser.data.model.DownloadItem
import com.navibrowser.data.model.DownloadStatus
import java.io.File

class DownloadListAdapter(
    private val items: List<DownloadItem>,
    private val onRetry: (DownloadItem) -> Unit,
    private val onDelete: (DownloadItem) -> Unit
) : RecyclerView.Adapter<DownloadListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDownloadName)
        val tvStatus: TextView = view.findViewById(R.id.tvDownloadStatus)
        val progressBar: ProgressBar = view.findViewById(R.id.downloadProgress)
        val btnRetry: TextView = view.findViewById(R.id.btnRetry)
        val btnDelete: TextView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvName.text = item.fileName
        holder.tvStatus.text = when (item.status) {
            DownloadStatus.PENDING -> "等待中"
            DownloadStatus.DOWNLOADING -> "${item.downloadedBytes / 1024}KB / ${item.totalBytes / 1024}KB"
            DownloadStatus.COMPLETED -> "已完成"
            DownloadStatus.PAUSED -> "已暂停"
            DownloadStatus.FAILED -> "下载失败 — 点击重试"
        }

        if (item.status == DownloadStatus.DOWNLOADING && item.totalBytes > 0) {
            holder.progressBar.visibility = View.VISIBLE
            holder.progressBar.progress = (item.downloadedBytes * 100 / item.totalBytes).toInt()
        } else {
            holder.progressBar.visibility = View.GONE
        }

        // Show retry button on failure
        holder.btnRetry.visibility = if (item.status == DownloadStatus.FAILED) View.VISIBLE else View.GONE
        holder.btnRetry.setOnClickListener { onRetry(item) }

        // Always show delete
        holder.btnDelete.visibility = View.VISIBLE
        holder.btnDelete.setOnClickListener { onDelete(item) }

        // On completed, open file
        holder.itemView.setOnClickListener {
            if (item.status == DownloadStatus.COMPLETED) {
                val file = File(item.filePath)
                if (file.exists()) {
                    val ctx = holder.itemView.context
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, item.mimeType ?: "*/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(intent)
                }
            }
        }
    }

    override fun getItemCount() = items.size
}
