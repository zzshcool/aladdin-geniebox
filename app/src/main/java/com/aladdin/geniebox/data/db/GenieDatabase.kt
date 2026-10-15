package com.aladdin.geniebox.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.aladdin.geniebox.data.db.dao.*
import com.aladdin.geniebox.data.db.entity.*

@Database(
    entities = [
        GameEntity::class,
        RomSourceEntity::class,
        FavoriteEntity::class,
        PlayHistoryEntity::class,
        SaveMetadataEntity::class,
        ControllerProfileEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class GenieDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun romSourceDao(): RomSourceDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playHistoryDao(): PlayHistoryDao
    abstract fun saveMetadataDao(): SaveMetadataDao
    abstract fun controllerProfileDao(): ControllerProfileDao

    companion object {
        @Volatile
        private var INSTANCE: GenieDatabase? = null

        fun getInstance(context: Context): GenieDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GenieDatabase::class.java,
                    "aladdin_geniebox.db"
                )
                // 嚴禁 fallbackToDestructiveMigration()，保護使用者的收藏與歷史記錄
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
