package com.example.data.repository

import android.content.Context
import com.example.data.local.HomeAiDatabase
import com.example.data.local.entities.ChatMessageEntity
import com.example.data.local.files.LocalFileManager
import com.example.ai.gemini.GeminiService
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
        return chatMessageDao.getMessagesForConversation(mode.id)
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
        chatMessageDao.clearMessages(mode.id)
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
                                    brand = obj.optString("brand", null),
                                    model = obj.optString("model", null),
                                    modelNumber = obj.optString("modelNumber", null),
                                    serialNumber = obj.optString("serialNumber", null),
                                    category = obj.optString("category", "General"),
                                    currentLocationId = obj.optString("currentLocationId", null),
                                    description = obj.optString("description", null),
                                    notes = obj.optString("notes", null),
                                    aiSummary = obj.optString("aiSummary", null),
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
