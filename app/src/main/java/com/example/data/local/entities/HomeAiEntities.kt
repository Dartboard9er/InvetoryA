package com.example.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val normalizedName: String,
    val brand: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val modelNumber: String? = null,
    val serialNumber: String? = null,
    val barcode: String? = null,
    val category: String = "General",
    val subcategory: String? = null,
    val quantity: Int = 1,
    val condition: String? = null,
    val description: String? = null,
    val currentLocationId: String? = null,
    val purchaseDate: Long? = null,
    val purchasePrice: Double? = null,
    val purchaseCurrency: String? = "USD",
    val retailer: String? = null,
    val returnWindowEnd: Long? = null,
    val warrantyStart: Long? = null,
    val warrantyEnd: Long? = null,
    val warrantyProvider: String? = null,
    val notes: String? = null,
    val aiSummary: String? = null,
    val aiConfidence: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastScannedAt: Long = System.currentTimeMillis(),
    val status: String = "ACTIVE" // ACTIVE, MISSING, ARCHIVED, DISPOSED, SOLD, UNKNOWN
)

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val parentLocationId: String? = null,
    val type: String = "ROOM", // PROPERTY, BUILDING, ROOM, AREA, WORKBENCH, CABINET, DRAWER, SHELF, BIN, CONTAINER, OTHER
    val description: String? = null,
    val lastScannedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val notes: String? = null
)

@Entity(tableName = "item_location_history")
data class ItemLocationHistoryEntity(
    @PrimaryKey
    val id: String,
    val itemId: String,
    val locationId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "USER_CONFIRMED", // USER_CONFIRMED, CAMERA_SCAN, ROOM_SWEEP, AI_INFERRED, IMPORTED
    val confidence: Float = 1.0f,
    val observationId: String? = null,
    val note: String? = null
)

@Entity(tableName = "item_observations")
data class ItemObservationEntity(
    @PrimaryKey
    val id: String,
    val itemId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val imagePath: String,
    val locationId: String? = null,
    val description: String? = null,
    val surroundings: String? = null,
    val visibleText: String? = null, // JSON array string
    val detectedNearbyItems: String? = null, // JSON array string
    val confidence: Float = 1.0f,
    val source: String = "CAMERA_SCAN" // CAMERA_SCAN, ROOM_SWEEP, MANUAL_ENTRY
)

@Entity(tableName = "item_images")
data class ItemImageEntity(
    @PrimaryKey
    val id: String,
    val itemId: String,
    val localPath: String,
    val thumbnailPath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val imageType: String = "PRIMARY", // PRIMARY, LABEL, SERIAL_NUMBER, MODEL_NUMBER, RECEIPT, ACCESSORY, LOCATION_CONTEXT, OTHER
    val source: String = "CAMERA",
    val contentHash: String? = null
)

@Entity(tableName = "custom_fields")
data class CustomFieldEntity(
    @PrimaryKey
    val id: String,
    val itemId: String,
    val fieldName: String,
    val fieldValue: String,
    val fieldType: String = "TEXT",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "item_relationships")
data class ItemRelationshipEntity(
    @PrimaryKey
    val id: String,
    val sourceItemId: String,
    val relationshipType: String, // USES, USED_WITH, MOUNTED_WITH, CONNECTED_TO, ACCESSORY_OF, PART_OF, STORED_WITH, REQUIRES, REPLACED_BY, RELATED_TO
    val targetItemId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String? = null
)

@Entity(tableName = "troubleshooting_events")
data class TroubleshootingEventEntity(
    @PrimaryKey
    val id: String,
    val itemId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val problem: String,
    val symptoms: String? = null,
    val suggestedActions: String? = null,
    val actionsTaken: String? = null,
    val result: String = "SUCCESS", // UNKNOWN, SUCCESS, PARTIAL, FAILED
    val successfulSolution: String? = null,
    val source: String = "ASSISTANT",
    val notes: String? = null
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey
    val id: String,
    val itemId: String,
    val name: String,
    val localPath: String,
    val sourceUrl: String? = null,
    val sourceDomain: String? = null,
    val documentType: String = "MANUAL", // MANUAL, QUICK_START, INSTALLATION, WARRANTY, SPECIFICATION, SUPPORT, RECEIPT, OTHER
    val manufacturer: String? = null,
    val discoveredAt: Long = System.currentTimeMillis(),
    val downloadedAt: Long = System.currentTimeMillis(),
    val checksum: String? = null,
    val summary: String? = null,
    val notes: String? = null
)

@Entity(tableName = "ai_memories")
data class AiMemoryEntity(
    @PrimaryKey
    val id: String,
    val type: String, // FACT, PREFERENCE, TROUBLESHOOTING, LOCATION, RELATIONSHIP, OBSERVATION, CONVERSATION_SUMMARY, DOCUMENT_SUMMARY
    val itemId: String? = null,
    val locationId: String? = null,
    val content: String,
    val importance: Float = 0.5f,
    val source: String = "CONVERSATION", // USER_PROVIDED, OBSERVED, INFERRED, DOCUMENT_VERIFIED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val embeddingVector: String? = null // Comma-separated floats for local cosine search
)

@Entity(tableName = "ai_jobs")
data class AiJobEntity(
    @PrimaryKey
    val id: String,
    val type: String, // SCAN_PROCESSING, GEMINI_ANALYSIS, EMBEDDING_GENERATION, MANUAL_DISCOVERY, MANUAL_PROCESSING, WARRANTY_AUDIT, STALE_LOCATION_AUDIT
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING", // PENDING, PROCESSING, COMPLETED, FAILED, RETRYING
    val retryCount: Int = 0,
    val itemId: String? = null,
    val scanId: String? = null,
    val documentId: String? = null,
    val payloadPath: String? = null,
    val errorMessage: String? = null,
    val lastAttemptAt: Long? = null
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String = "default",
    val role: String, // USER, ASSISTANT, SYSTEM
    val text: String,
    val referencedItemIds: String? = null, // JSON array string
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "maintenance_tasks")
data class MaintenanceTaskEntity(
    @PrimaryKey
    val id: String,
    val itemId: String,
    val title: String,
    val description: String? = null,
    val intervalType: String = "DATE_BASED", // DATE_BASED, USAGE_BASED, SEASONAL, BEFORE_USE, AS_NEEDED
    val intervalDays: Int? = 90,
    val nextDueDate: Long = System.currentTimeMillis() + (90L * 24 * 60 * 60 * 1000),
    val lastCompletedDate: Long? = null,
    val source: String = "MANUFACTURER_MANUAL", // MANUFACTURER_MANUAL, USER_DEFINED, AI_SUGGESTION
    val requiredSupplyItemId: String? = null,
    val isCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "item_intelligence_profiles")
data class ItemIntelligenceProfileEntity(
    @PrimaryKey
    val itemId: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val researchStatus: String = "KNOWLEDGE_READY", // NOT_RESEARCHED, RESEARCHING, KNOWLEDGE_READY, NEEDS_REVIEW, FAILED
    val officialProductPage: String? = null,
    val manualAvailable: Boolean = false,
    val manualUrl: String? = null,
    val maintenanceSummary: String? = null,
    val consumablesNeeded: String? = null,
    val commonProblems: String? = null,
    val safetyNotes: String? = null,
    val lastResearchedAt: Long = System.currentTimeMillis()
)
