package com.navibrowser.data.db

import androidx.room.*
import com.navibrowser.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun getAll(): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): Bookmark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: Bookmark): Long

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 500")
    fun getAllFlow(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC LIMIT 50")
    suspend fun search(query: String): List<HistoryEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntry): Long

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}

@Dao
interface PasswordDao {
    @Query("SELECT * FROM saved_passwords ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<SavedPassword>>

    @Query("SELECT * FROM saved_passwords WHERE domain = :domain LIMIT 1")
    suspend fun findByDomain(domain: String): SavedPassword?

    @Query("SELECT * FROM saved_passwords WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): SavedPassword?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(password: SavedPassword): Long

    @Update
    suspend fun update(password: SavedPassword)

    @Delete
    suspend fun delete(password: SavedPassword)

    @Query("DELETE FROM saved_passwords WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_passwords")
    suspend fun clearAll()
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<DownloadItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadItem): Long

    @Update
    suspend fun update(item: DownloadItem)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcuts ORDER BY position ASC")
    fun getAllFlow(): Flow<List<HomeShortcut>>

    @Query("SELECT * FROM shortcuts ORDER BY position ASC")
    suspend fun getAll(): List<HomeShortcut>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(shortcut: HomeShortcut): Long

    @Update
    suspend fun update(shortcut: HomeShortcut)

    @Delete
    suspend fun delete(shortcut: HomeShortcut)

    @Query("SELECT MAX(position) FROM shortcuts")
    suspend fun getMaxPosition(): Int?
}

@Dao
interface UserScriptDao {
    @Query("SELECT * FROM user_scripts ORDER BY updated_at DESC")
    fun getAllFlow(): Flow<List<UserScript>>

    @Query("SELECT * FROM user_scripts WHERE enabled = 1")
    suspend fun getEnabled(): List<UserScript>

    @Query("SELECT * FROM user_scripts WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): UserScript?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(script: UserScript): Long

    @Update
    suspend fun update(script: UserScript)

    @Delete
    suspend fun delete(script: UserScript)

    @Query("DELETE FROM user_scripts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE user_scripts SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
