package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.*
import com.example.data.local.entities.*

@Database(
    entities = [
        ItemEntity::class,
        LocationEntity::class,
        ItemLocationHistoryEntity::class,
        ItemObservationEntity::class,
        ItemImageEntity::class,
        CustomFieldEntity::class,
        ItemRelationshipEntity::class,
        TroubleshootingEventEntity::class,
        DocumentEntity::class,
        AiMemoryEntity::class,
        AiJobEntity::class,
        ChatMessageEntity::class,
        MaintenanceTaskEntity::class,
        ItemIntelligenceProfileEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class HomeAiDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun locationDao(): LocationDao
    abstract fun itemObservationDao(): ItemObservationDao
    abstract fun itemLocationHistoryDao(): ItemLocationHistoryDao
    abstract fun itemImageDao(): ItemImageDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun itemRelationshipDao(): ItemRelationshipDao
    abstract fun troubleshootingDao(): TroubleshootingDao
    abstract fun documentDao(): DocumentDao
    abstract fun aiMemoryDao(): AiMemoryDao
    abstract fun aiJobDao(): AiJobDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun maintenanceTaskDao(): MaintenanceTaskDao
    abstract fun itemIntelligenceProfileDao(): ItemIntelligenceProfileDao

    companion object {
        @Volatile
        private var INSTANCE: HomeAiDatabase? = null

        fun getDatabase(context: Context): HomeAiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    HomeAiDatabase::class.java,
                    "home_ai.db"
                ).fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
