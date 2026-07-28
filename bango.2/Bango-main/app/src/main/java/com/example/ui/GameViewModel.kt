package com.example.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.BookmarkEntity
import com.example.data.GameEntity
import com.example.data.GameRepository
import com.example.data.HistoryEntity
import com.example.util.GameDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class DownloadState {
    object Idle : DownloadState()
    data class Progress(val message: String) : DownloadState()
    data class Success(val gameTitle: String) : DownloadState()
    data class Error(val errorMessage: String) : DownloadState()
}

class GameViewModel(private val repository: GameRepository) : ViewModel() {

    // Database Flows
    val allGames: StateFlow<List<GameEntity>> = repository.allItemsFlow()
    val allBookmarks: StateFlow<List<BookmarkEntity>> = repository.allBookmarks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentHistory: StateFlow<List<HistoryEntity>> = repository.recentHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Browser State
    private val _currentUrl = MutableStateFlow("https://www.bing.com")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _isCurrentPageBookmarked = MutableStateFlow(false)
    val isCurrentPageBookmarked: StateFlow<Boolean> = _isCurrentPageBookmarked.asStateFlow()

    // Download State
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    // Playing Game State
    private val _playingGame = MutableStateFlow<GameEntity?>(null)
    val playingGame: StateFlow<GameEntity?> = _playingGame.asStateFlow()

    // Query for Search / URL Bar
    val searchInput = MutableStateFlow("")

    init {
        // Observe bookmark status of the current URL
        viewModelScope.launch {
            _currentUrl.collectLatest { url ->
                repository.isBookmarked(url).collect { bookmarked ->
                    _isCurrentPageBookmarked.value = bookmarked
                }
            }
        }
    }

    private fun GameRepository.allItemsFlow(): StateFlow<List<GameEntity>> {
        return allGames.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun updateUrl(url: String) {
        _currentUrl.value = url
        searchInput.value = url
    }

    fun addBookmark(title: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertBookmark(BookmarkEntity(title = title, url = url))
        }
    }

    fun removeBookmarkByUrl(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBookmarkByUrl(url)
        }
    }

    fun addHistoryEntry(title: String, url: String) {
        if (url.startsWith("file://") || url.startsWith("data:")) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertHistory(HistoryEntity(title = title, url = url))
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
        }
    }

    fun deleteHistoryById(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteHistoryById(id)
        }
    }

    fun toggleFavorite(game: GameEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateGame(game.copy(isFavorite = !game.isFavorite))
        }
    }

    fun resetDownloadState() {
        _downloadState.value = DownloadState.Idle
    }

    /**
     * Downloads an HTML webpage and compiles its resources into a local ZIP and index folder.
     */
    fun downloadHtmlGame(context: Context, url: String, title: String) {
        viewModelScope.launch {
            val gameId = java.util.UUID.randomUUID().toString().take(8)
            val initialGame = GameEntity(
                title = title,
                sourceUrl = url,
                localFolder = "",
                zipPath = "",
                gameType = "HTML",
                status = "DOWNLOADING",
                speedInfo = "Scraping page...",
                timestamp = System.currentTimeMillis()
            )
            val id = repository.insertGame(initialGame).toInt()
            val insertedGame = repository.getGameById(id) ?: initialGame

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val game = GameDownloader.downloadHtmlGameSource(context, url, title) { progress ->
                        viewModelScope.launch(Dispatchers.IO) {
                            repository.updateGame(insertedGame.copy(speedInfo = progress))
                        }
                    }
                    repository.updateGame(game.copy(
                        id = id,
                        status = "COMPLETED",
                        speedInfo = "Finished",
                        isFavorite = insertedGame.isFavorite
                    ))
                } catch (e: Exception) {
                    Log.e("GameViewModel", "Download HTML failed", e)
                    repository.updateGame(insertedGame.copy(
                        status = "FAILED",
                        speedInfo = "Error: ${e.message}"
                    ))
                }
            }
        }
    }

    /**
     * Downloads a ZIP file containing an HTML game and extracts it.
     */
    fun downloadZipGame(context: Context, zipUrl: String, title: String) {
        viewModelScope.launch {
            val gameId = java.util.UUID.randomUUID().toString().take(8)
            val initialGame = GameEntity(
                title = title,
                sourceUrl = zipUrl,
                localFolder = "",
                zipPath = "",
                gameType = "HTML",
                status = "DOWNLOADING",
                speedInfo = "Starting download...",
                timestamp = System.currentTimeMillis()
            )
            val id = repository.insertGame(initialGame).toInt()
            val insertedGame = repository.getGameById(id) ?: initialGame

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val game = GameDownloader.downloadDirectZipGame(context, zipUrl, title) { progress ->
                        viewModelScope.launch(Dispatchers.IO) {
                            repository.updateGame(insertedGame.copy(speedInfo = progress))
                        }
                    }
                    repository.updateGame(game.copy(
                        id = id,
                        status = "COMPLETED",
                        speedInfo = "Finished",
                        isFavorite = insertedGame.isFavorite
                    ))
                } catch (e: Exception) {
                    Log.e("GameViewModel", "Download ZIP failed", e)
                    repository.updateGame(insertedGame.copy(
                        status = "FAILED",
                        speedInfo = "Error: ${e.message}"
                    ))
                }
            }
        }
    }

    /**
     * Downloads a Flash (.swf) game.
     */
    fun downloadFlashGame(context: Context, swfUrl: String, title: String) {
        viewModelScope.launch {
            val gameId = java.util.UUID.randomUUID().toString().take(8)
            val initialGame = GameEntity(
                title = title,
                sourceUrl = swfUrl,
                localFolder = "",
                zipPath = "",
                gameType = "FLASH",
                status = "DOWNLOADING",
                speedInfo = "Starting SWF download...",
                timestamp = System.currentTimeMillis()
            )
            val id = repository.insertGame(initialGame).toInt()
            val insertedGame = repository.getGameById(id) ?: initialGame

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val game = GameDownloader.downloadFlashGameSource(context, swfUrl, title) { progress ->
                        viewModelScope.launch(Dispatchers.IO) {
                            repository.updateGame(insertedGame.copy(speedInfo = progress))
                        }
                    }
                    repository.updateGame(game.copy(
                        id = id,
                        status = "COMPLETED",
                        speedInfo = "Finished",
                        isFavorite = insertedGame.isFavorite
                    ))
                } catch (e: Exception) {
                    Log.e("GameViewModel", "Download Flash failed", e)
                    repository.updateGame(insertedGame.copy(
                        status = "FAILED",
                        speedInfo = "Error: ${e.message}"
                    ))
                }
            }
        }
    }

    /**
     * Downloads any video, audio, or general file using multi-segment parallel accelerator.
     */
    fun downloadResumableMedia(
        context: Context,
        url: String,
        title: String,
        category: String = "VIDEO",
        numSegments: Int = 8,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null
    ) {
        viewModelScope.launch {
            val gameId = java.util.UUID.randomUUID().toString().take(8)
            val cleanTitle = title.replace("[^a-zA-Z0-9\\s_\\-أ-ي]".toRegex(), "").ifBlank { "Download_$gameId" }

            val ext = GameDownloader.getFileExtensionFromUrl(url).ifBlank {
                when (category) {
                    "VIDEO" -> "mp4"
                    "AUDIO" -> "mp3"
                    "DOC" -> "pdf"
                    "ARCHIVE" -> "zip"
                    else -> "bin"
                }
            }
            val downloadsDir = java.io.File(context.filesDir, "downloads")
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val targetFile = java.io.File(downloadsDir, "${cleanTitle.replace(" ", "_")}_$gameId.$ext")

            val initialGame = GameEntity(
                title = title,
                sourceUrl = url,
                localFolder = downloadsDir.absolutePath,
                zipPath = targetFile.absolutePath,
                gameType = category,
                status = "DOWNLOADING",
                speedInfo = "0% (Initializing...)",
                timestamp = System.currentTimeMillis()
            )

            val id = repository.insertGame(initialGame).toInt()
            val insertedGame = repository.getGameById(id) ?: initialGame

            // Background download execution
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val isVideoSharingSite = url.contains("youtube.com") ||
                                             url.contains("youtu.be") ||
                                             url.contains("vimeo.com") ||
                                             url.contains("dailymotion.com") ||
                                             url.contains("instagram.com") ||
                                             url.contains("tiktok.com") ||
                                             url.contains("facebook.com")

                    val downloadedGame = if (isVideoSharingSite) {
                        // Use YoutubeDL downloader
                        var lastUpdate = 0L
                        GameDownloader.downloadWithYoutubeDL(context, url, title) { progressMsg ->
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 600) {
                                lastUpdate = now
                                viewModelScope.launch(Dispatchers.IO) {
                                    repository.updateGame(insertedGame.copy(
                                        speedInfo = progressMsg
                                    ))
                                }
                            }
                        }
                    } else {
                        // Use parallel range-support IDM downloader
                        var lastUpdate = 0L
                        GameDownloader.downloadResumableFile(
                            context = context,
                            fileUrl = url,
                            title = title,
                            fileTypeCategory = category,
                            numSegments = numSegments,
                            userAgent = userAgent,
                            cookies = cookies,
                            referer = referer
                        ) { msg, downloadedBytes, totalBytes, speed, threads ->
                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 600 || downloadedBytes == totalBytes) {
                                lastUpdate = now
                                val speedText = GameDownloader.formatSpeed(speed)
                                val percent = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0
                                viewModelScope.launch(Dispatchers.IO) {
                                    repository.updateGame(insertedGame.copy(
                                        downloadedBytes = downloadedBytes,
                                        sizeBytes = totalBytes,
                                        speedInfo = "$percent% | $speedText ($threads Streams)"
                                    ))
                                }
                            }
                        }
                    }

                    repository.updateGame(downloadedGame.copy(
                        id = id,
                        status = "COMPLETED",
                        speedInfo = "Completed",
                        isFavorite = insertedGame.isFavorite
                    ))
                } catch (e: Exception) {
                    Log.e("GameViewModel", "Download failed: $url", e)
                    repository.updateGame(insertedGame.copy(
                        status = "FAILED",
                        speedInfo = "Error: ${e.message ?: "Download failed"}"
                    ))
                }
            }
        }
    }

    /**
     * Enqueues download using system DownloadManager.
     */
    fun downloadViaSystemManager(
        context: Context,
        url: String,
        title: String,
        userAgent: String? = null,
        cookies: String? = null,
        referer: String? = null
    ) {
        try {
            GameDownloader.enqueueSystemDownload(context, url, title, userAgent, cookies, referer)
            _downloadState.value = DownloadState.Success("$title added to System Downloads")
        } catch (e: Exception) {
            Log.e("GameViewModel", "System DownloadManager failed", e)
            _downloadState.value = DownloadState.Error("Failed to enqueue system download: ${e.message}")
        }
    }

    /**
     * Deletes a game's files and its DB record.
     */
    fun deleteGame(context: Context, game: GameEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete extracted folder
                val folder = File(game.localFolder)
                if (folder.exists()) {
                    folder.deleteRecursively()
                }
                // Delete ZIP file
                val zip = File(game.zipPath)
                if (zip.exists()) {
                    zip.delete()
                }
                repository.deleteGame(game)
            } catch (e: Exception) {
                Log.e("GameViewModel", "Failed to delete game files", e)
            }
        }
    }

    fun playOfflineGame(game: GameEntity) {
        _playingGame.value = game
    }

    fun stopPlaying() {
        _playingGame.value = null
    }
}

class GameViewModelFactory(private val repository: GameRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GameViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
