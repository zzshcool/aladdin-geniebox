package com.aladdin.geniebox.data.repository

import com.aladdin.geniebox.data.db.dao.GameDao
import com.aladdin.geniebox.data.db.dao.RomSourceDao
import com.aladdin.geniebox.data.db.entity.RomSourceEntity
import com.aladdin.geniebox.domain.model.RomSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RomSourceRepository(
    private val romSourceDao: RomSourceDao,
    private val gameDao: GameDao
) {
    fun getAllSources(): Flow<List<RomSource>> = romSourceDao.getAllSources().map { list ->
        list.map {
            RomSource(
                id = it.id,
                displayName = it.displayName,
                treeUri = it.treeUri,
                isConnected = it.isConnected,
                lastScannedTime = it.lastScannedTime
            )
        }
    }

    suspend fun getSource(sourceId: String): RomSource? {
        val entity = romSourceDao.getSourceById(sourceId) ?: return null
        return RomSource(
            id = entity.id,
            displayName = entity.displayName,
            treeUri = entity.treeUri,
            isConnected = entity.isConnected,
            lastScannedTime = entity.lastScannedTime
        )
    }

    suspend fun registerSource(id: String, displayName: String, treeUri: String) {
        val entity = RomSourceEntity(
            id = id,
            displayName = displayName,
            treeUri = treeUri,
            isConnected = true,
            lastScannedTime = 0L
        )
        romSourceDao.insertOrUpdateSource(entity)
    }

    suspend fun setSourceConnected(sourceId: String, connected: Boolean) {
        romSourceDao.updateConnectionStatus(sourceId, connected)
        gameDao.updateAvailabilityBySource(sourceId, connected)
    }

    suspend fun removeSource(sourceId: String) {
        val src = romSourceDao.getSourceById(sourceId)
        if (src != null) {
            romSourceDao.deleteSource(src)
            // 依規格第 13 節：移除遊戲來源不連帶刪除存檔，但從遊戲庫移除該來源項目
            gameDao.deleteGamesBySource(sourceId)
        }
    }
}
