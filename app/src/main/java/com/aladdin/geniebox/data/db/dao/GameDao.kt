package com.aladdin.geniebox.data.db.dao

import androidx.room.*
import com.aladdin.geniebox.data.db.entity.GameEntity
import kotlinx.coroutines.flow.Flow

data class GameWithFavorite(
    @Embedded val game: GameEntity,
    @ColumnInfo(name = "isFavorite") val isFavorite: Boolean
)

@Dao
interface GameDao {
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
