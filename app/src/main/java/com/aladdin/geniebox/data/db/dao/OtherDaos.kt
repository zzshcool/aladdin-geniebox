package com.aladdin.geniebox.data.db.dao

import androidx.room.*
import com.aladdin.geniebox.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RomSourceDao {
    @Query("SELECT * FROM rom_sources")
    fun getAllSources(): Flow<List<RomSourceEntity>>

    @Query("SELECT * FROM rom_sources WHERE id = :id LIMIT 1")
    suspend fun getSourceById(id: String): RomSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSource(source: RomSourceEntity)

    @Delete
    suspend fun deleteSource(source: RomSourceEntity)

    @Query("UPDATE rom_sources SET isConnected = :connected WHERE id = :id")
    suspend fun updateConnectionStatus(id: String, connected: Boolean)

    @Query("UPDATE rom_sources SET lastScannedTime = :timestamp WHERE id = :id")
    suspend fun updateLastScannedTime(id: String, timestamp: Long)
}

@Dao
interface FavoriteDao {
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE gameId = :gameId)")
    fun isFavorite(gameId: String): Flow<Boolean>

    @Query("SELECT * FROM favorites")
    suspend fun getAllFavoritesSync(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllFavorites(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE gameId = :gameId")
    suspend fun removeFavorite(gameId: String)
}

@Dao
interface PlayHistoryDao {
    @Query("SELECT * FROM play_history WHERE gameId = :gameId LIMIT 1")
    suspend fun getPlayHistory(gameId: String): PlayHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordPlay(history: PlayHistoryEntity)
}

@Dao
interface SaveMetadataDao {
    @Query("SELECT * FROM save_metadata WHERE gameId = :gameId ORDER BY timestamp DESC")
    fun getSavesForGame(gameId: String): Flow<List<SaveMetadataEntity>>

    @Query("SELECT * FROM save_metadata WHERE gameId = :gameId AND coreId = :coreId AND saveType = :saveType AND slotIndex = :slotIndex LIMIT 1")
    suspend fun getSaveBySlot(gameId: String, coreId: String, saveType: String, slotIndex: Int): SaveMetadataEntity?

    @Query("SELECT * FROM save_metadata")
    suspend fun getAllSavesSync(): List<SaveMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSave(save: SaveMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllSaves(saves: List<SaveMetadataEntity>)

    @Delete
    suspend fun deleteSave(save: SaveMetadataEntity)
}

@Dao
interface ControllerProfileDao {
    @Query("SELECT * FROM controller_profiles ORDER BY playerId ASC")
    fun getAllProfiles(): Flow<List<ControllerProfileEntity>>

    @Query("SELECT * FROM controller_profiles WHERE playerId = :playerId LIMIT 1")
    suspend fun getProfileByPlayer(playerId: Int): ControllerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: ControllerProfileEntity)
}
