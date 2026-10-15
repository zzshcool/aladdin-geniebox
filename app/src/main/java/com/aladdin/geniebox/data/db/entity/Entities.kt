package com.aladdin.geniebox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rom_sources")
data class RomSourceEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val treeUri: String,
    val isConnected: Boolean,
    val lastScannedTime: Long
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val gameId: String,
    val addedTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey val gameId: String,
    val lastPlayedTime: Long = System.currentTimeMillis(),
    val totalPlaySeconds: Long = 0L,
    val lastUsedCoreId: String? = null
)

@Entity(tableName = "save_metadata")
data class SaveMetadataEntity(
    @PrimaryKey val id: String,
    val gameId: String,
    val coreId: String,
    val coreVersion: String,
    val formatVersion: Int,
    val saveType: String,            // NATIVE_SRAM, MANUAL_STATE, AUTO_RESUME
    val slotIndex: Int,
    val timestamp: Long,
    val filePath: String,
    val thumbnailPath: String?,
    val sizeBytes: Long
)

@Entity(tableName = "controller_profiles")
data class ControllerProfileEntity(
    @PrimaryKey val playerId: Int,    // 1, 2, 3, 4
    val deviceDescriptor: String,
    val deviceName: String,
    val deadzone: Float,
    val buttonMappingJson: String,   // JSON 序列化之按鍵映射
    val vibrationEnabled: Boolean
)
