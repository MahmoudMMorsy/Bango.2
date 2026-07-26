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
    private val _currentUrl = MutableStateFlow("https://www.google.com")
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
            _downloadState.value = DownloadState.Progress("Initializing scraper...")
            try {
                val game = withContext(Dispatchers.IO) {
                    GameDownloader.downloadHtmlGameSource(context, url, title) { progress ->
                        _downloadState.value = DownloadState.Progress(progress)
                    }
                }
                repository.insertGame(game)
                _downloadState.value = DownloadState.Success(title)
            } catch (e: Exception) {
                Log.e("GameViewModel", "Download failed", e)
                _downloadState.value = DownloadState.Error(e.message ?: "Unknown download error")
            }
        }
    }

    /**
     * Downloads a ZIP file containing an HTML game and extracts it.
     */
    fun downloadZipGame(context: Context, zipUrl: String, title: String) {
        viewModelScope.launch {
            _downloadState.value = DownloadState.Progress("Downloading ZIP file...")
            try {
                val game = withContext(Dispatchers.IO) {
                    GameDownloader.downloadDirectZipGame(context, zipUrl, title) { progress ->
                        _downloadState.value = DownloadState.Progress(progress)
                    }
                }
                repository.insertGame(game)
                _downloadState.value = DownloadState.Success(title)
            } catch (e: Exception) {
                Log.e("GameViewModel", "ZIP Download failed", e)
                _downloadState.value = DownloadState.Error(e.message ?: "Unknown ZIP extraction error")
            }
        }
    }

    /**
     * Downloads a Flash (.swf) game.
     */
    fun downloadFlashGame(context: Context, swfUrl: String, title: String) {
        viewModelScope.launch {
            _downloadState.value = DownloadState.Progress("Connecting to SWF source...")
            try {
                val game = withContext(Dispatchers.IO) {
                    GameDownloader.downloadFlashGameSource(context, swfUrl, title) { progress ->
                        _downloadState.value = DownloadState.Progress(progress)
                    }
                }
                repository.insertGame(game)
                _downloadState.value = DownloadState.Success(title)
            } catch (e: Exception) {
                Log.e("GameViewModel", "Flash Download failed", e)
                _downloadState.value = DownloadState.Error(e.message ?: "Unknown Flash download error")
            }
        }
    }

    /**
     * Downloads any video, audio, or general file using HTTP range resume support.
     */
    fun downloadResumableMedia(context: Context, url: String, title: String, category: String = "VIDEO") {
        viewModelScope.launch {
            _downloadState.value = DownloadState.Progress("Connecting to media server...")
            try {
                val game = withContext(Dispatchers.IO) {
                    GameDownloader.downloadResumableFile(context, url, title, category) { progress, _, _ ->
                        _downloadState.value = DownloadState.Progress(progress)
                    }
                }
                repository.insertGame(game)
                _downloadState.value = DownloadState.Success(title)
            } catch (e: Exception) {
                Log.e("GameViewModel", "Resumable media download failed", e)
                _downloadState.value = DownloadState.Error(e.message ?: "Download interrupted or failed")
            }
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
