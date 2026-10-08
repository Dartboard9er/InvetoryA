package com.example.data.repository

import android.content.Context
import com.example.ai.gemini.GeminiService
import com.example.ai.gemini.SingleItemScanResult
import com.example.data.local.HomeAiDatabase
import com.example.data.local.entities.ChatMessageEntity
import com.example.data.local.entities.ItemEntity
import com.example.data.local.entities.LocationEntity
import com.example.data.local.files.LocalFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class AssistantRepository(
    private val database: HomeAiDatabase,
    private val geminiService: GeminiService,
    private val inventoryRepository: InventoryRepository
) {
    private val chatMessageDao = database.chatMessageDao()

    fun getMessagesForMode(mode: com.example.ai.modes.AssistantMode): Flow<List<ChatMessageEntity>> {
        return if (mode == com.example.ai.modes.AssistantMode.HOME_AI) {
            chatMessageDao.getAllMessages()
        } else {
            chatMessageDao.getMessagesForConversation(mode.id)
        }
    }

    suspend fun sendMessage(userText: String, mode: com.example.ai.modes.AssistantMode): Result<String> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val conversationId = mode.id

        // 1. Save user message locally
        val userMsg = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            role = "USER",
            text = userText,
            timestamp = now
        )
        chatMessageDao.insertMessage(userMsg)

        val cleanText = userText.trim()
        val lower = cleanText.lowercase()

        // Detect if this is an item addition request
        val isAddAction = lower.startsWith("add ") || lower.startsWith("added ") ||
                lower.startsWith("bought ") || lower.startsWith("new item ") ||
                lower.startsWith("record ") || lower.startsWith("store ") ||
                lower.startsWith("put ") || lower.startsWith("save ") ||
                (lower.contains(" add ") && !lower.contains("how do i add")) ||
                (lower.contains(" added ") && !lower.contains("who added")) ||
                (lower.contains(" put ") && (lower.contains(" in ") || lower.contains(" to ") || lower.contains(" on ")))

        // Detect if this is a relocation request
        val isRelocateAction = !isAddAction && (
                lower.startsWith("move ") || lower.startsWith("moved ") ||
                lower.startsWith("relocate ") || lower.startsWith("relocated ") ||
                (lower.contains(" moved ") && (lower.contains(" to ") || lower.contains(" into ") || lower.contains(" in ")))
        )

        if (isAddAction) {
            val locations = database.locationDao().getLocationsList()
            val locsSummary = locations.joinToString(", ") { "${it.name} (${it.type})" }

            val parsedResult: SingleItemScanResult? = if (geminiService.getApiKey().isNotEmpty()) {
                try {
                    val res = geminiService.parseItemFromConversation(userText, locsSummary)
                    res.getOrNull()
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }

            val finalParsed = parsedResult ?: parseItemLocally(userText, locations)

            val matchedLoc = locations.find { loc ->
                userText.contains(loc.name, ignoreCase = true)
            } ?: locations.firstOrNull()

            val tempFile = File.createTempFile("assistant_add_", ".jpg")
            com.example.util.SampleScanGenerator.generateSampleImageFile(tempFile, (0..5).random())

            val savedItem = inventoryRepository.saveNewItemFromScan(
                scanResult = finalParsed,
                imageFile = tempFile,
                locationId = matchedLoc?.id
            )
            tempFile.delete()

            val locationName = matchedLoc?.name ?: "Home"
            val replyText = buildString {
                append("✅ Added to Inventory!\n\n")
                append("• Item: ${savedItem.name}\n")
                if (!savedItem.brand.isNullOrEmpty() || !savedItem.model.isNullOrEmpty()) {
                    append("• Details: ${listOfNotNull(savedItem.brand, savedItem.model).joinToString(" ")}\n")
                }
                append("• Location: $locationName\n")
                append("• Category: ${savedItem.category}\n")
                append("• Quantity: ${savedItem.quantity}\n\n")
                append("I've saved this item in your inventory database. You can view, search, or edit it under the Inventory tab.")
            }

            val assistantMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = "ASSISTANT",
                text = replyText,
                timestamp = System.currentTimeMillis()
            )
            chatMessageDao.insertMessage(assistantMsg)
            return@withContext Result.success(replyText)
        }

        if (isRelocateAction) {
            val locations = database.locationDao().getLocationsList()
            val matchedLoc = locations.find { loc ->
                userText.contains(loc.name, ignoreCase = true)
            }

            val allCandidates = database.itemDao().findMatchingCandidates("")
            val matchedItem = allCandidates.firstOrNull { item ->
                userText.contains(item.name, ignoreCase = true) ||
                        (!item.brand.isNullOrEmpty() && userText.contains(item.brand, ignoreCase = true)) ||
                        (!item.model.isNullOrEmpty() && userText.contains(item.model, ignoreCase = true))
            }

            if (matchedItem != null && matchedLoc != null) {
                inventoryRepository.updateItem(
                    matchedItem.copy(
                        currentLocationId = matchedLoc.id,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                val history = com.example.data.local.entities.ItemLocationHistoryEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = matchedItem.id,
                    locationId = matchedLoc.id,
                    timestamp = System.currentTimeMillis(),
                    source = "ASSISTANT_CHAT",
                    confidence = 1.0f,
                    note = userText
                )
                database.itemLocationHistoryDao().insertHistory(history)

                val replyText = "✅ Location Updated!\n\n• Item: ${matchedItem.name}\n• New Location: ${matchedLoc.name}\n\nI've updated this in your household inventory database."
                val assistantMsg = ChatMessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = "ASSISTANT",
                    text = replyText,
                    timestamp = System.currentTimeMillis()
                )
                chatMessageDao.insertMessage(assistantMsg)
                return@withContext Result.success(replyText)
            }
        }

        // 2. Build Mode-Scoped Context
        val context = buildModeScopedContext(userText, mode)

        // 3. Check for API key
        val apiKey = geminiService.getApiKey()
        if (apiKey.isEmpty()) {
            val fallback = if (context.contains("Directly matching inventory:") || context.contains("Kitchen items:") || context.contains("Garage tools:")) {
                "Here is what I found in your local Home AI records for [${mode.title}]:\n\n" +
                        context.trim() +
                        "\n\n(Tip: Add your Gemini API key in More → Settings to enable intelligent meal planning, troubleshooting and advice)."
            } else {
                "I searched your local inventory under [${mode.title}] for '$userText' but found no matching records.\n\nAdd your Gemini API key in More → Settings for conversational assistance."
            }
            val assistantMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = "ASSISTANT",
                text = fallback,
                timestamp = System.currentTimeMillis()
            )
            chatMessageDao.insertMessage(assistantMsg)
            return@withContext Result.success(fallback)
        }

        // 4. Send to Gemini with mode-specific instruction
        val result = geminiService.answerAssistantQuery(userText, context)
        if (result.isSuccess) {
            val reply = result.getOrNull() ?: "I couldn't generate an answer."
            val assistantMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = "ASSISTANT",
                text = reply,
                timestamp = System.currentTimeMillis()
            )
            chatMessageDao.insertMessage(assistantMsg)
            Result.success(reply)
        } else {
            val errorText = "AI could not process your question: ${result.exceptionOrNull()?.message ?: "Check your connection"}"
            val assistantMsg = ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                role = "ASSISTANT",
                text = errorText,
                timestamp = System.currentTimeMillis()
            )
            chatMessageDao.insertMessage(assistantMsg)
            Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    private fun parseItemLocally(userText: String, locations: List<com.example.data.local.entities.LocationEntity>): SingleItemScanResult {
        var raw = userText.trim()
        val stripPrefixes = listOf(
            "add a ", "add an ", "add the ", "add my ", "add ",
            "added a ", "added an ", "added the ", "added my ", "added ",
            "bought a ", "bought an ", "bought the ", "bought ",
            "put a ", "put an ", "put the ", "put my ", "put ",
            "new item: ", "new item ", "record ", "store "
        )
        for (prefix in stripPrefixes) {
            if (raw.startsWith(prefix, ignoreCase = true)) {
                raw = raw.substring(prefix.length).trim()
                break
            }
        }

        var cleanName = raw
        locations.forEach { loc ->
            val locSuffixes = listOf(
                " in the ${loc.name}", " in ${loc.name}",
                " into the ${loc.name}", " into ${loc.name}",
                " to the ${loc.name}", " to ${loc.name}",
                " on the ${loc.name}", " on ${loc.name}",
                " at the ${loc.name}", " at ${loc.name}"
            )
            locSuffixes.forEach { suffix ->
                if (cleanName.endsWith(suffix, ignoreCase = true)) {
                    cleanName = cleanName.substring(0, cleanName.length - suffix.length).trim()
                }
            }
        }

        if (cleanName.isBlank()) cleanName = raw.take(40).ifBlank { "Household Item" }

        val lower = cleanName.lowercase()
        val category = when {
            lower.contains("drill") || lower.contains("saw") || lower.contains("hammer") ||
                    lower.contains("wrench") || lower.contains("screwdriver") || lower.contains("socket") ||
                    lower.contains("tool") || lower.contains("plier") || lower.contains("clamp") -> "Tools"

            lower.contains("battery") || lower.contains("screw") || lower.contains("bolt") ||
                    lower.contains("nail") || lower.contains("washer") || lower.contains("hardware") -> "Hardware"

            lower.contains("pan") || lower.contains("pot") || lower.contains("blender") ||
                    lower.contains("knife") || lower.contains("coffee") || lower.contains("plate") ||
                    lower.contains("rice") || lower.contains("food") || lower.contains("spice") -> "Kitchen"

            lower.contains("tv") || lower.contains("laptop") || lower.contains("monitor") ||
                    lower.contains("speaker") || lower.contains("phone") || lower.contains("charger") ||
                    lower.contains("camera") || lower.contains("printer") -> "Electronics"

            lower.contains("box") || lower.contains("bin") || lower.contains("tote") ||
                    lower.contains("shelf") || lower.contains("container") -> "Storage"

            else -> "General"
        }

        var quantity = 1
        val qtyRegex = Regex("""\b(\d+)\s+([a-zA-Z].*)""")
        val match = qtyRegex.matchEntire(cleanName)
        if (match != null) {
            val num = match.groupValues[1].toIntOrNull()
            if (num != null && num in 1..9999) {
                quantity = num
            }
        }

        return SingleItemScanResult(
            name = cleanName.replaceFirstChar { it.uppercase() },
            brand = null,
            manufacturer = null,
            model = null,
            modelNumber = null,
            serialNumber = null,
            barcode = null,
            category = category,
            subcategory = null,
            quantity = quantity,
            condition = "Good",
            description = userText,
            visibleText = emptyList<String>(),
            accessories = emptyList<String>(),
            confidence = 0.95f,
            aiSummary = "Added via Home AI assistant conversation"
        )
    }

    private suspend fun buildModeScopedContext(userText: String, mode: com.example.ai.modes.AssistantMode): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append("=== ACTIVE ASSISTANT MODE: ${mode.title.uppercase()} (${mode.scopeBadge}) ===\n")

        val allItems = database.itemDao().findMatchingCandidates("")

        when (mode) {
            com.example.ai.modes.AssistantMode.COOKING -> {
                sb.append("SCOPE RULE: Kitchen, pantry, food ingredients, cookware, and kitchen appliances only.\n")
                val kitchenItems = allItems.filter {
                    it.category.equals("Kitchen", ignoreCase = true) ||
                            it.category.equals("Food", ignoreCase = true) ||
                            it.category.equals("Storage", ignoreCase = true) ||
                            it.name.contains("chicken", true) || it.name.contains("potato", true) ||
                            it.name.contains("bacon", true) || it.name.contains("pan", true) ||
                            it.name.contains("knife", true)
                }
                sb.append("Kitchen items found (${kitchenItems.size}):\n")
                kitchenItems.take(20).forEach {
                    sb.append("- ${it.name} (${it.category}, condition: ${it.condition ?: "Good"}, qty: ${it.quantity})\n")
                }
            }

            com.example.ai.modes.AssistantMode.GARAGE -> {
                sb.append("SCOPE RULE: Garage tools, equipment, workshop supplies, hardware, and location history.\n")
                val garageItems = allItems.filter {
                    it.category.equals("Tools", ignoreCase = true) ||
                            it.category.equals("Hardware", ignoreCase = true) ||
                            it.name.contains("drill", true) || it.name.contains("pressure", true) ||
                            it.name.contains("saw", true) || it.name.contains("box", true) ||
                            it.name.contains("battery", true)
                }
                sb.append("Garage tools & equipment (${garageItems.size}):\n")
                garageItems.take(20).forEach {
                    val loc = inventoryRepository.getLocationPath(it.currentLocationId)
                    sb.append("- ${it.name} (${it.brand ?: ""} ${it.model ?: ""}) at $loc\n")
                }
            }

            com.example.ai.modes.AssistantMode.MAINTENANCE -> {
                sb.append("SCOPE RULE: Maintenance schedules, appliance servicing, filters, troubleshooting history, and warranties.\n")
                val maintItems = allItems.filter {
                    it.category.equals("Tools", ignoreCase = true) ||
                            it.category.equals("Electronics", ignoreCase = true) ||
                            it.warrantyEnd != null ||
                            it.name.contains("drill", true) || it.name.contains("washer", true) ||
                            it.name.contains("printer", true) || it.name.contains("tv", true)
                }
                sb.append("Maintainable household equipment (${maintItems.size}):\n")
                maintItems.take(15).forEach {
                    val loc = inventoryRepository.getLocationPath(it.currentLocationId)
                    sb.append("- ${it.name} (${it.brand ?: ""}) at $loc\n")
                }
                // Include recorded fixes
                val troubles = database.troubleshootingDao().searchTroubleshooting("")
                if (troubles.isNotEmpty()) {
                    sb.append("Previous fixes remembered:\n")
                    troubles.take(5).forEach {
                        sb.append("- Problem '${it.problem}' fixed by '${it.successfulSolution ?: it.actionsTaken}'\n")
                    }
                }
            }

            com.example.ai.modes.AssistantMode.FIND, com.example.ai.modes.AssistantMode.HOME_AI -> {
                sb.append(inventoryRepository.buildAssistantContext(userText))
            }
        }

        sb.toString()
    }

    suspend fun clearHistory(mode: com.example.ai.modes.AssistantMode) = withContext(Dispatchers.IO) {
        if (mode == com.example.ai.modes.AssistantMode.HOME_AI) {
            chatMessageDao.clearAllMessages()
        } else {
            chatMessageDao.clearMessages(mode.id)
        }
    }
}

class BackupRepository(
    private val context: Context,
    private val database: HomeAiDatabase,
    private val fileManager: LocalFileManager
) {
    suspend fun exportBackup(targetZipFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            targetZipFile.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(targetZipFile))).use { zos ->
                // 1. Export database records to json
                val backupJson = JSONObject()
                val items = database.itemDao().findMatchingCandidates("")
                val itemsArr = JSONArray()
                for (it in items) {
                    val obj = JSONObject().apply {
                        put("id", it.id)
                        put("name", it.name)
                        put("brand", it.brand)
                        put("model", it.model)
                        put("modelNumber", it.modelNumber)
                        put("serialNumber", it.serialNumber)
                        put("category", it.category)
                        put("currentLocationId", it.currentLocationId)
                        put("description", it.description)
                        put("notes", it.notes)
                        put("aiSummary", it.aiSummary)
                        put("lastScannedAt", it.lastScannedAt)
                    }
                    itemsArr.put(obj)
                }
                backupJson.put("version", 1)
                backupJson.put("exportedAt", System.currentTimeMillis())
                backupJson.put("items", itemsArr)

                // Write metadata.json into zip
                val metaEntry = ZipEntry("home_ai_data.json")
                zos.putNextEntry(metaEntry)
                zos.write(backupJson.toString(2).toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. Include all images and files
                val homeAiDir = File(context.filesDir, "home_ai")
                if (homeAiDir.exists()) {
                    homeAiDir.walkTopDown().forEach { file ->
                        if (file.isFile && !file.absolutePath.contains("temporary")) {
                            val relPath = file.relativeTo(context.filesDir).path
                            val entry = ZipEntry(relPath)
                            zos.putNextEntry(entry)
                            file.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
            }
            Result.success(targetZipFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(zipFile: File): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var restoredItems = 0
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == "home_ai_data.json") {
                        val jsonString = zis.bufferedReader().readText()
                        val root = JSONObject(jsonString)
                        val itemsArr = root.optJSONArray("items")
                        if (itemsArr != null) {
                            for (i in 0 until itemsArr.length()) {
                                val obj = itemsArr.getJSONObject(i)
                                val item = com.example.data.local.entities.ItemEntity(
                                    id = obj.getString("id"),
                                    name = obj.getString("name"),
                                    normalizedName = obj.getString("name").lowercase(),
                                    brand = if (obj.has("brand")) obj.optString("brand") else null,
                                    model = if (obj.has("model")) obj.optString("model") else null,
                                    modelNumber = if (obj.has("modelNumber")) obj.optString("modelNumber") else null,
                                    serialNumber = if (obj.has("serialNumber")) obj.optString("serialNumber") else null,
                                    category = obj.optString("category", "General"),
                                    currentLocationId = if (obj.has("currentLocationId")) obj.optString("currentLocationId") else null,
                                    description = if (obj.has("description")) obj.optString("description") else null,
                                    notes = if (obj.has("notes")) obj.optString("notes") else null,
                                    aiSummary = if (obj.has("aiSummary")) obj.optString("aiSummary") else null,
                                    lastScannedAt = obj.optLong("lastScannedAt", System.currentTimeMillis())
                                )
                                database.itemDao().insertItem(item)
                                restoredItems++
                            }
                        }
                    } else if (!entry.isDirectory) {
                        val destFile = File(context.filesDir, name)
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            Result.success(restoredItems)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportCsv(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val items = database.itemDao().findMatchingCandidates("")
            val sb = StringBuilder()
            sb.append("ID,Name,Brand,Model,ModelNumber,SerialNumber,Category,LocationID,LastScanned\n")
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            for (it in items) {
                sb.append("\"${it.id}\",\"${it.name.replace("\"", "\"\"")}\",\"${it.brand ?: ""}\",\"${it.model ?: ""}\",\"${it.modelNumber ?: ""}\",\"${it.serialNumber ?: ""}\",\"${it.category}\",\"${it.currentLocationId ?: ""}\",\"${sdf.format(Date(it.lastScannedAt))}\"\n")
            }
            Result.success(sb.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
