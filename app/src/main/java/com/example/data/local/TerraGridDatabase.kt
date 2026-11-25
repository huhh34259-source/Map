package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        OfflineRegionEntity::class,
        CachedTileEntity::class,
        WaypointEntity::class,
        TrackPointEntity::class,
        FieldIntelEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TerraGridDatabase : RoomDatabase() {
    abstract fun dao(): TerraGridDao

    companion object {
        @Volatile
        private var INSTANCE: TerraGridDatabase? = null

        fun getInstance(context: Context): TerraGridDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TerraGridDatabase::class.java,
                    "terragrid_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
