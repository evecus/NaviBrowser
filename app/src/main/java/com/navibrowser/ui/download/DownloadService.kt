package com.navibrowser.ui.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import com.navibrowser.R
import com.navibrowser.data.db.AppDatabase
import com.navibrowser.data.model.DownloadItem
import com.navibrowser.data.model.DownloadStatus
import com.navibrowser.util.PrefsManager
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Built-in downloader using OkHttp.
 * Supports cookies from WebView session, progress tracking, and runs as foreground service.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val CHANNEL_ID = "navi_download"
        private const val NOTIF_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url") ?: return START_NOT_STICKY
        val fileName = intent.getStringExtra("fileName") ?: "download"
        val mimeType = intent.getStringExtra("mimeType")

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在下载")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
        startForeground(NOTIF_ID, notif)

        scope.launch { downloadFile(url, fileName, mimeType, startId) }
        return START_NOT_STICKY
    }

    private suspend fun downloadFile(url: String, fileName: String, mimeType: String?, startId: Int) {
        val db = AppDatabase.getInstance(this)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Sanitize filename
        val safeFileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val customDir = PrefsManager(this).downloadDir
        val downloadsDir = if (customDir.isNotEmpty()) {
            java.io.File(customDir).also { it.mkdirs() }
        } else {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).also { it.mkdirs() }
        }

        // Avoid overwriting existing files
        var destFile = File(downloadsDir, safeFileName)
        var counter = 1
        while (destFile.exists()) {
            val dotIdx = safeFileName.lastIndexOf('.')
            destFile = if (dotIdx >= 0) {
                File(downloadsDir, safeFileName.substring(0, dotIdx) + "($counter)" + safeFileName.substring(dotIdx))
            } else {
                File(downloadsDir, "$safeFileName($counter)")
            }
            counter++
        }

        val item = DownloadItem(
            url = url,
            fileName = destFile.name,
            filePath = destFile.absolutePath,
            mimeType = mimeType,
            status = DownloadStatus.DOWNLOADING
        )
        val dbId = db.downloadDao().insert(item)

        try {
            // Get cookies from the WebView's CookieManager for the request
            val cookies = CookieManager.getInstance().getCookie(url) ?: ""

            val request = Request.Builder()
                .url(url)
                .header("User-Agent",
                    "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .apply { if (cookies.isNotEmpty()) header("Cookie", cookies) }
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                db.downloadDao().update(item.copy(id = dbId, status = DownloadStatus.FAILED))
                showFailedNotification(nm, destFile.name)
                stopSelf(startId)
                return
            }

            val body = response.body ?: run {
                db.downloadDao().update(item.copy(id = dbId, status = DownloadStatus.FAILED))
                showFailedNotification(nm, destFile.name)
                stopSelf(startId)
                return
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L

            FileOutputStream(destFile).use { fos ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastNotifUpdate = 0L
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Update progress every 500ms
                        val now = System.currentTimeMillis()
                        if (now - lastNotifUpdate > 500) {
                            lastNotifUpdate = now
                            val progress = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0
                            db.downloadDao().update(
                                item.copy(id = dbId, status = DownloadStatus.DOWNLOADING,
                                    downloadedBytes = downloadedBytes, totalBytes = totalBytes)
                            )
                            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                                .setContentTitle("正在下载")
                                .setContentText(destFile.name)
                                .setSmallIcon(android.R.drawable.stat_sys_download)
                                .setOngoing(true)
                                .setProgress(100, progress, totalBytes <= 0)
                                .build()
                            nm.notify(NOTIF_ID, notif)
                        }
                    }
                }
            }

            db.downloadDao().update(
                item.copy(id = dbId, status = DownloadStatus.COMPLETED,
                    downloadedBytes = downloadedBytes, totalBytes = downloadedBytes)
            )

            // Notify completion
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this@DownloadService, "$packageName.fileprovider", destFile
                )
                setDataAndType(uri, mimeType ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val doneNotif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("下载完成")
                .setContentText(destFile.name)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            nm.notify(dbId.toInt() + 2000, doneNotif)

        } catch (e: Exception) {
            destFile.delete() // clean up partial file
            db.downloadDao().update(item.copy(id = dbId, status = DownloadStatus.FAILED))
            showFailedNotification(nm, destFile.name)
        } finally {
            stopForeground(true)
            nm.cancel(NOTIF_ID)
            stopSelf(startId)
        }
    }

    private fun showFailedNotification(nm: NotificationManager, fileName: String) {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("下载失败")
            .setContentText(fileName)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID + 1, notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "NaviBrowser 下载", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "文件下载进度通知" }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
