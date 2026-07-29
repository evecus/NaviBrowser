package com.navibrowser.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.navibrowser.data.model.*

@Database(
    entities = [Bookmark::class, HistoryEntry::class, SavedPassword::class, DownloadItem::class, HomeShortcut::class, UserScript::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun passwordDao(): PasswordDao
    abstract fun downloadDao(): DownloadDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun userScriptDao(): UserScriptDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "navibrowser.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
