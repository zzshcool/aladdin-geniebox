package com.aladdin.geniebox.data.db.dao

import androidx.room.*
import android.net.Uri
import android.provider.DocumentsContract
import com.aladdin.geniebox.data.db.entity.GameEntity
import com.aladdin.geniebox.data.db.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

data class GameWithFavorite(
    @Embedded val game: GameEntity,
    @ColumnInfo(name = "isFavorite") val isFavorite: Boolean
)

data class IndexedGame(
    @Embedded val entry: GameWithFavorite,
    val lastPlayedTime: Long?,
    val lastSavedTime: Long?
)

internal fun romDocumentKey(documentUri: String): String {
    val uri = Uri.parse(documentUri)
    return try {
        "${uri.scheme}://${uri.authority}/${DocumentsContract.getDocumentId(uri)}"
    } catch (_: IllegalArgumentException) {
        documentUri
    }
}

internal fun List<IndexedGame>.preferredGame(): IndexedGame = maxWith(
    compareBy<IndexedGame> { maxOf(it.lastSavedTime ?: Long.MIN_VALUE, it.lastPlayedTime ?: Long.MIN_VALUE) }
        .thenBy { it.lastSavedTime ?: Long.MIN_VALUE }
        .thenBy { it.lastPlayedTime ?: Long.MIN_VALUE }
        .thenByDescending { it.entry.game.id }
)

private const val INDEXED_GAMES_QUERY = """
    SELECT g.*, (f.gameId IS NOT NULL) AS isFavorite,
           p.lastPlayedTime AS lastPlayedTime, s.lastSavedTime AS lastSavedTime
    FROM games g
    LEFT JOIN favorites f ON g.id = f.gameId
    LEFT JOIN play_history p ON g.id = p.gameId
    LEFT JOIN (
        SELECT gameId, MAX(timestamp) AS lastSavedTime FROM save_metadata GROUP BY gameId
    ) s ON g.id = s.gameId
    ORDER BY g.title ASC, g.id ASC
"""

@Dao
interface GameDao {
    @Query(INDEXED_GAMES_QUERY)
    fun observeIndexedGames(): Flow<List<IndexedGame>>

    @Query(INDEXED_GAMES_QUERY)
    suspend fun getIndexedGames(): List<IndexedGame>

    @Transaction
    suspend fun upsertScannedGames(games: List<GameEntity>) {
        // 保留已有進度的 ID；不同授權樹指向同一文件時，不建立另一份遊戲索引。
        val existing = getIndexedGames().groupBy { romDocumentKey(it.entry.game.documentUri) }
            .mapValues { (_, rows) -> rows.preferredGame() }.toMutableMap()
        val updates = linkedMapOf<String, GameEntity>()
        for (game in games) {
            val key = romDocumentKey(game.documentUri)
            val previous = existing[key]
            val owner = previous?.entry?.game
            val updated = if (owner == null) game else game.copy(
                id = owner.id,
                sourceId = if (owner.isAvailable) owner.sourceId else game.sourceId,
                relativePath = if (owner.isAvailable) owner.relativePath else game.relativePath,
                documentUri = if (owner.isAvailable) owner.documentUri else game.documentUri
            )
            updates[updated.id] = updated
            existing[key] = IndexedGame(
                GameWithFavorite(updated, previous?.entry?.isFavorite ?: false),
                previous?.lastPlayedTime, previous?.lastSavedTime
            )
        }
        updates.values.chunked(100).forEach { insertOrUpdateGames(it) }
    }

    @Query("DELETE FROM favorites WHERE gameId IN (:gameIds)")
    suspend fun removeDocumentFavorites(gameIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDocumentFavorites(favorites: List<FavoriteEntity>)

    @Query("""
        SELECT g.*, (f.gameId IS NOT NULL) AS isFavorite
        FROM games g
        LEFT JOIN favorites f ON g.id = f.gameId
        ORDER BY g.title ASC
    """)
    fun getAllGames(): Flow<List<GameWithFavorite>>

    @Query("""
        SELECT g.*, (f.gameId IS NOT NULL) AS isFavorite
        FROM games g
        LEFT JOIN favorites f ON g.id = f.gameId
        WHERE g.platform = :platform
        ORDER BY g.title ASC
    """)
    fun getGamesByPlatform(platform: String): Flow<List<GameWithFavorite>>

    @Query("""
        SELECT g.*, 1 AS isFavorite
        FROM games g
        INNER JOIN favorites f ON g.id = f.gameId
        ORDER BY f.addedTime DESC
    """)
    fun getFavoriteGames(): Flow<List<GameWithFavorite>>

    @Query("""
        SELECT g.*, (f.gameId IS NOT NULL) AS isFavorite
        FROM games g
        INNER JOIN play_history p ON g.id = p.gameId
        LEFT JOIN favorites f ON g.id = f.gameId
        ORDER BY p.lastPlayedTime DESC
    """)
    fun getRecentGames(): Flow<List<GameWithFavorite>>

    @Query("""
        SELECT g.*, (f.gameId IS NOT NULL) AS isFavorite
        FROM games g
        LEFT JOIN favorites f ON g.id = f.gameId
        WHERE g.title LIKE '%' || :query || '%' 
           OR g.pinyinIndex LIKE '%' || :query || '%' 
           OR g.zhuyinIndex LIKE '%' || :query || '%'
        ORDER BY g.title ASC
    """)
    fun searchGames(query: String): Flow<List<GameWithFavorite>>

    @Query("""
        SELECT g.*, (f.gameId IS NOT NULL) AS isFavorite
        FROM games g
        LEFT JOIN favorites f ON g.id = f.gameId
        WHERE g.id = :id LIMIT 1
    """)
    suspend fun getGameById(id: String): GameWithFavorite?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateGames(games: List<GameEntity>)

    @Query("UPDATE games SET isAvailable = :isAvailable WHERE sourceId = :sourceId")
    suspend fun updateAvailabilityBySource(sourceId: String, isAvailable: Boolean)

    @Query("DELETE FROM games WHERE sourceId = :sourceId")
    suspend fun deleteGamesBySource(sourceId: String)

    @Query("SELECT COUNT(*) FROM games")
    suspend fun getTotalGameCount(): Int
}
