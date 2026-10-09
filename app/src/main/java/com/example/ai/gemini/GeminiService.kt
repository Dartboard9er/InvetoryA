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
    const val MODEL_FALLBACK_VISION = "gemini-2.5-flash"
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
        val userKey = prefs.getString("user_gemini_api_key", null)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")
        if (!userKey.isNullOrEmpty()) return userKey
        // Fallback to BuildConfig if provided in .env
        val buildKey = BuildConfig.GEMINI_API_KEY.trim().removeSurrounding("\"").removeSurrounding("'")
        if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }

    fun saveApiKey(key: String) {
        val cleaned = key.trim().removeSurrounding("\"").removeSurrounding("'").removePrefix("key=").trim()
        prefs.edit().putString("user_gemini_api_key", cleaned).apply()
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
                append("You are Home AI's expert visual household inventory analyzer. ")
                append("Your primary mission is to examine this user's photo and AUTOMATICALLY identify the object and extract all details so the user does NOT need to fill them in manually. ")
                append("Identify:\n")
                append("1. 'name': Clear, descriptive, specific product title (e.g. 'Sony WH-1000XM4 Wireless Headphones', 'DeWalt 20V Max Cordless Drill', 'Stainless Steel French Press'). Never return generic placeholders like 'Item', 'Object', or 'Unknown'. Describe what is shown.\n")
                append("2. 'brand': The brand, manufacturer, or company name if visible from logos, badges, embossed text, or recognizable product design (e.g., DeWalt, Sony, Apple, Samsung, Bosch, Milwaukee, Craftsman, IKEA, Ninja, Dyson). If no brand is visible, infer the manufacturer or return null.\n")
                append("3. 'model': The specific model name or series (e.g. 'WH-1000XM4', 'DCD791', 'Series 7', 'AirPods Pro 2nd Gen').\n")
                append("4. 'modelNumber': The alphanumeric model number if printed on label, rating plate, or casing.\n")
                append("5. 'serialNumber': The unique serial number if printed, engraved, or visible near a barcode.\n")
                append("6. 'barcode': Barcode digits if visible on sticker.\n")
                append("7. 'category': Pick the best match (Tools, Electronics, Kitchen, Storage, Office, Home, Hardware, Sports, Outdoor, Other).\n")
                append("8. 'condition': Visual condition based on wear/scratches (New, Excellent, Good, Fair, Poor).\n")
                append("9. 'description': 1-3 sentences describing the item's visual color, material, physical state, port/switch layouts, and distinguishing markings.\n")
                append("10. 'visibleText': List all legible words, brand names, model numbers, voltage, or specs visible on the item or labels.\n")
                append("11. 'accessories': List any visible cables, attachments, cases, batteries, or parts.\n")
                if (!userContextHint.isNullOrEmpty()) {
                    append("User context hint: $userContextHint.\n")
                }
                append("Respond ONLY with a JSON object in this exact format:\n")
                append("{\n")
                append("  \"name\": \"Specific Product Name\",\n")
                append("  \"brand\": \"Brand Name or null\",\n")
                append("  \"manufacturer\": \"Manufacturer Name or null\",\n")
                append("  \"model\": \"Model Name or null\",\n")
                append("  \"modelNumber\": \"Model Number or null\",\n")
                append("  \"serialNumber\": \"Serial Number or null\",\n")
                append("  \"barcode\": \"Barcode digits or null\",\n")
                append("  \"category\": \"Category\",\n")
                append("  \"subcategory\": null,\n")
                append("  \"quantity\": 1,\n")
                append("  \"condition\": \"Condition\",\n")
                append("  \"description\": \"Visual description\",\n")
                append("  \"visibleText\": [\"text 1\", \"text 2\"],\n")
                append("  \"accessories\": [],\n")
                append("  \"confidence\": 0.95,\n")
                append("  \"aiSummary\": \"Summary of identified details\"\n")
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
                    // Try fallback model if fast vision had temporary issues
                    val fallbackResult = tryFallbackModels(apiKey, requestJson)
                    if (fallbackResult != null) {
                        recordUsage()
                        return@withContext Result.success(fallbackResult)
                    }
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

    private fun tryFallbackModels(apiKey: String, requestJson: JSONObject): SingleItemScanResult? {
        val fallbackModels = listOf(AIConfiguration.MODEL_FALLBACK_VISION, AIConfiguration.MODEL_REASONING, "gemini-flash-latest")
        for (model in fallbackModels) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
                val request = Request.Builder()
                    .url(url)
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val text = parseCandidateText(body)
                        if (text.isNotBlank()) {
                            return parseSingleItemJson(text)
                        }
                    }
                }
            } catch (_: Exception) {
                // Continue to next fallback
            }
        }
        return null
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
                        caution = if (json.has("caution")) json.optString("caution") else null
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

        val textBuilder = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            // Skip thought parts generated by reasoning models
            if (part.optBoolean("thought", false)) continue
            val t = part.optString("text", "")
            if (t.isNotEmpty()) {
                textBuilder.append(t)
            }
        }
        val result = textBuilder.toString()
        if (result.isNotBlank()) return result
        return parts.getJSONObject(0).optString("text", "")
    }

    private fun parseSingleItemJson(jsonText: String): SingleItemScanResult {
        var cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1)
        }
        val obj = JSONObject(cleaned)

        fun JSONObject.optSafeString(key: String): String? {
            if (isNull(key)) return null
            val s = optString(key, "").trim()
            if (s.isEmpty() || s.equals("null", ignoreCase = true) || s.equals("none", ignoreCase = true)) return null
            return s
        }

        val visibleTextList = mutableListOf<String>()
        obj.optJSONArray("visibleText")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty() && !s.equals("null", ignoreCase = true)) {
                    visibleTextList.add(s)
                }
            }
        }

        val accessoriesList = mutableListOf<String>()
        obj.optJSONArray("accessories")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty() && !s.equals("null", ignoreCase = true)) {
                    accessoriesList.add(s)
                }
            }
        }

        var rawName = obj.optSafeString("name") ?: "Household Item"
        var brand = obj.optSafeString("brand")
        val manufacturer = obj.optSafeString("manufacturer")
        var model = obj.optSafeString("model")
        var modelNumber = obj.optSafeString("modelNumber")
        var serialNumber = obj.optSafeString("serialNumber")
        val barcode = obj.optSafeString("barcode")
        val category = obj.optSafeString("category") ?: "General"
        val subcategory = obj.optSafeString("subcategory")
        val quantity = obj.optInt("quantity", 1).coerceAtLeast(1)
        val condition = obj.optSafeString("condition") ?: "Good"
        val description = obj.optSafeString("description") ?: "Item visual details"
        val confidence = obj.optDouble("confidence", 0.95).toFloat()
        val aiSummary = obj.optSafeString("aiSummary") ?: "Item details extracted by Home AI vision analysis"

        // Brand heuristic extraction from visible text or name if brand is missing
        val knownBrands = listOf(
            "DeWalt", "Milwaukee", "Makita", "Ryobi", "Bosch", "Craftsman", "Black & Decker", "Stanley", "Kobalt", "Husky",
            "Sony", "Samsung", "Apple", "LG", "Bose", "Anker", "Logitech", "HP", "Dell", "Lenovo", "Canon", "Nikon",
            "Dyson", "KitchenAid", "Ninja", "Cuisinart", "Keurig", "Nespresso", "Instant Pot", "Breville", "Philips",
            "IKEA", "Nike", "Adidas", "Casio", "Garmin", "Fitbit", "Southwire", "Eneloop", "Panasonic"
        )

        if (brand.isNullOrEmpty()) {
            for (vt in visibleTextList) {
                val found = knownBrands.find { vt.contains(it, ignoreCase = true) }
                if (found != null) {
                    brand = found
                    break
                }
            }
        }

        if (brand.isNullOrEmpty()) {
            val foundInName = knownBrands.find { rawName.contains(it, ignoreCase = true) }
            if (foundInName != null) {
                brand = foundInName
            }
        }

        // Model & Model Number heuristic extraction from visible text
        if (modelNumber.isNullOrEmpty()) {
            for (vt in visibleTextList) {
                val clean = vt.trim()
                val prefix = listOf("MODEL NO:", "MODEL NUMBER:", "MODEL:", "MOD NO:", "MOD:", "M/N:")
                    .find { clean.startsWith(it, ignoreCase = true) }
                if (prefix != null) {
                    modelNumber = clean.substring(prefix.length).trim()
                    if (model.isNullOrEmpty()) model = modelNumber
                    break
                }
            }
        }

        if (model.isNullOrEmpty() && modelNumber.isNullOrEmpty()) {
            for (vt in visibleTextList) {
                // If visible text looks like a model code (has digits and uppercase letters, length 4-16)
                val clean = vt.trim()
                if (clean.any { it.isDigit() } && clean.any { it.isLetter() } && clean.length in 4..16 && !clean.contains("BARCODE", ignoreCase = true)) {
                    model = clean
                    break
                }
            }
        }

        // Serial Number heuristic extraction from visible text if missing
        if (serialNumber.isNullOrEmpty()) {
            for (vt in visibleTextList) {
                val clean = vt.trim()
                val prefix = listOf("SERIAL NO:", "SERIAL NUMBER:", "SERIAL:", "SER NO:", "SER:", "S/N:", "SN:")
                    .find { clean.startsWith(it, ignoreCase = true) }
                if (prefix != null) {
                    serialNumber = clean.substring(prefix.length).trim()
                    break
                }
            }
        }

        // Barcode extraction from visible text if missing
        var finalBarcode = barcode
        if (finalBarcode.isNullOrEmpty()) {
            for (vt in visibleTextList) {
                val clean = vt.trim()
                if (clean.startsWith("BARCODE:", ignoreCase = true)) {
                    finalBarcode = clean.substringAfter(":").trim()
                    break
                }
            }
        }

        // Clean up name if it contains generic phrases or infer from brand + model
        if (rawName.equals("Household Item", ignoreCase = true) ||
            rawName.equals("Scanned Item", ignoreCase = true) ||
            rawName.equals("Detected Item", ignoreCase = true) ||
            rawName.contains("No item", ignoreCase = true) ||
            rawName.contains("Unidentified", ignoreCase = true)) {
            rawName = when {
                !brand.isNullOrEmpty() && !model.isNullOrEmpty() -> "$brand $model"
                !brand.isNullOrEmpty() -> "$brand Item"
                !model.isNullOrEmpty() -> model
                else -> "Household Item"
            }
        }

        return SingleItemScanResult(
            name = rawName,
            brand = brand,
            manufacturer = manufacturer ?: brand,
            model = model,
            modelNumber = modelNumber,
            serialNumber = serialNumber,
            barcode = finalBarcode,
            category = category,
            subcategory = subcategory,
            quantity = quantity,
            condition = condition,
            description = description,
            visibleText = visibleTextList,
            accessories = accessoriesList,
            confidence = confidence,
            aiSummary = aiSummary
        )
    }

    private fun parseSweepItemsJson(jsonText: String): List<SingleItemScanResult> {
        val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val list = mutableListOf<SingleItemScanResult>()
        val arr = JSONArray(cleaned)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            fun JSONObject.optSafe(k: String): String? {
                if (isNull(k)) return null
                val v = optString(k, "").trim()
                return if (v.isEmpty() || v.equals("null", ignoreCase = true)) null else v
            }
            list.add(
                SingleItemScanResult(
                    name = obj.optSafe("name") ?: "Detected Item",
                    brand = obj.optSafe("brand"),
                    manufacturer = null,
                    model = obj.optSafe("model"),
                    modelNumber = null,
                    serialNumber = null,
                    barcode = null,
                    category = obj.optSafe("category") ?: "General",
                    subcategory = null,
                    quantity = 1,
                    condition = obj.optSafe("condition") ?: "Good",
                    description = obj.optSafe("description") ?: "Identified during room sweep",
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
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) throw IllegalArgumentException("Cannot decode image bounds from ${file.name}")

        var sampleSize = 1
        while (origW / (sampleSize * 2) >= maxDimension || origH / (sampleSize * 2) >= maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val sampledBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            ?: throw IllegalArgumentException("Cannot decode image bitmap from ${file.name}")

        // Handle EXIF orientation rotation
        var rotatedBitmap = sampledBitmap
        try {
            val exif = android.media.ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION,
                android.media.ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = android.graphics.Matrix()
            when (orientation) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            if (orientation != android.media.ExifInterface.ORIENTATION_NORMAL && orientation != android.media.ExifInterface.ORIENTATION_UNDEFINED) {
                rotatedBitmap = Bitmap.createBitmap(sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true)
            }
        } catch (_: Exception) {
            // Ignore EXIF errors and keep sampledBitmap
        }

        val width = rotatedBitmap.width
        val height = rotatedBitmap.height

        val finalBitmap: Bitmap = if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            val targetW = if (width > height) maxDimension else (maxDimension * ratio).toInt()
            val targetH = if (width > height) (maxDimension / ratio).toInt() else maxDimension
            Bitmap.createScaledBitmap(rotatedBitmap, targetW.coerceAtLeast(1), targetH.coerceAtLeast(1), true)
        } else {
            rotatedBitmap
        }

        val output = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun researchItemIntel(
        name: String,
        brand: String?,
        model: String?,
        modelNumber: String?,
        category: String?
    ): Result<ItemResearchResult> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Gemini API key is missing."))
        }

        try {
            val prompt = buildString {
                append("You are Home AI's Deep Hardware, Appliance, and Electronics Research Specialist.\n")
                append("Conduct comprehensive research on this household item by synthesizing data from official manufacturer documentation and 3-5 popular consumer/enthusiast forums (e.g. Reddit r/Tools or r/HomeImprovement, iFixit repair guides, YouTube workshop teardowns, brand community boards, Consumer Reports).\n\n")
                append("Item Name: $name\n")
                if (!brand.isNullOrEmpty()) append("Brand: $brand\n")
                if (!model.isNullOrEmpty()) append("Model: $model\n")
                if (!modelNumber.isNullOrEmpty()) append("Model Number: $modelNumber\n")
                if (!category.isNullOrEmpty()) append("Category: $category\n")
                append("\nProvide structured intelligence in valid JSON:\n")
                append("{\n")
                append("  \"manualTitle\": \"Official manual title or document guide\",\n")
                append("  \"manualUrl\": \"https://www.manufacturer.com/support or official manual link\",\n")
                append("  \"manualSummary\": \"Summary of official operating guidelines, key specs, and maintenance instructions from manual\",\n")
                append("  \"forumSources\": [\"Forum/Website 1\", \"Forum/Website 2\", \"Forum/Website 3\", \"Forum/Website 4\"],\n")
                append("  \"commonIssues\": [\n")
                append("    {\n")
                append("      \"issue\": \"Common issue name\",\n")
                append("      \"symptom\": \"How it presents / warning signs\",\n")
                append("      \"solution\": \"Community verified fix or preventative action\",\n")
                append("      \"source\": \"Specific forum or repair community\"\n")
                append("    }\n")
                append("  ],\n")
                append("  \"proTips\": [\n")
                append("    \"Enthusiast tip 1\",\n")
                append("    \"Enthusiast tip 2\",\n")
                append("    \"Enthusiast tip 3\"\n")
                append("  ],\n")
                append("  \"maintenanceTasks\": [\n")
                append("    {\n")
                append("      \"title\": \"Maintenance task title\",\n")
                append("      \"intervalDays\": 90,\n")
                append("      \"description\": \"Detailed step-by-step instructions\"\n")
                append("    }\n")
                append("  ],\n")
                append("  \"recommendedParts\": [\"Part name or number 1\", \"Part name or number 2\"],\n")
                append("  \"specsSummary\": \"Key technical specifications summary\",\n")
                append("  \"safetyNotes\": \"Crucial safety or precaution notes\"\n")
                append("}\n")
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

            val primaryModel = AIConfiguration.MODEL_FAST_VISION
            val fallbackModel = AIConfiguration.MODEL_FALLBACK_VISION
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$primaryModel:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            var responseBody: String? = null
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    responseBody = response.body?.string()
                }
            }

            if (responseBody == null) {
                // Try fallback model
                val fallbackUrl = "https://generativelanguage.googleapis.com/v1beta/models/$fallbackModel:generateContent?key=$apiKey"
                val fallbackRequest = Request.Builder()
                    .url(fallbackUrl)
                    .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(fallbackRequest).execute().use { fbResponse ->
                    if (fbResponse.isSuccessful) {
                        responseBody = fbResponse.body?.string()
                    }
                }
            }

            if (responseBody.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Could not retrieve research data from Gemini."))
            }

            val text = parseCandidateText(responseBody!!)
            recordUsage()
            val research = parseItemResearchJson(text)
            Result.success(research)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseItemResearchJson(jsonText: String): ItemResearchResult {
        var cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1)
        }
        val obj = JSONObject(cleaned)

        fun JSONObject.optSafe(k: String): String? {
            if (isNull(k)) return null
            val v = optString(k, "").trim()
            return if (v.isEmpty() || v.equals("null", ignoreCase = true)) null else v
        }

        val manualTitle = obj.optSafe("manualTitle")
        val manualUrl = obj.optSafe("manualUrl")
        val manualSummary = obj.optSafe("manualSummary")
        val specsSummary = obj.optSafe("specsSummary")
        val safetyNotes = obj.optSafe("safetyNotes")

        val forumSources = mutableListOf<String>()
        obj.optJSONArray("forumSources")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty()) forumSources.add(s)
            }
        }

        val proTips = mutableListOf<String>()
        obj.optJSONArray("proTips")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty()) proTips.add(s)
            }
        }

        val recommendedParts = mutableListOf<String>()
        obj.optJSONArray("recommendedParts")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty()) recommendedParts.add(s)
            }
        }

        val commonIssues = mutableListOf<IssueTipItem>()
        obj.optJSONArray("commonIssues")?.let { arr ->
            for (i in 0 until arr.length()) {
                val itemObj = arr.optJSONObject(i) ?: continue
                commonIssues.add(
                    IssueTipItem(
                        issue = itemObj.optSafe("issue") ?: "Common Issue",
                        symptom = itemObj.optSafe("symptom"),
                        solution = itemObj.optSafe("solution"),
                        source = itemObj.optSafe("source")
                    )
                )
            }
        }

        val maintenanceTasks = mutableListOf<ResearchMaintenanceTask>()
        obj.optJSONArray("maintenanceTasks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val taskObj = arr.optJSONObject(i) ?: continue
                maintenanceTasks.add(
                    ResearchMaintenanceTask(
                        title = taskObj.optSafe("title") ?: "Maintenance Check",
                        intervalDays = taskObj.optInt("intervalDays", 90).coerceAtLeast(15),
                        description = taskObj.optSafe("description") ?: "Routine equipment maintenance"
                    )
                )
            }
        }

        return ItemResearchResult(
            manualTitle = manualTitle,
            manualUrl = manualUrl,
            manualSummary = manualSummary,
            forumSources = forumSources,
            commonIssues = commonIssues,
            proTips = proTips,
            maintenanceTasks = maintenanceTasks,
            recommendedParts = recommendedParts,
            specsSummary = specsSummary,
            safetyNotes = safetyNotes
        )
    }
}

data class ItemResearchResult(
    val manualTitle: String?,
    val manualUrl: String?,
    val manualSummary: String?,
    val forumSources: List<String>,
    val commonIssues: List<IssueTipItem>,
    val proTips: List<String>,
    val maintenanceTasks: List<ResearchMaintenanceTask>,
    val recommendedParts: List<String>,
    val specsSummary: String?,
    val safetyNotes: String?
)

data class IssueTipItem(
    val issue: String,
    val symptom: String?,
    val solution: String?,
    val source: String?
)

data class ResearchMaintenanceTask(
    val title: String,
    val intervalDays: Int,
    val description: String
)

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
