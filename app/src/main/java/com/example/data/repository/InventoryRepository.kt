package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.example.ai.gemini.GeminiService
import com.example.ai.gemini.SingleItemScanResult
import com.example.data.local.HomeAiDatabase
import com.example.data.local.entities.*
import com.example.data.local.files.LocalFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class InventoryRepository(
    private val database: HomeAiDatabase,
    private val fileManager: LocalFileManager,
    private val geminiService: GeminiService
) {
    private val itemDao = database.itemDao()
    private val locationDao = database.locationDao()
    private val observationDao = database.itemObservationDao()
    private val historyDao = database.itemLocationHistoryDao()
    private val imageDao = database.itemImageDao()
    private val customFieldDao = database.customFieldDao()
    private val relationshipDao = database.itemRelationshipDao()
    private val troubleshootingDao = database.troubleshootingDao()
    private val documentDao = database.documentDao()
    private val memoryDao = database.aiMemoryDao()
    private val jobDao = database.aiJobDao()
    private val maintenanceDao = database.maintenanceTaskDao()
    private val profileDao = database.itemIntelligenceProfileDao()

    val allActiveItems: Flow<List<ItemEntity>> = itemDao.getAllActiveItems()
    val allLocations: Flow<List<LocationEntity>> = locationDao.getAllLocations()
    val pendingJobs: Flow<List<AiJobEntity>> = jobDao.getPendingJobs()
    val allMaintenanceTasks: Flow<List<MaintenanceTaskEntity>> = maintenanceDao.getAllTasks()
    val overdueTasks: Flow<List<MaintenanceTaskEntity>> = maintenanceDao.getOverdueTasks()
    val upcomingTasks: Flow<List<MaintenanceTaskEntity>> = maintenanceDao.getUpcomingTasks()

    fun observeItem(id: String): Flow<ItemEntity?> = itemDao.observeItemById(id)
    fun observeProfile(itemId: String): Flow<ItemIntelligenceProfileEntity?> = profileDao.observeProfile(itemId)
    fun observeTasksForItem(itemId: String): Flow<List<MaintenanceTaskEntity>> = maintenanceDao.getTasksForItem(itemId)
    fun observeImages(itemId: String): Flow<List<ItemImageEntity>> = imageDao.getImagesForItem(itemId)
    fun observeObservations(itemId: String): Flow<List<ItemObservationEntity>> = observationDao.getObservationsForItem(itemId)
    fun observeHistory(itemId: String): Flow<List<ItemLocationHistoryEntity>> = historyDao.getHistoryForItem(itemId)
    fun observeCustomFields(itemId: String): Flow<List<CustomFieldEntity>> = customFieldDao.getFieldsForItem(itemId)
    fun observeRelationships(itemId: String): Flow<List<ItemRelationshipEntity>> = relationshipDao.getRelationshipsForItem(itemId)
    fun observeTroubleshooting(itemId: String): Flow<List<TroubleshootingEventEntity>> = troubleshootingDao.getEventsForItem(itemId)
    fun observeDocuments(itemId: String): Flow<List<DocumentEntity>> = documentDao.getDocumentsForItem(itemId)
    fun observeMemories(itemId: String): Flow<List<AiMemoryEntity>> = memoryDao.getMemoriesForItem(itemId)

    fun searchItems(query: String): Flow<List<ItemEntity>> = itemDao.searchItems(query)

    suspend fun getItem(id: String): ItemEntity? = itemDao.getItemById(id)

    suspend fun saveNewItemFromScan(
        scanResult: SingleItemScanResult,
        imageFile: File,
        locationId: String?
    ): ItemEntity = withContext(Dispatchers.IO) {
        val itemId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // 1. Save image permanently to item folder
        val targetImg = File(fileManager.getItemImagesDir(itemId), "photo_primary.jpg")
        imageFile.copyTo(targetImg, overwrite = true)
        val thumbPath = fileManager.createThumbnailForFile(itemId, targetImg)

        val primaryImage = ItemImageEntity(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            localPath = targetImg.absolutePath,
            thumbnailPath = thumbPath,
            timestamp = now,
            imageType = "PRIMARY"
        )
        imageDao.insertImage(primaryImage)

        // 2. Create item
        val item = ItemEntity(
            id = itemId,
            name = scanResult.name,
            normalizedName = scanResult.name.lowercase().trim(),
            brand = scanResult.brand,
            manufacturer = scanResult.manufacturer,
            model = scanResult.model,
            modelNumber = scanResult.modelNumber,
            serialNumber = scanResult.serialNumber,
            barcode = scanResult.barcode,
            category = scanResult.category,
            subcategory = scanResult.subcategory,
            quantity = scanResult.quantity,
            condition = scanResult.condition,
            description = scanResult.description,
            currentLocationId = locationId,
            notes = null,
            aiSummary = scanResult.aiSummary,
            aiConfidence = scanResult.confidence,
            createdAt = now,
            updatedAt = now,
            lastScannedAt = now
        )
        itemDao.insertItem(item)

        // 3. Location observation & history if location provided
        if (!locationId.isNullOrEmpty()) {
            val history = ItemLocationHistoryEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                locationId = locationId,
                timestamp = now,
                source = "CAMERA_SCAN",
                confidence = scanResult.confidence,
                note = "Initial scan discovery"
            )
            historyDao.insertHistory(history)
            locationDao.updateLastScanned(locationId, now)
        }

        // 4. Observation record
        val observation = ItemObservationEntity(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            timestamp = now,
            imagePath = targetImg.absolutePath,
            locationId = locationId,
            description = scanResult.description,
            surroundings = null,
            visibleText = if (scanResult.visibleText.isNotEmpty()) scanResult.visibleText.joinToString(", ") else null,
            detectedNearbyItems = null,
            confidence = scanResult.confidence,
            source = "CAMERA_SCAN"
        )
        observationDao.insertObservation(observation)

        // 5. Memory record
        val memory = AiMemoryEntity(
            id = UUID.randomUUID().toString(),
            type = "OBSERVATION",
            itemId = itemId,
            locationId = locationId,
            content = "Item '${item.name}' scanned and identified with confidence ${scanResult.confidence}. Summary: ${scanResult.aiSummary ?: scanResult.description}",
            importance = 0.8f,
            source = "OBSERVED"
        )
        memoryDao.insertMemory(memory)

        item
    }

    suspend fun updateExistingItemObservation(
        itemId: String,
        imageFile: File,
        locationId: String?,
        note: String? = null
    ): ItemEntity? = withContext(Dispatchers.IO) {
        val existing = itemDao.getItemById(itemId) ?: return@withContext null
        val now = System.currentTimeMillis()

        // Save new observation photo
        val targetImg = File(fileManager.getItemImagesDir(itemId), "photo_${System.currentTimeMillis()}.jpg")
        imageFile.copyTo(targetImg, overwrite = true)
        val imageRecord = ItemImageEntity(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            localPath = targetImg.absolutePath,
            thumbnailPath = null,
            timestamp = now,
            imageType = "LOCATION_CONTEXT"
        )
        imageDao.insertImage(imageRecord)

        // Update item location & scan time
        val updated = existing.copy(
            currentLocationId = locationId ?: existing.currentLocationId,
            lastScannedAt = now,
            updatedAt = now
        )
        itemDao.updateItem(updated)

        // Add history
        if (locationId != null) {
            val history = ItemLocationHistoryEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                locationId = locationId,
                timestamp = now,
                source = "CAMERA_SCAN",
                confidence = 1.0f,
                note = note ?: "Updated during scan"
            )
            historyDao.insertHistory(history)
            locationDao.updateLastScanned(locationId, now)
        }

        // Add observation
        val observation = ItemObservationEntity(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            timestamp = now,
            imagePath = targetImg.absolutePath,
            locationId = locationId,
            description = "Re-scanned at location",
            surroundings = null,
            visibleText = null,
            detectedNearbyItems = null,
            confidence = 1.0f,
            source = "CAMERA_SCAN"
        )
        observationDao.insertObservation(observation)

        updated
    }

    suspend fun findDuplicateCandidates(
        scanResult: SingleItemScanResult
    ): List<ItemEntity> = withContext(Dispatchers.IO) {
        val matches = mutableListOf<ItemEntity>()

        // 1. Serial number exact match
        if (!scanResult.serialNumber.isNullOrEmpty()) {
            itemDao.findBySerialNumber(scanResult.serialNumber)?.let { matches.add(it) }
        }

        // 2. Barcode match
        if (matches.isEmpty() && !scanResult.barcode.isNullOrEmpty()) {
            itemDao.findByBarcode(scanResult.barcode)?.let { matches.add(it) }
        }

        // 3. Model number match
        if (matches.isEmpty() && !scanResult.modelNumber.isNullOrEmpty()) {
            matches.addAll(itemDao.findByModelNumber(scanResult.modelNumber))
        }

        // 4. Fuzzy name/brand candidates
        if (matches.isEmpty()) {
            val candidates = itemDao.findMatchingCandidates(scanResult.name)
            matches.addAll(candidates.take(3))
        }

        matches.distinctBy { it.id }
    }

    suspend fun updateItem(item: ItemEntity) = itemDao.updateItem(item.copy(updatedAt = System.currentTimeMillis()))

    suspend fun archiveItem(itemId: String) = withContext(Dispatchers.IO) {
        val item = itemDao.getItemById(itemId) ?: return@withContext
        itemDao.updateItem(item.copy(status = "ARCHIVED", updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteItem(itemId: String, deleteFiles: Boolean = true) = withContext(Dispatchers.IO) {
        itemDao.deleteItemById(itemId)
        if (deleteFiles) {
            fileManager.getItemDir(itemId).deleteRecursively()
        }
    }

    suspend fun moveItem(itemId: String, targetLocationId: String) = withContext(Dispatchers.IO) {
        val item = itemDao.getItemById(itemId) ?: return@withContext
        val now = System.currentTimeMillis()
        val updated = item.copy(currentLocationId = targetLocationId, updatedAt = now)
        itemDao.updateItem(updated)

        val history = ItemLocationHistoryEntity(
            id = UUID.randomUUID().toString(),
            itemId = itemId,
            locationId = targetLocationId,
            timestamp = now,
            source = "USER_CONFIRMED",
            confidence = 1.0f,
            note = "Manually moved"
        )
        historyDao.insertHistory(history)
    }

    suspend fun addCustomField(itemId: String, name: String, value: String) {
        customFieldDao.insertField(
            CustomFieldEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                fieldName = name,
                fieldValue = value
            )
        )
    }

    suspend fun addRelationship(sourceItemId: String, targetItemId: String, type: String, note: String? = null) {
        relationshipDao.insertRelationship(
            ItemRelationshipEntity(
                id = UUID.randomUUID().toString(),
                sourceItemId = sourceItemId,
                targetItemId = targetItemId,
                relationshipType = type,
                notes = note
            )
        )
    }

    suspend fun recordTroubleshooting(
        itemId: String,
        problem: String,
        actionsTaken: String?,
        result: String,
        successfulSolution: String?
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        troubleshootingDao.insertEvent(
            TroubleshootingEventEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                timestamp = now,
                problem = problem,
                actionsTaken = actionsTaken,
                result = result,
                successfulSolution = successfulSolution
            )
        )

        // Also add to AI memory
        val item = itemDao.getItemById(itemId)
        val name = item?.name ?: "Item"
        memoryDao.insertMemory(
            AiMemoryEntity(
                id = UUID.randomUUID().toString(),
                type = "TROUBLESHOOTING",
                itemId = itemId,
                content = "Troubleshooting on $name: Problem '$problem'. Fixed by: '${successfulSolution ?: actionsTaken}'. Result: $result",
                importance = 0.9f,
                source = "USER_PROVIDED"
            )
        )
    }

    suspend fun addDocument(
        itemId: String,
        name: String,
        file: File,
        docType: String = "MANUAL",
        sourceUrl: String? = null,
        summary: String? = null
    ) {
        val now = System.currentTimeMillis()
        documentDao.insertDocument(
            DocumentEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                name = name,
                localPath = file.absolutePath,
                documentType = docType,
                sourceUrl = sourceUrl,
                downloadedAt = now,
                summary = summary
            )
        )
    }

    suspend fun queueScanJob(imageFile: File, userHint: String? = null): AiJobEntity = withContext(Dispatchers.IO) {
        val job = AiJobEntity(
            id = UUID.randomUUID().toString(),
            type = "SCAN_PROCESSING",
            payloadPath = imageFile.absolutePath,
            status = "PENDING"
        )
        jobDao.insertJob(job)
        job
    }

    suspend fun processQueuedScanJob(job: AiJobEntity): Result<SingleItemScanResult> = withContext(Dispatchers.IO) {
        val file = File(job.payloadPath ?: "")
        if (!file.exists()) {
            jobDao.updateJob(job.copy(status = "FAILED", errorMessage = "Scan image file not found on disk."))
            return@withContext Result.failure(Exception("File not found"))
        }

        jobDao.updateJob(job.copy(status = "PROCESSING", lastAttemptAt = System.currentTimeMillis()))
        val result = geminiService.analyzeSingleItem(file)
        if (result.isSuccess) {
            jobDao.updateJob(job.copy(status = "COMPLETED", errorMessage = null))
        } else {
            val err = result.exceptionOrNull()?.message ?: "Unknown error"
            jobDao.updateJob(job.copy(status = "FAILED", errorMessage = err, retryCount = job.retryCount + 1))
        }
        result
    }

    suspend fun getLocationPath(locationId: String?): String = withContext(Dispatchers.IO) {
        if (locationId.isNullOrEmpty()) return@withContext "Unassigned"
        val parts = mutableListOf<String>()
        var curr: LocationEntity? = locationDao.getLocationById(locationId)
        var depth = 0
        while (curr != null && depth < 8) {
            parts.add(0, curr.name)
            curr = curr.parentLocationId?.let { locationDao.getLocationById(it) }
            depth++
        }
        if (parts.isEmpty()) "Unassigned" else parts.joinToString(" / ")
    }

    suspend fun buildAssistantContext(userPrompt: String): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()

        // 1. Search candidate items
        val candidateItems = itemDao.findMatchingCandidates(userPrompt.replace("?", "").replace("where", "").replace("is", "").trim())
        val allItems = if (candidateItems.isNotEmpty()) candidateItems else itemDao.getAllActiveItems() // or take recent
        
        sb.append("=== REGISTERED INVENTORY ===\n")
        val itemsList = itemDao.getAllActiveItems()
        // Query recent 25 items for context
        // Direct read via DAO
        // Since getAllActiveItems returns Flow, get top candidates or recent
        val relevantItems = if (candidateItems.isNotEmpty()) candidateItems else emptyList()
        if (relevantItems.isNotEmpty()) {
            sb.append("Directly matching inventory:\n")
            for (item in relevantItems.take(5)) {
                val locPath = getLocationPath(item.currentLocationId)
                val obs = observationDao.getLatestObservation(item.id)
                sb.append("- Item: ${item.name} (${item.brand ?: ""} ${item.model ?: ""})\n")
                sb.append("  Current Location: $locPath\n")
                sb.append("  Last Scanned: ${java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.US).format(java.util.Date(item.lastScannedAt))}\n")
                if (obs != null) {
                    sb.append("  Visual Memory: ${obs.description ?: "None"}\n")
                }
                if (!item.aiSummary.isNullOrEmpty()) {
                    sb.append("  AI Note: ${item.aiSummary}\n")
                }
            }
        }

        // 2. Troubleshooting history check
        val troubles = troubleshootingDao.searchTroubleshooting(userPrompt)
        if (troubles.isNotEmpty()) {
            sb.append("\n=== PREVIOUS TROUBLESHOOTING HISTORY ===\n")
            for (t in troubles.take(3)) {
                val itm = itemDao.getItemById(t.itemId)
                sb.append("- On ${itm?.name ?: "Item"}: Problem '${t.problem}'. Fixed by '${t.successfulSolution ?: t.actionsTaken}'. Result: ${t.result}\n")
            }
        }

        // 3. Relevant AI Memories
        val memories = memoryDao.getAllImportantMemories()
        if (memories.isNotEmpty()) {
            sb.append("\n=== RELEVANT HOUSEHOLD MEMORIES ===\n")
            for (m in memories.take(6)) {
                sb.append("- [${m.type}] ${m.content}\n")
            }
        }

        sb.toString()
    }

    suspend fun saveMemory(memory: AiMemoryEntity) = withContext(Dispatchers.IO) {
        memoryDao.insertMemory(memory)
    }

    fun observeMemoriesForItem(itemId: String): Flow<List<AiMemoryEntity>> {
        return memoryDao.getMemoriesForItem(itemId)
    }

    suspend fun researchAndApplyToItem(itemId: String): Result<com.example.ai.gemini.ItemResearchResult> = withContext(Dispatchers.IO) {
        val item = itemDao.getItemById(itemId) ?: return@withContext Result.failure(Exception("Item not found: $itemId"))
        val researchResult = geminiService.researchItemIntel(
            name = item.name,
            brand = item.brand,
            model = item.model,
            modelNumber = item.modelNumber,
            category = item.category
        )
        if (researchResult.isSuccess) {
            val research = researchResult.getOrNull()!!
            applyResearchToItem(itemId, research)
        }
        researchResult
    }

    suspend fun applyResearchToItem(itemId: String, research: com.example.ai.gemini.ItemResearchResult): ItemEntity = withContext(Dispatchers.IO) {
        val item = itemDao.getItemById(itemId) ?: throw IllegalStateException("Item not found: $itemId")
        val now = System.currentTimeMillis()

        // 1. Intelligence Profile
        val profile = ItemIntelligenceProfileEntity(
            itemId = itemId,
            manufacturer = item.brand ?: item.manufacturer,
            model = item.model ?: item.modelNumber,
            researchStatus = "KNOWLEDGE_READY",
            officialProductPage = research.manualUrl,
            manualAvailable = !research.manualUrl.isNullOrEmpty() || !research.manualTitle.isNullOrEmpty(),
            manualUrl = research.manualUrl,
            maintenanceSummary = if (research.proTips.isNotEmpty()) "• " + research.proTips.joinToString("\n• ") else null,
            consumablesNeeded = if (research.recommendedParts.isNotEmpty()) research.recommendedParts.joinToString(", ") else null,
            commonProblems = if (research.commonIssues.isNotEmpty()) {
                research.commonIssues.joinToString("\n---\n") { issue ->
                    val sym = if (!issue.symptom.isNullOrEmpty()) " (${issue.symptom})" else ""
                    val sol = if (!issue.solution.isNullOrEmpty()) " -> Fix: ${issue.solution}" else ""
                    val src = if (!issue.source.isNullOrEmpty()) " [Source: ${issue.source}]" else ""
                    "${issue.issue}$sym$sol$src"
                }
            } else null,
            safetyNotes = research.safetyNotes,
            lastResearchedAt = now
        )
        profileDao.insertProfile(profile)

        // 2. Official Document / Manual
        if (!research.manualTitle.isNullOrEmpty() || !research.manualUrl.isNullOrEmpty()) {
            val doc = DocumentEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                name = research.manualTitle ?: "${item.name} User Manual",
                localPath = "", // Web link reference
                sourceUrl = research.manualUrl,
                sourceDomain = research.forumSources.firstOrNull() ?: "Official Support",
                documentType = "MANUAL",
                manufacturer = item.brand,
                downloadedAt = now,
                summary = research.manualSummary
            )
            documentDao.insertDocument(doc)
        }

        // 3. Maintenance Tasks
        for (task in research.maintenanceTasks) {
            val mTask = MaintenanceTaskEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                title = task.title,
                description = task.description,
                intervalDays = task.intervalDays,
                nextDueDate = now + (task.intervalDays * 24L * 60 * 60 * 1000),
                source = "AI_SUGGESTION"
            )
            maintenanceDao.insertTask(mTask)
        }

        // 4. Custom Field for Specs if available
        if (!research.specsSummary.isNullOrEmpty()) {
            customFieldDao.insertField(
                CustomFieldEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = itemId,
                    fieldName = "Specifications",
                    fieldValue = research.specsSummary
                )
            )
        }

        // 5. Enrich Item Notes & AI Summary
        var enrichedNotes = item.notes
        if (enrichedNotes.isNullOrEmpty() && !research.specsSummary.isNullOrEmpty()) {
            enrichedNotes = research.specsSummary
        }

        val enrichedSummary = if (item.aiSummary.isNullOrEmpty()) {
            "Intel synthesized from ${research.forumSources.take(3).joinToString(", ")}. " + (research.proTips.firstOrNull() ?: "")
        } else {
            item.aiSummary
        }

        val updatedItem = item.copy(
            notes = enrichedNotes,
            aiSummary = enrichedSummary,
            updatedAt = now
        )
        itemDao.updateItem(updatedItem)

        // 6. Memory for Assistant
        val topIssuesStr = research.commonIssues.take(2).map { it.issue }.joinToString(", ")
        val topTip = research.proTips.firstOrNull() ?: ""
        memoryDao.insertMemory(
            AiMemoryEntity(
                id = UUID.randomUUID().toString(),
                type = "FACT",
                itemId = itemId,
                content = "Forum & Manual Intel for ${item.name}: Common issues ($topIssuesStr). Pro maintenance tip: $topTip",
                importance = 0.85f,
                source = "DOCUMENT_VERIFIED"
            )
        )

        updatedItem
    }

    suspend fun batchResearchItems(
        itemIds: List<String>,
        onProgress: (current: Int, total: Int, currentItemName: String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        var count = 0
        val total = itemIds.size
        for ((index, id) in itemIds.withIndex()) {
            val itm = itemDao.getItemById(id)
            val name = itm?.name ?: "Item"
            onProgress(index + 1, total, name)
            val res = researchAndApplyToItem(id)
            if (res.isSuccess) {
                count++
            }
        }
        count
    }
}
