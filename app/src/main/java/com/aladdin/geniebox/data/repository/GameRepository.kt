package com.aladdin.geniebox.data.repository

import com.aladdin.geniebox.data.db.dao.FavoriteDao
import com.aladdin.geniebox.data.db.dao.GameDao
import com.aladdin.geniebox.data.db.dao.GameWithFavorite
import com.aladdin.geniebox.data.db.dao.PlayHistoryDao
import com.aladdin.geniebox.data.db.entity.FavoriteEntity
import com.aladdin.geniebox.data.db.entity.PlayHistoryEntity
import com.aladdin.geniebox.domain.model.EntryType
import com.aladdin.geniebox.domain.model.Game
import com.aladdin.geniebox.domain.model.PlatformType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameRepository(
    private val gameDao: GameDao,
    private val favoriteDao: FavoriteDao,
    private val playHistoryDao: PlayHistoryDao
) {
    fun getAllGames(): Flow<List<Game>> = gameDao.getAllGames().map { list -> list.map { it.toDomain() } }

    fun getGamesByPlatform(platform: PlatformType): Flow<List<Game>> =
        gameDao.getGamesByPlatform(platform.id).map { list -> list.map { it.toDomain() } }

    fun getFavoriteGames(): Flow<List<Game>> =
        gameDao.getFavoriteGames().map { list -> list.map { it.toDomain() } }

    fun getRecentGames(): Flow<List<Game>> =
        gameDao.getRecentGames().map { list -> list.map { it.toDomain() } }

    fun searchGames(query: String): Flow<List<Game>> =
        gameDao.searchGames(query).map { list -> list.map { it.toDomain() } }

    suspend fun getGameById(id: String): Game? = gameDao.getGameById(id)?.toDomain()

    suspend fun toggleFavorite(gameId: String, currentIsFavorite: Boolean) {
        if (currentIsFavorite) {
            favoriteDao.removeFavorite(gameId)
        } else {
            favoriteDao.addFavorite(FavoriteEntity(gameId = gameId))
        }
    }

    suspend fun recordGamePlay(gameId: String, coreId: String) {
        val existing = playHistoryDao.getPlayHistory(gameId)
        val history = PlayHistoryEntity(
            gameId = gameId,
            lastPlayedTime = System.currentTimeMillis(),
            totalPlaySeconds = (existing?.totalPlaySeconds ?: 0L) + 1L,
            lastUsedCoreId = coreId
        )
        playHistoryDao.recordPlay(history)
    }

    private fun GameWithFavorite.toDomain(): Game {
        val g = this.game
        return Game(
            id = g.id,
            title = g.title,
            platform = PlatformType.fromId(g.platform) ?: PlatformType.NES,
            sourceId = g.sourceId,
            relativePath = g.relativePath,
            documentUri = g.documentUri,
            entryType = try { EntryType.valueOf(g.entryType) } catch (e: Exception) { EntryType.FILE },
            sizeBytes = g.sizeBytes,
            lastModified = g.lastModified,
            coverUri = g.coverUri,
            pinyinIndex = g.pinyinIndex,
            zhuyinIndex = g.zhuyinIndex,
            isAvailable = g.isAvailable,
            isFavorite = this.isFavorite
        )
    }
}
