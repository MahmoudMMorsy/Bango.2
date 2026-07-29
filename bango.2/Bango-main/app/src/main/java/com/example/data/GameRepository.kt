package com.example.data

import kotlinx.coroutines.flow.Flow

class GameRepository(private val gameDao: GameDao) {

    val allGames: Flow<List<GameEntity>> = gameDao.getAllGames()
    val allBookmarks: Flow<List<BookmarkEntity>> = gameDao.getAllBookmarks()
    val recentHistory: Flow<List<HistoryEntity>> = gameDao.getRecentHistory()

    suspend fun insertGame(game: GameEntity): Long = gameDao.insertGame(game)

    suspend fun updateGame(game: GameEntity) = gameDao.updateGame(game)

    suspend fun deleteGame(game: GameEntity) = gameDao.deleteGame(game)

    suspend fun getGameById(id: Int): GameEntity? = gameDao.getGameById(id)

    suspend fun insertBookmark(bookmark: BookmarkEntity) = gameDao.insertBookmark(bookmark)

    suspend fun deleteBookmark(bookmark: BookmarkEntity) = gameDao.deleteBookmark(bookmark)

    fun isBookmarked(url: String): Flow<Boolean> = gameDao.isBookmarked(url)

    suspend fun deleteBookmarkByUrl(url: String) = gameDao.deleteBookmarkByUrl(url)

    suspend fun insertHistory(history: HistoryEntity) = gameDao.insertHistory(history)

    suspend fun deleteHistoryById(id: Int) = gameDao.deleteHistoryById(id)

    suspend fun clearHistory() = gameDao.clearHistory()
}
