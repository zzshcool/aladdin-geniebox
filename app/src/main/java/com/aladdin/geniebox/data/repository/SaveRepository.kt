package com.aladdin.geniebox.data.repository

import com.aladdin.geniebox.data.db.dao.SaveMetadataDao
import com.aladdin.geniebox.data.db.entity.SaveMetadataEntity
import com.aladdin.geniebox.data.storage.StorageManager
import com.aladdin.geniebox.domain.model.PlatformType
import com.aladdin.geniebox.domain.model.SaveMetadata
import com.aladdin.geniebox.domain.model.SaveType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import com.aladdin.geniebox.data.db.dao.GameDao
import com.aladdin.geniebox.data.db.dao.romDocumentKey
import java.io.File
import java.io.DataInputStream

class SaveRepository(
    private val saveMetadataDao: SaveMetadataDao,
    private val storageManager: StorageManager,
    private val gameDao: GameDao
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun getSavesForGame(gameId: String): Flow<List<SaveMetadata>> =
        gameDao.observeIndexedGames().flatMapLatest { rows ->
            val document = rows.firstOrNull { it.entry.game.id == gameId }?.entry?.game?.documentUri
            val documentAliases = if (document == null) emptyList() else rows.filter {
                romDocumentKey(it.entry.game.documentUri) == romDocumentKey(document)
            }.map { it.entry.game.id }
            val aliases = listOf(gameId) + (documentAliases + storageManager.backupAliases(gameId))
                .filter { it != gameId }.distinct().sorted()
            gameDao.observeAliasSaves(aliases).map { saves ->
                saves.map { save ->
                    val alias = aliases.indexOf(save.gameId)
                    save.toDomain().copy(slotIndex = if (alias == 0 || save.slotIndex == 0) save.slotIndex
                        else alias * 100 + save.slotIndex)
                }
            }
        }.flowOn(Dispatchers.IO)

    fun getAvailableSavesForGame(gameId: String): Flow<List<SaveMetadata>> =
        getSavesForGame(gameId).map { saves ->
            saves.filter { save ->
                val file = File(save.filePath)
                val backup = File(file.parentFile, "${file.name}.bak")
                (file.isFile && file.canRead()) || (backup.isFile && backup.canRead())
            }
        }.flowOn(Dispatchers.IO)

    suspend fun saveState(
        gameId: String,
        coreId: String,
        coreVersion: String,
        slotIndex: Int,
        stateData: ByteArray,
        thumbnailPath: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val saveType = if (slotIndex == 0) SaveType.AUTO_RESUME else SaveType.MANUAL_STATE
        val dir = storageManager.getStateDir(coreId, gameId)
        val file = File(dir, "slot_${slotIndex}.state")

        val previous = if (slotIndex in 1..5) {
            getSavesForGame(gameId).first().firstOrNull {
                it.gameId == gameId && it.coreId == coreId && it.slotIndex == slotIndex
            }
        } else null
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
        storageManager.writeSaveState(file, stateData, previous, entity)
    }

    suspend fun loadStateData(gameId: String, coreId: String, expectedCoreVersion: String, slotIndex: Int, previous: Boolean = false): ByteArray? = withContext(Dispatchers.IO) {
        val entity = getAvailableSavesForGame(gameId).first().firstOrNull {
            it.slotIndex == slotIndex && it.coreId == coreId && (previous || it.coreVersion == expectedCoreVersion && it.formatVersion == 1)
        } ?: return@withContext null
        if (previous) {
            return@withContext try {
                DataInputStream(File("${entity.filePath}.previous").inputStream().buffered()).use {
                    if (it.readUTF() != expectedCoreVersion || it.readInt() != 1) return@withContext null
                    it.readLong()
                    val size = it.readInt()
                    if (size <= 0 || size.toLong() > File("${entity.filePath}.previous").length()) return@withContext null
                    ByteArray(size).also { bytes -> it.readFully(bytes) }
                }
            } catch (e: java.io.IOException) { null }
        }
        if (entity.formatVersion != 1) return@withContext null
        if (entity.coreVersion != expectedCoreVersion) return@withContext null
        val file = File(entity.filePath)
        storageManager.readBytes(file)
    }

    suspend fun previousStateTimestamp(filePath: String, coreVersion: String): Long? = withContext(Dispatchers.IO) {
        try {
            DataInputStream(File("$filePath.previous").inputStream().buffered()).use {
                if (it.readUTF() != coreVersion || it.readInt() != 1) null else it.readLong()
            }
        } catch (e: java.io.IOException) { null }
    }

    suspend fun saveSram(platform: PlatformType, gameId: String, sramData: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val dir = storageManager.getSramDir(platform, gameId)
        val file = File(dir, "save.srm")
        storageManager.atomicWrite(file, sramData)
    }

    suspend fun loadSram(platform: PlatformType, gameId: String): ByteArray? = withContext(Dispatchers.IO) {
        val dir = storageManager.getSramDir(platform, gameId)
        val file = File(dir, "save.srm")
        storageManager.readBytes(file) ?: run {
            val rows = gameDao.getIndexedGames()
            val document = rows.firstOrNull { it.entry.game.id == gameId }?.entry?.game?.documentUri
            val aliases = storageManager.backupAliases(gameId) + rows.filter {
                document != null && romDocumentKey(it.entry.game.documentUri) == romDocumentKey(document)
            }.map { it.entry.game.id }
            aliases.distinct().filter { it != gameId }.map { alias ->
                File(storageManager.getSramDir(platform, alias), "save.srm")
            }.sortedByDescending { it.lastModified() }.firstNotNullOfOrNull { storageManager.readBytes(it) }
        }
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
