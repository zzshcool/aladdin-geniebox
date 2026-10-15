package com.aladdin.geniebox.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "games",
    indices = [
        Index("platform"),
        Index("sourceId"),
        Index("title"),
        Index("pinyinIndex"),
        Index("zhuyinIndex")
    ]
)
data class GameEntity(
    @PrimaryKey val id: String,
    val title: String,
    val platform: String,             // NES, GB, GBA, SFC, PS1, PSP
    val sourceId: String,
    val relativePath: String,
    val documentUri: String,
    val entryType: String,            // FILE, ZIP, CUE_BIN, M3U_DISC_SET
    val sizeBytes: Long,
    val lastModified: Long,
    val coverUri: String?,
    val pinyinIndex: String,
    val zhuyinIndex: String,
    val isAvailable: Boolean
)
