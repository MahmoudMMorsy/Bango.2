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
    val gameType: String,   // "VIDEO", "AUDIO", "HTML", "FLASH", "DOC", "ARCHIVE", "FILE"
    val timestamp: Long = System.currentTimeMillis(),
    val sizeBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val status: String = "COMPLETED", // "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED"
    val isFavorite: Boolean = false,
    val speedInfo: String = "",
    val numSegments: Int = 8
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
