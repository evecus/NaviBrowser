package com.navibrowser.ui.download

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.asLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.navibrowser.R
import com.navibrowser.data.db.AppDatabase
import com.navibrowser.data.model.DownloadItem
import com.navibrowser.data.model.DownloadStatus
import com.navibrowser.ui.browser.BrowserViewModel
import java.io.File

class DownloadManagerFragment : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.fragment_download_manager, container, false)
        val rv = root.findViewById<RecyclerView>(R.id.rvDownloads)
        rv.layoutManager = LinearLayoutManager(requireContext())

        val db = AppDatabase.getInstance(requireContext())
        db.downloadDao().getAllFlow().asLiveData().observe(viewLifecycleOwner) { downloads ->
            rv.adapter = DownloadAdapter(downloads) { item ->
                // Open file
                val file = File(item.filePath)
                if (file.exists()) {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        requireContext(), "${requireContext().packageName}.fileprovider", file
                    )
                    intent.setDataAndType(uri, item.mimeType ?: "*/*")
                    intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(intent)
                }
            }
            root.findViewById<TextView>(R.id.tvEmpty).visibility =
                if (downloads.isEmpty()) View.VISIBLE else View.GONE
        }
        return root
    }
}

class DownloadAdapter(
    private val items: List<DownloadItem>,
    private val onClick: (DownloadItem) -> Unit
) : RecyclerView.Adapter<DownloadAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvDownloadName)
        val tvStatus: TextView = view.findViewById(R.id.tvDownloadStatus)
        val progressBar: ProgressBar = view.findViewById(R.id.downloadProgress)
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
            DownloadStatus.COMPLETED -> "完成 · ${item.filePath}"
            DownloadStatus.PAUSED -> "已暂停"
            DownloadStatus.FAILED -> "下载失败"
        }
        if (item.status == DownloadStatus.DOWNLOADING && item.totalBytes > 0) {
            holder.progressBar.visibility = View.VISIBLE
            holder.progressBar.progress = (item.downloadedBytes * 100 / item.totalBytes).toInt()
        } else {
            holder.progressBar.visibility = View.GONE
        }
        holder.itemView.setOnClickListener { if (item.status == DownloadStatus.COMPLETED) onClick(item) }
    }

    override fun getItemCount() = items.size
}
