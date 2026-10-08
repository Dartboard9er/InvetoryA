package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE status != 'ARCHIVED' ORDER BY updatedAt DESC")
    fun getAllActiveItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY updatedAt DESC")
    fun getAllItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE status = 'ARCHIVED' ORDER BY updatedAt DESC")
    fun getArchivedItems(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    fun observeItemById(id: String): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE currentLocationId = :locationId AND status != 'ARCHIVED' ORDER BY name ASC")
    fun getItemsByLocation(locationId: String): Flow<List<ItemEntity>>

    @Query("""
        SELECT * FROM items 
        WHERE status != 'ARCHIVED' AND (
            name LIKE '%' || :query || '%' OR 
            brand LIKE '%' || :query || '%' OR 
            model LIKE '%' || :query || '%' OR 
            modelNumber LIKE '%' || :query || '%' OR 
            serialNumber LIKE '%' || :query || '%' OR 
            category LIKE '%' || :query || '%' OR 
            description LIKE '%' || :query || '%' OR 
            notes LIKE '%' || :query || '%'
        )
        ORDER BY updatedAt DESC
    """)
    fun searchItems(query: String): Flow<List<ItemEntity>>

    @Query("""
        SELECT * FROM items 
        WHERE status != 'ARCHIVED' AND (
            name LIKE '%' || :query || '%' OR 
            brand LIKE '%' || :query || '%' OR 
            model LIKE '%' || :query || '%' OR 
            modelNumber LIKE '%' || :query || '%' OR 
            serialNumber LIKE '%' || :query || '%'
        )
    """)
    suspend fun findMatchingCandidates(query: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE serialNumber IS NOT NULL AND serialNumber = :serialNumber LIMIT 1")
    suspend fun findBySerialNumber(serialNumber: String): ItemEntity?

    @Query("SELECT * FROM items WHERE modelNumber IS NOT NULL AND modelNumber = :modelNumber LIMIT 5")
    suspend fun findByModelNumber(modelNumber: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE barcode IS NOT NULL AND barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): ItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity)

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("SELECT COUNT(*) FROM items WHERE status != 'ARCHIVED'")
    fun getActiveItemCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun getTotalItemCount(): Int

    @Query("SELECT * FROM items WHERE warrantyEnd IS NOT NULL AND warrantyEnd > :now ORDER BY warrantyEnd ASC")
    fun getUpcomingWarranties(now: Long = System.currentTimeMillis()): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE warrantyEnd IS NOT NULL AND warrantyEnd <= :now ORDER BY warrantyEnd DESC")
    fun getExpiredWarranties(now: Long = System.currentTimeMillis()): Flow<List<ItemEntity>>
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY name ASC")
    fun getAllLocations(): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations ORDER BY name ASC")
    suspend fun getLocationsList(): List<LocationEntity>

    @Query("SELECT * FROM locations WHERE id = :id LIMIT 1")
    suspend fun getLocationById(id: String): LocationEntity?

    @Query("SELECT * FROM locations WHERE id = :id LIMIT 1")
    fun observeLocationById(id: String): Flow<LocationEntity?>

    @Query("SELECT * FROM locations WHERE parentLocationId = :parentId ORDER BY name ASC")
    fun getSubLocations(parentId: String): Flow<List<LocationEntity>>

    @Query("SELECT * FROM locations WHERE parentLocationId IS NULL ORDER BY name ASC")
    fun getRootLocations(): Flow<List<LocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationEntity)

    @Update
    suspend fun updateLocation(location: LocationEntity)

    @Query("DELETE FROM locations WHERE id = :id")
    suspend fun deleteLocationById(id: String)

    @Query("UPDATE locations SET lastScannedAt = :timestamp WHERE id = :locationId")
    suspend fun updateLastScanned(locationId: String, timestamp: Long = System.currentTimeMillis())
}

@Dao
interface ItemObservationDao {
    @Query("SELECT * FROM item_observations WHERE itemId = :itemId ORDER BY timestamp DESC")
    fun getObservationsForItem(itemId: String): Flow<List<ItemObservationEntity>>

    @Query("SELECT * FROM item_observations WHERE itemId = :itemId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestObservation(itemId: String): ItemObservationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertObservation(observation: ItemObservationEntity)
}

@Dao
interface ItemLocationHistoryDao {
    @Query("SELECT * FROM item_location_history WHERE itemId = :itemId ORDER BY timestamp DESC")
    fun getHistoryForItem(itemId: String): Flow<List<ItemLocationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: ItemLocationHistoryEntity)
}

@Dao
interface ItemImageDao {
    @Query("SELECT * FROM item_images WHERE itemId = :itemId ORDER BY timestamp DESC")
    fun getImagesForItem(itemId: String): Flow<List<ItemImageEntity>>

    @Query("SELECT * FROM item_images WHERE itemId = :itemId AND imageType = 'PRIMARY' LIMIT 1")
    suspend fun getPrimaryImage(itemId: String): ItemImageEntity?

    @Query("SELECT * FROM item_images WHERE contentHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): ItemImageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ItemImageEntity)

    @Query("DELETE FROM item_images WHERE id = :id")
    suspend fun deleteImageById(id: String)
}

@Dao
interface CustomFieldDao {
    @Query("SELECT * FROM custom_fields WHERE itemId = :itemId ORDER BY fieldName ASC")
    fun getFieldsForItem(itemId: String): Flow<List<CustomFieldEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertField(field: CustomFieldEntity)

    @Query("DELETE FROM custom_fields WHERE id = :id")
    suspend fun deleteField(id: String)
}

@Dao
interface ItemRelationshipDao {
    @Query("SELECT * FROM item_relationships WHERE sourceItemId = :itemId OR targetItemId = :itemId")
    fun getRelationshipsForItem(itemId: String): Flow<List<ItemRelationshipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelationship(relationship: ItemRelationshipEntity)

    @Query("DELETE FROM item_relationships WHERE id = :id")
    suspend fun deleteRelationship(id: String)
}

@Dao
interface TroubleshootingDao {
    @Query("SELECT * FROM troubleshooting_events WHERE itemId = :itemId ORDER BY timestamp DESC")
    fun getEventsForItem(itemId: String): Flow<List<TroubleshootingEventEntity>>

    @Query("""
        SELECT * FROM troubleshooting_events 
        WHERE problem LIKE '%' || :query || '%' OR symptoms LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    suspend fun searchTroubleshooting(query: String): List<TroubleshootingEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TroubleshootingEventEntity)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE itemId = :itemId ORDER BY downloadedAt DESC")
    fun getDocumentsForItem(itemId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)
}

@Dao
interface AiMemoryDao {
    @Query("SELECT * FROM ai_memories WHERE itemId = :itemId ORDER BY createdAt DESC")
    fun getMemoriesForItem(itemId: String): Flow<List<AiMemoryEntity>>

    @Query("SELECT * FROM ai_memories ORDER BY importance DESC, createdAt DESC LIMIT 50")
    suspend fun getAllImportantMemories(): List<AiMemoryEntity>

    @Query("SELECT * FROM ai_memories WHERE embeddingVector IS NOT NULL")
    suspend fun getAllEmbeddedMemories(): List<AiMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: AiMemoryEntity)
}

@Dao
interface AiJobDao {
    @Query("SELECT * FROM ai_jobs WHERE status IN ('PENDING', 'RETRYING', 'PROCESSING') ORDER BY createdAt ASC")
    fun getPendingJobs(): Flow<List<AiJobEntity>>

    @Query("SELECT * FROM ai_jobs ORDER BY createdAt DESC LIMIT 20")
    fun getRecentJobs(): Flow<List<AiJobEntity>>

    @Query("SELECT * FROM ai_jobs WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPendingJob(): AiJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: AiJobEntity)

    @Update
    suspend fun updateJob(job: AiJobEntity)

    @Query("DELETE FROM ai_jobs WHERE id = :id")
    suspend fun deleteJob(id: String)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE conversationId = :convId ORDER BY timestamp ASC")
    fun getMessagesForConversation(convId: String = "default"): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE conversationId = :convId")
    suspend fun clearMessages(convId: String = "default")

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}

@Dao
interface MaintenanceTaskDao {
    @Query("SELECT * FROM maintenance_tasks ORDER BY nextDueDate ASC")
    fun getAllTasks(): Flow<List<MaintenanceTaskEntity>>

    @Query("SELECT * FROM maintenance_tasks WHERE isCompleted = 0 AND nextDueDate < :now ORDER BY nextDueDate ASC")
    fun getOverdueTasks(now: Long = System.currentTimeMillis()): Flow<List<MaintenanceTaskEntity>>

    @Query("SELECT * FROM maintenance_tasks WHERE isCompleted = 0 AND nextDueDate >= :now ORDER BY nextDueDate ASC")
    fun getUpcomingTasks(now: Long = System.currentTimeMillis()): Flow<List<MaintenanceTaskEntity>>

    @Query("SELECT * FROM maintenance_tasks WHERE itemId = :itemId ORDER BY nextDueDate ASC")
    fun getTasksForItem(itemId: String): Flow<List<MaintenanceTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: MaintenanceTaskEntity)

    @Update
    suspend fun updateTask(task: MaintenanceTaskEntity)

    @Query("DELETE FROM maintenance_tasks WHERE id = :id")
    suspend fun deleteTask(id: String)
}

@Dao
interface ItemIntelligenceProfileDao {
    @Query("SELECT * FROM item_intelligence_profiles WHERE itemId = :itemId LIMIT 1")
    fun observeProfile(itemId: String): Flow<ItemIntelligenceProfileEntity?>

    @Query("SELECT * FROM item_intelligence_profiles WHERE itemId = :itemId LIMIT 1")
    suspend fun getProfile(itemId: String): ItemIntelligenceProfileEntity?

    @Query("SELECT * FROM item_intelligence_profiles WHERE researchStatus = 'KNOWLEDGE_READY'")
    fun getAllReadyProfiles(): Flow<List<ItemIntelligenceProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ItemIntelligenceProfileEntity)
}
