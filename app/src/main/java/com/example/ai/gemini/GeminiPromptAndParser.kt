package com.example.ai.gemini

import org.json.JSONArray
import org.json.JSONObject

object GeminiPromptBuilder {

    fun buildSingleItemScanPrompt(userContextHint: String? = null): String {
        return buildString {
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
    }

    fun buildRoomSweepPrompt(locationName: String): String {
        return "You are Home AI's room sweep inspector. Location being scanned: $locationName. " +
                "Analyze these keyframes from the sweep. Identify individual household possessions visible in the space. " +
                "Group observations and identify each distinct item. " +
                "Return ONLY valid JSON array with objects matching: " +
                "[{\"name\":\"Item name\",\"brand\":\"Brand or null\",\"model\":\"Model or null\",\"category\":\"Category\",\"condition\":\"Condition\",\"confidence\":0.9,\"description\":\"Where it was in the room\"}]"
    }

    fun buildAssistantSystemInstructions(): String {
        return "You are 'Home AI', the user's private household inventory & memory assistant. " +
                "Your tagline is 'Your home, remembered.' " +
                "Answer the user's questions about where their items are, what they own, previous fixes, and manuals. " +
                "STRICT RULES:\n" +
                "- Ground your answers ONLY in the provided Household Inventory Context below.\n" +
                "- If an item's location is known, state it directly and mention when/where it was last seen.\n" +
                "- If you are not sure or information is missing, admit it politely. Never fabricate an exact shelf, drawer, or serial number.\n" +
                "- For troubleshooting, mention past recorded solutions if present in context.\n" +
                "- Keep answers concise, natural, warm, and helpful."
    }

    fun buildAssistantUserPrompt(userPrompt: String, inventoryContext: String): String {
        return "Household Inventory Context:\n$inventoryContext\n\nUser Question:\n$userPrompt"
    }

    fun buildConversationItemParsePrompt(userMessage: String, availableLocations: String): String {
        return buildString {
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
    }

    fun buildTroubleshootPrompt(itemName: String, problemDesc: String, pastHistory: String?): String {
        return buildString {
            append("You are Home AI's household technician. Item: $itemName. ")
            append("Problem reported: $problemDesc. ")
            if (!pastHistory.isNullOrEmpty()) {
                append("Past troubleshooting history on this item:\n$pastHistory\n")
            }
            append("Provide safe, step-by-step troubleshooting suggestions. ")
            append("WARNING: For mains electricity, gas, or dangerous high-voltage/structural issues, advise caution. ")
            append("Return valid JSON: {\"summary\":\"Brief summary\",\"steps\":[\"step 1\",\"step 2\"],\"caution\":\"Safety caution or null\"}")
        }
    }

    fun buildResearchPrompt(
        name: String,
        brand: String?,
        model: String?,
        modelNumber: String?,
        category: String?
    ): String {
        return buildString {
            append("You are Home AI's Deep Hardware, Appliance, and Electronics Research Specialist.\n")
            append("Conduct comprehensive research on this household item by synthesizing data from official manufacturer documentation and 3-5 popular consumer/enthusiast forums (e.g. Reddit r/Tools or r/HomeImprovement, iFixit repair guides, YouTube workshop teardowns, brand community boards, Consumer Reports).\n\n")
            append("Item Name: $name\n")
            if (!brand.isNullOrEmpty()) append("Brand: $brand\n")
            if (!model.isNullOrEmpty()) append("Model: $model\n")
            if (!modelNumber.isNullOrEmpty()) append("Model Number: $modelNumber\n")
            if (!category.isNullOrEmpty()) append("Category: $category\n")
            append("\nCRITICAL SPEED & RESILIENCE RULES:\n")
            append("- Synthesize immediately without stalling or deep-crawling timeouts.\n")
            append("- If direct manufacturer manuals or specific forum threads for this exact sub-model are unindexed or unavailable, synthesize immediately using brand-line and equipment-class engineering knowledge.\n")
            append("- Never leave fields empty if engineering consensus exists for this class of device; always fill out whatever is known.\n")
            append("- Formulate a thoughtful, intellectual synthesis note in 'intellectualInsight' summarizing the technical pedigree, design ethos, and preventative maintenance rationale for this hardware.\n\n")
            append("Provide structured intelligence in valid JSON:\n")
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
            append("  \"safetyNotes\": \"Crucial safety or precaution notes\",\n")
            append("  \"intellectualInsight\": \"Articulate engineering synthesis note on this equipment's operational durability and maintenance rationale\"\n")
            append("}\n")
        }
    }
}

object GeminiParser {

    val KNOWN_BRANDS = listOf(
        "DeWalt", "Milwaukee", "Makita", "Ryobi", "Bosch", "Craftsman", "Black & Decker", "Stanley", "Kobalt", "Husky",
        "Sony", "Samsung", "Apple", "LG", "Bose", "Anker", "Logitech", "HP", "Dell", "Lenovo", "Canon", "Nikon",
        "Dyson", "KitchenAid", "Ninja", "Cuisinart", "Keurig", "Nespresso", "Instant Pot", "Breville", "Philips",
        "IKEA", "Nike", "Adidas", "Casio", "Garmin", "Fitbit", "Southwire", "Eneloop", "Panasonic"
    )

    fun cleanMarkdownFences(text: String): String {
        var cleaned = text.trim()
        if (cleaned.startsWith("```json", ignoreCase = true)) {
            cleaned = cleaned.substring(7).trim()
        } else if (cleaned.startsWith("```", ignoreCase = true)) {
            cleaned = cleaned.substring(3).trim()
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length - 3).trim()
        }
        return cleaned
    }

    fun extractJsonObjectString(text: String): String {
        val cleaned = cleanMarkdownFences(text)
        val firstBrace = cleaned.indexOf('{')
        val lastBrace = cleaned.lastIndexOf('}')
        return if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned.substring(firstBrace, lastBrace + 1)
        } else {
            cleaned
        }
    }

    fun extractJsonArrayString(text: String): String {
        val cleaned = cleanMarkdownFences(text)
        val firstBracket = cleaned.indexOf('[')
        val lastBracket = cleaned.lastIndexOf(']')
        return if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket) {
            cleaned.substring(firstBracket, lastBracket + 1)
        } else {
            cleaned
        }
    }

    fun parseCandidateText(rawJson: String): String {
        if (rawJson.isBlank()) return ""
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            return rawJson
        }
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

    fun parseSingleItemJson(jsonText: String): SingleItemScanResult {
        val extractedJson = extractJsonObjectString(jsonText)
        val obj = JSONObject(extractedJson)

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
        if (brand.isNullOrEmpty()) {
            for (vt in visibleTextList) {
                val found = KNOWN_BRANDS.find { vt.contains(it, ignoreCase = true) }
                if (found != null) {
                    brand = found
                    break
                }
            }
        }

        if (brand.isNullOrEmpty()) {
            val foundInName = KNOWN_BRANDS.find { rawName.contains(it, ignoreCase = true) }
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

        // Clean up name if it contains generic placeholders
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

    fun parseSweepItemsJson(jsonText: String): List<SingleItemScanResult> {
        val extractedJson = extractJsonArrayString(jsonText)
        val list = mutableListOf<SingleItemScanResult>()
        val arr = JSONArray(extractedJson)
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

    fun parseItemResearchJson(jsonText: String): ItemResearchResult {
        val extractedJson = extractJsonObjectString(jsonText)
        val obj = JSONObject(extractedJson)

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
        val intellectualInsight = obj.optSafe("intellectualInsight")

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
            safetyNotes = safetyNotes,
            intellectualInsight = intellectualInsight
        )
    }

    fun parseTroubleshootJson(jsonText: String): TroubleshootAdvice {
        val extractedJson = extractJsonObjectString(jsonText)
        val obj = JSONObject(extractedJson)
        val summary = obj.optString("summary", "Troubleshooting guide")
        val caution = if (obj.isNull("caution")) null else obj.optString("caution", "").takeIf { it.isNotBlank() }
        val steps = mutableListOf<String>()
        obj.optJSONArray("steps")?.let { arr ->
            for (i in 0 until arr.length()) {
                val s = arr.optString(i, "").trim()
                if (s.isNotEmpty()) steps.add(s)
            }
        }
        return TroubleshootAdvice(
            summary = summary,
            steps = steps,
            caution = caution
        )
    }
}
