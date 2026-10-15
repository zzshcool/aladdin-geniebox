package com.aladdin.geniebox.data.repository

import com.aladdin.geniebox.data.db.dao.SaveMetadataDao
import com.aladdin.geniebox.data.db.entity.SaveMetadataEntity
import com.aladdin.geniebox.data.storage.StorageManager
import com.aladdin.geniebox.domain.model.PlatformType
import com.aladdin.geniebox.domain.model.SaveMetadata
import com.aladdin.geniebox.domain.model.SaveType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

class SaveRepository(
    private val saveMetadataDao: SaveMetadataDao,
    private val storageManager: StorageManager
) {
    fun getSavesForGame(gameId: String): Flow<List<SaveMetadata>> =
        saveMetadataDao.getSavesForGame(gameId).map { list -> list.map { it.toDomain() } }

    suspend fun saveState(
        gameId: String,
        coreId: String,
        coreVersion: String,
        slotIndex: Int,
        stateData: ByteArray,
        thumbnailPath: String? = null
    ): Boolean {
        val saveType = if (slotIndex == 0) SaveType.AUTO_RESUME else SaveType.MANUAL_STATE
        val dir = storageManager.getStateDir(coreId, gameId)
        val file = File(dir, "slot_${slotIndex}.state")

        val ok = storageManager.atomicWrite(file, stateData)
        if (ok) {
            val entity = SaveMetadataEntity(
                id = "${gameId}_${coreId}_slot_${slotIndex}",
                gameId = gameId,
                coreId = coreId,
                coreVersion = coreVersion,
                formatVersion = 1,
                saveType = saveType.name,
                slotIndex = slotIndex,
                timestamp = System.currentTimeMillis(),
                filePath = file.absolutePath,
                thumbnailPath = thumbnailPath,
                sizeBytes = stateData.size.toLong()
            )
            saveMetadataDao.insertOrUpdateSave(entity)
        }
        return ok
    }

    suspend fun loadStateData(gameId: String, coreId: String, expectedCoreVersion: String, slotIndex: Int): ByteArray? {
        val saveType = if (slotIndex == 0) SaveType.AUTO_RESUME else SaveType.MANUAL_STATE
        val entity = saveMetadataDao.getSaveBySlot(gameId, coreId, saveType.name, slotIndex) ?: return null
        if (entity.formatVersion != 1) return null
        if (entity.coreVersion != expectedCoreVersion) return null
        val file = File(entity.filePath)
        return storageManager.readBytes(file)
    }

    suspend fun saveSram(platform: PlatformType, gameId: String, sramData: ByteArray): Boolean {
        val dir = storageManager.getSramDir(platform, gameId)
        val file = File(dir, "save.srm")
        return storageManager.atomicWrite(file, sramData)
    }

    fun loadSram(platform: PlatformType, gameId: String): ByteArray? {
        val dir = storageManager.getSramDir(platform, gameId)
        val file = File(dir, "save.srm")
        return storageManager.readBytes(file)
    }

    private fun SaveMetadataEntity.toDomain(): SaveMetadata {
        return SaveMetadata(
            id = id,
            gameId = gameId,
            coreId = coreId,
            coreVersion = coreVersion,
            formatVersion = formatVersion,
            saveType = try { SaveType.valueOf(saveType) } catch (e: Exception) { SaveType.MANUAL_STATE },
            slotIndex = slotIndex,
            timestamp = timestamp,
            filePath = filePath,
            thumbnailPath = thumbnailPath,
            sizeBytes = sizeBytes
        )
    }
}
