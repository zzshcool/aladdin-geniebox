package com.aladdin.geniebox.data.repository

import com.aladdin.geniebox.data.db.dao.FavoriteDao
import com.aladdin.geniebox.data.db.dao.GameDao
import com.aladdin.geniebox.data.db.dao.GameWithFavorite
import com.aladdin.geniebox.data.db.dao.PlayHistoryDao
import com.aladdin.geniebox.data.db.dao.preferredGame
import com.aladdin.geniebox.data.db.dao.romDocumentKey
import com.aladdin.geniebox.data.db.entity.FavoriteEntity
import com.aladdin.geniebox.data.db.entity.PlayHistoryEntity
import com.aladdin.geniebox.domain.model.EntryType
import com.aladdin.geniebox.domain.model.Game
import com.aladdin.geniebox.domain.model.PlatformType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine

class GameRepository(
    private val gameDao: GameDao,
    private val favoriteDao: FavoriteDao,
    private val playHistoryDao: PlayHistoryDao
) {
    fun getAllGames(): Flow<List<Game>> = gameDao.observeIndexedGames().map { rows ->
        rows.groupBy { romDocumentKey(it.entry.game.documentUri) }.values.map { group ->
            val preferred = group.preferredGame()
            val accessible = group.firstOrNull {
                it.entry.game.id == preferred.entry.game.id && it.entry.game.isAvailable
            } ?: group.firstOrNull { it.entry.game.isAvailable } ?: preferred
            val file = accessible.entry.game
            preferred.entry.toDomain().copy(
                sourceId = file.sourceId,
                relativePath = file.relativePath,
                documentUri = file.documentUri,
                isAvailable = group.any { it.entry.game.isAvailable },
                isFavorite = group.any { it.entry.isFavorite },
                lastPlayedTime = group.mapNotNull { it.lastPlayedTime }.maxOrNull()
            )
        }
    }

    fun getGamesByPlatform(platform: PlatformType): Flow<List<Game>> =
        getAllGames().map { games -> games.filter { it.platform == platform } }

    fun getFavoriteGames(): Flow<List<Game>> =
        combine(getAllGames(), gameDao.getFavoriteGames()) { games, favorites ->
            val byDocument = games.associateBy { romDocumentKey(it.documentUri) }
            favorites.mapNotNull { byDocument[romDocumentKey(it.game.documentUri)] }.distinctBy { it.id }
        }

    fun getRecentGames(): Flow<List<Game>> =
        getAllGames().map { games ->
            games.filter { it.lastPlayedTime != null }.sortedByDescending { it.lastPlayedTime }
        }

    fun searchGames(query: String): Flow<List<Game>> =
        combine(getAllGames(), gameDao.searchGames(query)) { games, matches ->
            val keys = matches.map { romDocumentKey(it.game.documentUri) }.toSet()
            games.filter { romDocumentKey(it.documentUri) in keys }
        }

    suspend fun getGameById(id: String): Game? = gameDao.getGameById(id)?.toDomain()

    suspend fun toggleFavorite(gameId: String, currentIsFavorite: Boolean) {
        val rows = gameDao.getIndexedGames()
        val target = rows.firstOrNull { it.entry.game.id == gameId }
        val aliases = if (target == null) listOf(gameId) else {
            val key = romDocumentKey(target.entry.game.documentUri)
            rows.filter { romDocumentKey(it.entry.game.documentUri) == key }.map { it.entry.game.id }
        }
        if (currentIsFavorite) {
            gameDao.removeDocumentFavorites(aliases)
        } else {
            gameDao.addDocumentFavorites(aliases.map { FavoriteEntity(gameId = it) })
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
