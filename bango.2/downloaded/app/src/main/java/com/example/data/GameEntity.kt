package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val sourceUrl: String,
    val localFolder: String, // local folder name or path under context.filesDir
    val zipPath: String,    // local ZIP file path for sharing/exporting
    val gameType: String,   // "HTML" or "FLASH"
    val timestamp: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0,
    val isFavorite: Boolean = false
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)
