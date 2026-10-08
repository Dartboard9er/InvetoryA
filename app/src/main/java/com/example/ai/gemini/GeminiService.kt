package com.example.ai.gemini

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

object AIConfiguration {
    // Current official Gemini models as per gemini-api skill guidelines
    const val MODEL_FAST_VISION = "gemini-3.5-flash"
    const val MODEL_REASONING = "gemini-3.1-pro-preview"
    const val MODEL_EMBEDDINGS = "gemini-embedding-2-preview"
}

class GeminiService(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("home_ai_prefs", Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getApiKey(): String {
        val userKey = prefs.getString("user_gemini_api_key", null)?.trim()
        if (!userKey.isNullOrEmpty()) return userKey
        // Fallback to BuildConfig if provided in .env
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }

    fun saveApiKey(key: String) {
        prefs.edit().putString("user_gemini_api_key", key.trim()).apply()
    }

    fun clearApiKey() {
        prefs.edit().remove("user_gemini_api_key").apply()
    }

    fun isGeminiEnabled(): Boolean {
        return prefs.getBoolean("gemini_enabled", true)
    }

    fun setGeminiEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("gemini_enabled", enabled).apply()
    }

    fun isAutoProcessEnabled(): Boolean {
        return prefs.getBoolean("auto_process_scans", true)
    }

    fun setAutoProcessEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_process_scans", enabled).apply()
    }

    fun getUsageStats(): Pair<Int, Int> {
        val count = prefs.getInt("ai_request_count", 0)
        val month = prefs.getInt("ai_request_month", 0)
        return Pair(count, month)
    }

    private fun recordUsage() {
        val count = prefs.getInt("ai_request_count", 0)
        prefs.edit().putInt("ai_request_count", count + 1).apply()
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No Gemini API key configured. Please enter one in Settings."))
        }
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/${AIConfiguration.MODEL_FAST_VISION}:generateContent?key=$apiKey"
            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    val content = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", "Respond strictly with the single word: CONNECTED") })
                        }
                        put("parts", parts)
                    }
                    put(content)
                }
                put("contents", contents)
            }

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("API Error (${response.code}): $err"))
                }
                val respStr = response.body?.string() ?: ""
                recordUsage()
                Result.success("Connection successful! Gemini API is active.")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeSingleItem(imageFile: File, userContextHint: String? = null): Result<SingleItemScanResult> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is missing."))
        }

        try {
            val base64Image = encodeImageToBase64(imageFile, maxDimension = 1440)
            val prompt = buildString {
                append("You are Home AI's precise visual inventory identification engine. ")
                append("Analyze this household object photograph. Extract exact facts observed in the photo. ")
                append("STRICT RULES: Never fabricate serial numbers, model numbers, or brands. If not visible, return null. ")
                append("Distinguish observed facts from reasonable inferences. ")
                if (!userContextHint.isNullOrEmpty()) {
                    append("User hint/location context: $userContextHint. ")
                }
                append("Return ONLY valid JSON matching this schema: ")
                append("{\n")
                append("  \"name\": \"Short recognizable title (e.g. DeWalt Cordless Drill)\",\n")
                append("  \"brand\": \"Brand or null\",\n")
                append("  \"manufacturer\": \"Manufacturer or null\",\n")
                append("  \"model\": \"Model or null\",\n")
                append("  \"modelNumber\": \"Model number if visible or null\",\n")
                append("  \"serialNumber\": \"Serial number if visible or null\",\n")
                append("  \"barcode\": \"Barcode digits if visible or null\",\n")
                append("  \"category\": \"Category (Tools, Electronics, Kitchen, Storage, Office, Home, Other)\",\n")
                append("  \"subcategory\": \"Subcategory or null\",\n")
                append("  \"quantity\": 1,\n")
                append("  \"condition\": \"Condition assessment (New, Excellent, Good, Fair, Poor)\",\n")
                append("  \"description\": \"Concise visual description\",\n")
                append("  \"visibleText\": [\"Text snippet 1\", \"Text snippet 2\"],\n")
                append("  \"accessories\": [\"Included accessory 1\"],\n")
                append("  \"confidence\": 0.95,\n")
                append("  \"aiSummary\": \"Home AI visual observation note\"\n")
                append("}")
            }

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val content = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        }
                        put("parts", parts)
                    }
                    put(content)
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/${AIConfiguration.MODEL_FAST_VISION}:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("Gemini error (${response.code}): $err"))
                }
                val respStr = response.body?.string() ?: ""
                val jsonText = parseCandidateText(respStr)
                recordUsage()

                val result = parseSingleItemJson(jsonText)
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun analyzeRoomSweepFrames(frameFiles: List<File>, locationName: String): Result<List<SingleItemScanResult>> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) return@withContext Result.failure(IllegalStateException("Gemini API key is missing."))

        try {
            val prompt = "You are Home AI's room sweep inspector. Location being scanned: $locationName. " +
                    "Analyze these keyframes from the sweep. Identify individual household possessions visible in the space. " +
                    "Group observations and identify each distinct item. " +
                    "Return ONLY valid JSON array with objects matching: " +
                    "[{\"name\":\"Item name\",\"brand\":\"Brand or null\",\"model\":\"Model or null\",\"category\":\"Category\",\"condition\":\"Condition\",\"confidence\":0.9,\"description\":\"Where it was in the room\"}]"

            val contents = JSONArray().apply {
                val content = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                        // Include up to 4 representative frames
                        frameFiles.take(4).forEach { file ->
                            val b64 = encodeImageToBase64(file, maxDimension = 1024)
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", b64)
                                })
                            })
                        }
                    }
                    put("parts", parts)
                }
                put(content)
            }

            val requestJson = JSONObject().apply {
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/${AIConfiguration.MODEL_FAST_VISION}:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("Sweep analysis error (${response.code}): $err"))
                }
                val respStr = response.body?.string() ?: ""
                val jsonText = parseCandidateText(respStr)
                recordUsage()

                val items = parseSweepItemsJson(jsonText)
                Result.success(items)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun answerAssistantQuery(userPrompt: String, inventoryContext: String): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No Gemini API key configured. You can search inventory offline, or add a key in Settings for AI answers."))
        }

        try {
            val systemInstructions = "You are 'Home AI', the user's private household inventory & memory assistant. " +
                    "Your tagline is 'Your home, remembered.' " +
                    "Answer the user's questions about where their items are, what they own, previous fixes, and manuals. " +
                    "STRICT RULES:\n" +
                    "- Ground your answers ONLY in the provided Household Inventory Context below.\n" +
                    "- If an item's location is known, state it directly and mention when/where it was last seen.\n" +
                    "- If you are not sure or information is missing, admit it politely. Never fabricate an exact shelf, drawer, or serial number.\n" +
                    "- For troubleshooting, mention past recorded solutions if present in context.\n" +
                    "- Keep answers concise, natural, warm, and helpful."

            val prompt = "Household Inventory Context:\n$inventoryContext\n\nUser Question:\n$userPrompt"

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val content = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", parts)
                    }
                    put(content)
                }
                put("contents", contents)
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", systemInstructions) })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/${AIConfiguration.MODEL_FAST_VISION}:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("Assistant query error (${response.code}): $err"))
                }
                val respStr = response.body?.string() ?: ""
                val text = parseCandidateText(respStr)
                recordUsage()
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun parseItemFromConversation(userMessage: String, availableLocations: String): Result<SingleItemScanResult> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) return@withContext Result.failure(IllegalStateException("Gemini API key missing."))

        try {
            val prompt = buildString {
                append("You are Home AI's inventory conversational ingestion assistant. ")
                append("The user wants to add an item to their household inventory through conversation. ")
                append("User message: \"$userMessage\". ")
                append("Available locations in home: $availableLocations. ")
                append("Extract the item details into valid JSON: ")
                append("{\n")
                append("  \"name\": \"Recognizable item name\",\n")
                append("  \"brand\": \"Brand or null\",\n")
                append("  \"manufacturer\": \"Manufacturer or null\",\n")
                append("  \"model\": \"Model or null\",\n")
                append("  \"modelNumber\": \"Model number or null\",\n")
                append("  \"serialNumber\": \"Serial number or null\",\n")
                append("  \"barcode\": \"Barcode or null\",\n")
                append("  \"category\": \"Category (Tools, Electronics, Kitchen, Storage, Office, Home, Hardware, Other)\",\n")
                append("  \"subcategory\": \"Subcategory or null\",\n")
                append("  \"quantity\": 1,\n")
                append("  \"condition\": \"New, Good, Excellent, Fair, Poor\",\n")
                append("  \"description\": \"Description including user details\",\n")
                append("  \"visibleText\": [],\n")
                append("  \"accessories\": [],\n")
                append("  \"confidence\": 0.95,\n")
                append("  \"aiSummary\": \"Added via conversational AI\"\n")
                append("}")
            }

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val content = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", parts)
                    }
                    put(content)
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/${AIConfiguration.MODEL_FAST_VISION}:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string() ?: ""
                    return@withContext Result.failure(Exception("Gemini error (${response.code}): $err"))
                }
                val respStr = response.body?.string() ?: ""
                val jsonText = parseCandidateText(respStr)
                recordUsage()
                val result = parseSingleItemJson(jsonText)
                Result.success(result)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun parseMemoryIntent(prompt: String): JSONObject? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) return@withContext null

        try {
            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val content = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", parts)
                    }
                    put(content)
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/${AIConfiguration.MODEL_FAST_VISION}:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val respStr = response.body?.string() ?: ""
                val jsonText = parseCandidateText(respStr).trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                recordUsage()
                JSONObject(jsonText)
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun troubleshootItem(itemName: String, problemDesc: String, pastHistory: String?): Result<TroubleshootAdvice> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) return@withContext Result.failure(IllegalStateException("Gemini API key missing."))

        try {
            val prompt = buildString {
                append("You are Home AI's household technician. Item: $itemName. ")
                append("Problem reported: $problemDesc. ")
                if (!pastHistory.isNullOrEmpty()) {
                    append("Past troubleshooting history on this item:\n$pastHistory\n")
                }
                append("Provide safe, step-by-step troubleshooting suggestions. ")
                append("WARNING: For mains electricity, gas, or dangerous high-voltage/structural issues, advise caution. ")
                append("Return valid JSON: {\"summary\":\"Brief summary\",\"steps\":[\"step 1\",\"step 2\"],\"caution\":\"Safety caution or null\"}")
            }

            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val content = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        }
                        put("parts", parts)
                    }
                    put(content)
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.2)
                })
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/${AIConfiguration.MODEL_FAST_VISION}:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Troubleshoot error: ${response.code}"))
                }
                val json = JSONObject(parseCandidateText(response.body?.string() ?: "{}"))
                recordUsage()
                val stepsList = mutableListOf<String>()
                val stepsArr = json.optJSONArray("steps")
                if (stepsArr != null) {
                    for (i in 0 until stepsArr.length()) stepsList.add(stepsArr.getString(i))
                }
                Result.success(
                    TroubleshootAdvice(
                        summary = json.optString("summary", "Troubleshooting guide"),
                        steps = stepsList,
                        caution = json.optString("caution", null)
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateEmbedding(text: String): Result<List<Float>> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) return@withContext Result.failure(IllegalStateException("Gemini API key missing."))

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/${AIConfiguration.MODEL_EMBEDDINGS}:embedContent?key=$apiKey"
            val requestJson = JSONObject().apply {
                put("model", "models/${AIConfiguration.MODEL_EMBEDDINGS}")
                put("content", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", text) })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Embedding error: ${response.code}"))
                }
                val respStr = response.body?.string() ?: ""
                val root = JSONObject(respStr)
                val embeddingObj = root.optJSONObject("embedding")
                val valuesArr = embeddingObj?.optJSONArray("values")
                if (valuesArr != null) {
                    val floats = mutableListOf<Float>()
                    for (i in 0 until valuesArr.length()) {
                        floats.add(valuesArr.getDouble(i).toFloat())
                    }
                    recordUsage()
                    Result.success(floats)
                } else {
                    Result.failure(Exception("Invalid embedding response format"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseCandidateText(rawJson: String): String {
        val root = JSONObject(rawJson)
        val candidates = root.optJSONArray("candidates") ?: return ""
        if (candidates.length() == 0) return ""
        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        if (parts.length() == 0) return ""
        return parts.getJSONObject(0).optString("text", "")
    }

    private fun parseSingleItemJson(jsonText: String): SingleItemScanResult {
        val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val obj = JSONObject(cleaned)

        val visibleTextList = mutableListOf<String>()
        obj.optJSONArray("visibleText")?.let { arr ->
            for (i in 0 until arr.length()) visibleTextList.add(arr.getString(i))
        }

        val accessoriesList = mutableListOf<String>()
        obj.optJSONArray("accessories")?.let { arr ->
            for (i in 0 until arr.length()) accessoriesList.add(arr.getString(i))
        }

        return SingleItemScanResult(
            name = obj.optString("name", "Unidentified Item"),
            brand = obj.optString("brand", "").ifEmpty { null },
            manufacturer = obj.optString("manufacturer", "").ifEmpty { null },
            model = obj.optString("model", "").ifEmpty { null },
            modelNumber = obj.optString("modelNumber", "").ifEmpty { null },
            serialNumber = obj.optString("serialNumber", "").ifEmpty { null },
            barcode = obj.optString("barcode", "").ifEmpty { null },
            category = obj.optString("category", "General"),
            subcategory = obj.optString("subcategory", "").ifEmpty { null },
            quantity = obj.optInt("quantity", 1),
            condition = obj.optString("condition", "Good"),
            description = obj.optString("description", "Scanned household item"),
            visibleText = visibleTextList,
            accessories = accessoriesList,
            confidence = obj.optDouble("confidence", 0.9).toFloat(),
            aiSummary = obj.optString("aiSummary", "Identified via Gemini vision analysis")
        )
    }

    private fun parseSweepItemsJson(jsonText: String): List<SingleItemScanResult> {
        val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val list = mutableListOf<SingleItemScanResult>()
        val arr = JSONArray(cleaned)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                SingleItemScanResult(
                    name = obj.optString("name", "Detected Item"),
                    brand = obj.optString("brand", "").ifEmpty { null },
                    manufacturer = null,
                    model = obj.optString("model", "").ifEmpty { null },
                    modelNumber = null,
                    serialNumber = null,
                    barcode = null,
                    category = obj.optString("category", "General"),
                    subcategory = null,
                    quantity = 1,
                    condition = obj.optString("condition", "Good"),
                    description = obj.optString("description", "Identified during room sweep"),
                    visibleText = emptyList(),
                    accessories = emptyList(),
                    confidence = obj.optDouble("confidence", 0.85).toFloat(),
                    aiSummary = "Observed during room sweep"
                )
            )
        }
        return list
    }

    private fun encodeImageToBase64(file: File, maxDimension: Int): String {
        val original = BitmapFactory.decodeFile(file.absolutePath) ?: throw IllegalArgumentException("Cannot decode image")
        var width = original.width
        var height = original.height

        val scaled: Bitmap = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            if (width > height) {
                width = maxDimension
                height = (maxDimension / ratio).toInt()
            } else {
                height = maxDimension
                width = (maxDimension * ratio).toInt()
            }
            Bitmap.createScaledBitmap(original, width, height, true)
        } else {
            original
        }

        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }
}

data class SingleItemScanResult(
    val name: String,
    val brand: String?,
    val manufacturer: String?,
    val model: String?,
    val modelNumber: String?,
    val serialNumber: String?,
    val barcode: String?,
    val category: String,
    val subcategory: String?,
    val quantity: Int,
    val condition: String?,
    val description: String?,
    val visibleText: List<String>,
    val accessories: List<String>,
    val confidence: Float,
    val aiSummary: String?
)

data class TroubleshootAdvice(
    val summary: String,
    val steps: List<String>,
    val caution: String?
)
