package com.navibrowser.ui.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.navibrowser.R
import com.navibrowser.data.db.AppDatabase
import kotlinx.coroutines.launch

class DownloadManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_manager)

        supportActionBar?.apply {
            title = "下载管理"
            setDisplayHomeAsUpEnabled(true)
        }

        val rv = findViewById<RecyclerView>(R.id.recyclerViewDownloads)
        rv.layoutManager = LinearLayoutManager(this)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)

        val db = AppDatabase.getInstance(this)
        lifecycleScope.launch {
            db.downloadDao().getAllFlow().collect { list ->
                runOnUiThread {
                    tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                    rv.adapter = DownloadListAdapter(list,
                        onRetry = { item ->
                            retryDownload(item.url, item.fileName)
                        },
                        onDelete = { item ->
                            lifecycleScope.launch {
                                // DownloadDao only has deleteById
                                db.downloadDao().deleteById(item.id)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun retryDownload(url: String, fileName: String) {
        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setDescription("正在下载")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
            addRequestHeader(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            )
        }
        dm.enqueue(request)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
