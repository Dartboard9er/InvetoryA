package com.example

import com.example.ai.gemini.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract tests verifying Gemini AI prompt construction, markdown fence sanitization,
 * response schema parsing, and edge-case fallbacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiPromptContractTest {

    // ==========================================
    // 1. PROMPT BUILDER CONTRACT TESTS
    // ==========================================

    @Test
    fun testSingleItemScanPromptContainsRequiredSchemaAndRules() {
        val promptWithoutHint = GeminiPromptBuilder.buildSingleItemScanPrompt(null)
        assertTrue("Must specify name key", promptWithoutHint.contains("\"name\""))
        assertTrue("Must specify brand key", promptWithoutHint.contains("\"brand\""))
        assertTrue("Must specify model key", promptWithoutHint.contains("\"model\""))
        assertTrue("Must specify modelNumber key", promptWithoutHint.contains("\"modelNumber\""))
        assertTrue("Must specify serialNumber key", promptWithoutHint.contains("\"serialNumber\""))
        assertTrue("Must specify barcode key", promptWithoutHint.contains("\"barcode\""))
        assertTrue("Must specify category key", promptWithoutHint.contains("\"category\""))
        assertTrue("Must specify visibleText key", promptWithoutHint.contains("\"visibleText\""))
        assertTrue("Must specify accessories key", promptWithoutHint.contains("\"accessories\""))
        assertTrue("Must instruct not to return generic placeholders", promptWithoutHint.contains("Never return generic placeholders"))

        val promptWithHint = GeminiPromptBuilder.buildSingleItemScanPrompt("Found in basement tool chest")
        assertTrue("Must include user hint", promptWithHint.contains("User context hint: Found in basement tool chest"))
    }

    @Test
    fun testRoomSweepPromptContainsLocationAndArraySchema() {
        val prompt = GeminiPromptBuilder.buildRoomSweepPrompt("Garage Workbench")
        assertTrue("Must mention location", prompt.contains("Garage Workbench"))
        assertTrue("Must specify JSON array output", prompt.contains("ONLY valid JSON array"))
        assertTrue("Must contain object keys", prompt.contains("\"name\"") && prompt.contains("\"confidence\""))
    }

    @Test
    fun testAssistantPromptGroundingAndAntiHallucinationRules() {
        val instructions = GeminiPromptBuilder.buildAssistantSystemInstructions()
        assertTrue("Must enforce strict grounding rule", instructions.contains("Ground your answers ONLY"))
        assertTrue("Must forbid fabricating locations", instructions.contains("Never fabricate an exact shelf"))
        assertTrue("Must include Home AI tagline", instructions.contains("Your home, remembered."))

        val userPrompt = GeminiPromptBuilder.buildAssistantUserPrompt("Where did I leave my safety glasses?", "Safety Glasses in Garage Cabinet A")
        assertTrue("Must embed inventory context", userPrompt.contains("Household Inventory Context:\nSafety Glasses in Garage Cabinet A"))
        assertTrue("Must embed user question", userPrompt.contains("User Question:\nWhere did I leave my safety glasses?"))
    }

    @Test
    fun testConversationItemParsePromptContract() {
        val prompt = GeminiPromptBuilder.buildConversationItemParsePrompt(
            userMessage = "I bought a Stanley 25ft tape measure for the workshop",
            availableLocations = "Workshop, Garage, Basement"
        )
        assertTrue("Must include user message", prompt.contains("Stanley 25ft tape measure"))
        assertTrue("Must include available locations", prompt.contains("Workshop, Garage, Basement"))
        assertTrue("Must specify valid JSON schema", prompt.contains("\"category\"") && prompt.contains("\"quantity\""))
    }

    @Test
    fun testTroubleshootPromptContract() {
        val promptWithHistory = GeminiPromptBuilder.buildTroubleshootPrompt(
            itemName = "Makita Router",
            problemDesc = "Collet vibrating excessively at high RPM",
            pastHistory = "Previously replaced bearings 6 months ago"
        )
        assertTrue(promptWithHistory.contains("Makita Router"))
        assertTrue(promptWithHistory.contains("Collet vibrating excessively"))
        assertTrue(promptWithHistory.contains("Previously replaced bearings"))
        assertTrue(promptWithHistory.contains("WARNING: For mains electricity"))

        val promptWithoutHistory = GeminiPromptBuilder.buildTroubleshootPrompt(
            itemName = "Toaster",
            problemDesc = "Lever won't stay down",
            pastHistory = null
        )
        assertFalse(promptWithoutHistory.contains("Past troubleshooting history"))
    }

    @Test
    fun testResearchPromptContract() {
        val prompt = GeminiPromptBuilder.buildResearchPrompt(
            name = "DeWalt DCD791",
            brand = "DeWalt",
            model = "DCD791",
            modelNumber = "DCD791B",
            category = "Tools"
        )
        assertTrue(prompt.contains("Item Name: DeWalt DCD791"))
        assertTrue(prompt.contains("Brand: DeWalt"))
        assertTrue(prompt.contains("Model: DCD791"))
        assertTrue(prompt.contains("Model Number: DCD791B"))
        assertTrue(prompt.contains("Category: Tools"))
        assertTrue(prompt.contains("manualTitle") && prompt.contains("manualUrl"))
        assertTrue(prompt.contains("commonIssues") && prompt.contains("maintenanceTasks"))
        assertTrue(prompt.contains("Reddit r/Tools"))
    }

    // ==========================================
    // 2. MARKDOWN FENCE CLEANING & EXTRACTION
    // ==========================================

    @Test
    fun testMarkdownFenceStripping() {
        val jsonWithFences = """
            ```json
            {
              "name": "Sony Headphones",
              "category": "Electronics"
            }
            ```
        """.trimIndent()
        val cleaned = GeminiParser.cleanMarkdownFences(jsonWithFences)
        assertFalse(cleaned.startsWith("```"))
        assertFalse(cleaned.endsWith("```"))
        assertTrue(cleaned.startsWith("{") && cleaned.endsWith("}"))

        val nakedFences = "```\n{\"test\": 1}\n```"
        assertEquals("{\"test\": 1}", GeminiParser.cleanMarkdownFences(nakedFences))

        // Raw JSON without fences should be untouched
        val plain = "{\"simple\": true}"
        assertEquals(plain, GeminiParser.cleanMarkdownFences(plain))
    }

    @Test
    fun testJsonObjectExtractionWithSurroundingChatter() {
        val aiOutputWithChatter = """
            Here is the detected item from your image:
            ```json
            {
                "name": "Bosch Laser Measure",
                "brand": "Bosch",
                "category": "Tools"
            }
            ```
            Hope this helps with your inventory!
        """.trimIndent()

        val extracted = GeminiParser.extractJsonObjectString(aiOutputWithChatter)
        assertTrue(extracted.startsWith("{"))
        assertTrue(extracted.endsWith("}"))
        assertTrue(extracted.contains("\"Bosch Laser Measure\""))
    }

    @Test
    fun testJsonArrayExtractionWithSurroundingChatter() {
        val aiArrayOutput = """
            I found the following items:
            [
              {"name": "Hammer", "category": "Tools"},
              {"name": "Pliers", "category": "Tools"}
            ]
            Done scanning.
        """.trimIndent()

        val extracted = GeminiParser.extractJsonArrayString(aiArrayOutput)
        assertTrue(extracted.startsWith("["))
        assertTrue(extracted.endsWith("]"))
        assertTrue(extracted.contains("Hammer"))
        assertTrue(extracted.contains("Pliers"))
    }

    // ==========================================
    // 3. CANDIDATE TEXT PARSING
    // ==========================================

    @Test
    fun testParseCandidateTextSkipsThoughtParts() {
        val reasoningResponse = """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  {"thought": true, "text": "Analyzing user image. Looks like an impact wrench."},
                  {"text": "{\"name\": \"Milwaukee M18 Impact Wrench\", \"brand\": \"Milwaukee\"}"}
                ]
              }
            }
          ]
        }
        """.trimIndent()

        val parsed = GeminiParser.parseCandidateText(reasoningResponse)
        assertFalse("Must skip reasoning thoughts", parsed.contains("Analyzing user image"))
        assertTrue("Must extract real output", parsed.contains("Milwaukee M18 Impact Wrench"))
    }

    @Test
    fun testParseCandidateTextEmptyCandidatesHandledGracefully() {
        val empty = "{}"
        assertEquals("", GeminiParser.parseCandidateText(empty))

        val emptyCandidates = "{\"candidates\": []}"
        assertEquals("", GeminiParser.parseCandidateText(emptyCandidates))
    }

    // ==========================================
    // 4. SINGLE ITEM PARSING & RESILIENT FALLBACKS
    // ==========================================

    @Test
    fun testSingleItemJsonCompleteValid() {
        val json = """
        {
            "name": "DeWalt 20V Atomic Drill",
            "brand": "DeWalt",
            "manufacturer": "Stanley Black & Decker",
            "model": "DCD708",
            "modelNumber": "DCD708C2",
            "serialNumber": "SN987654",
            "barcode": "088591156789",
            "category": "Tools",
            "quantity": 1,
            "condition": "Excellent",
            "description": "Compact cordless drill with 2.0Ah battery attached",
            "visibleText": ["20V MAX", "ATOMIC", "BRUSHLESS"],
            "accessories": ["Belt clip", "Battery charger"],
            "confidence": 0.98,
            "aiSummary": "DeWalt Atomic brushless compact drill"
        }
        """.trimIndent()

        val result = GeminiParser.parseSingleItemJson(json)
        assertEquals("DeWalt 20V Atomic Drill", result.name)
        assertEquals("DeWalt", result.brand)
        assertEquals("DCD708", result.model)
        assertEquals("DCD708C2", result.modelNumber)
        assertEquals("SN987654", result.serialNumber)
        assertEquals("088591156789", result.barcode)
        assertEquals("Tools", result.category)
        assertEquals(1, result.quantity)
        assertEquals("Excellent", result.condition)
        assertEquals(3, result.visibleText.size)
        assertEquals(2, result.accessories.size)
        assertEquals(0.98f, result.confidence, 0.01f)
    }

    @Test
    fun testBrandFallbackInferredFromVisibleTextAndName() {
        // Brand is null in JSON, but visibleText contains "Makita"
        val jsonWithBrandInText = """
        {
            "name": "Cordless Angle Grinder",
            "brand": null,
            "category": "Tools",
            "visibleText": ["18V LXT", "Makita Corp", "AVT"],
            "confidence": 0.9
        }
        """.trimIndent()

        val result1 = GeminiParser.parseSingleItemJson(jsonWithBrandInText)
        assertEquals("Makita", result1.brand)

        // Brand is null, but name contains "Milwaukee"
        val jsonWithBrandInName = """
        {
            "name": "Milwaukee M12 Soldering Iron",
            "brand": null,
            "category": "Tools",
            "visibleText": []
        }
        """.trimIndent()

        val result2 = GeminiParser.parseSingleItemJson(jsonWithBrandInName)
        assertEquals("Milwaukee", result2.brand)
    }

    @Test
    fun testModelAndSerialFallbackExtractedFromVisibleText() {
        val json = """
        {
            "name": "Shop Vacuum",
            "brand": "Craftsman",
            "modelNumber": null,
            "serialNumber": null,
            "barcode": null,
            "visibleText": [
                "MODEL NO: CMXEVBE17595",
                "S/N: 202311894",
                "BARCODE: 048753229910"
            ]
        }
        """.trimIndent()

        val result = GeminiParser.parseSingleItemJson(json)
        assertEquals("CMXEVBE17595", result.modelNumber)
        assertEquals("CMXEVBE17595", result.model)
        assertEquals("202311894", result.serialNumber)
        assertEquals("048753229910", result.barcode)
    }

    @Test
    fun testPlaceholderNameFallbackInferredFromBrandAndModel() {
        val genericNameJson = """
        {
            "name": "Detected Item",
            "brand": "Ninja",
            "model": "Air Fryer XL",
            "category": "Kitchen"
        }
        """.trimIndent()

        val result = GeminiParser.parseSingleItemJson(genericNameJson)
        assertEquals("Ninja Air Fryer XL", result.name)

        val itemOnlyBrand = """
        {
            "name": "Household Item",
            "brand": "Dyson",
            "model": null,
            "category": "Appliances"
        }
        """.trimIndent()

        val result2 = GeminiParser.parseSingleItemJson(itemOnlyBrand)
        assertEquals("Dyson Item", result2.name)
    }

    @Test
    fun testQuantityAndStringSanitization() {
        val invalidQtyJson = """
        {
            "name": "AA Batteries",
            "quantity": -5,
            "condition": "null",
            "brand": "none"
        }
        """.trimIndent()

        val result = GeminiParser.parseSingleItemJson(invalidQtyJson)
        assertEquals("Quantity must be coerced to at least 1", 1, result.quantity)
        assertEquals("Condition fallback when 'null' provided must be 'Good'", "Good", result.condition)
        assertNull("String 'none' must convert to null", result.brand)
    }

    // ==========================================
    // 5. ROOM SWEEP & ITEM RESEARCH PARSING
    // ==========================================

    @Test
    fun testSweepItemsParsingArrayAndDefaults() {
        val sweepJson = """
        [
            {
                "name": "Socket Set",
                "brand": "Husky",
                "category": "Tools",
                "description": "On bottom shelf"
            },
            {
                "name": null,
                "category": null
            }
        ]
        """.trimIndent()

        val items = GeminiParser.parseSweepItemsJson(sweepJson)
        assertEquals(2, items.size)
        assertEquals("Socket Set", items[0].name)
        assertEquals("Husky", items[0].brand)
        assertEquals("Detected Item", items[1].name)
        assertEquals("General", items[1].category)
    }

    @Test
    fun testItemResearchJsonParsingWithMinimumTaskInterval() {
        val researchJson = """
        {
            "manualTitle": "DeWalt DCD791 User Guide",
            "manualUrl": "https://service.dewalt.com/dcd791",
            "manualSummary": "Complete cordless drill maintenance instructions.",
            "forumSources": ["Reddit r/Tools", "Garage Journal"],
            "commonIssues": [
                {
                    "issue": "Chuck loosening",
                    "symptom": "Bit wobbles",
                    "solution": "Clean chuck teeth with wire brush",
                    "source": "Reddit"
                }
            ],
            "proTips": ["Always store in cool, dry place"],
            "maintenanceTasks": [
                {
                    "title": "Clean cooling vents",
                    "intervalDays": 7,
                    "description": "Blow compressed air"
                }
            ],
            "recommendedParts": ["Chuck Key N12345"],
            "specsSummary": "20V Max, 0-2000 RPM",
            "safetyNotes": "Disconnect battery before maintenance"
        }
        """.trimIndent()

        val research = GeminiParser.parseItemResearchJson(researchJson)
        assertEquals("DeWalt DCD791 User Guide", research.manualTitle)
        assertEquals("https://service.dewalt.com/dcd791", research.manualUrl)
        assertEquals(2, research.forumSources.size)
        assertEquals(1, research.commonIssues.size)
        assertEquals("Chuck loosening", research.commonIssues[0].issue)
        assertEquals("Bit wobbles", research.commonIssues[0].symptom)
        assertEquals(1, research.maintenanceTasks.size)
        // Interval must be coerced to minimum 15 days
        assertEquals(15, research.maintenanceTasks[0].intervalDays)
        assertEquals(1, research.recommendedParts.size)
    }

    @Test
    fun testTroubleshootJsonParsing() {
        val adviceJson = """
        {
            "summary": "Check furnace pilot and thermostat battery",
            "steps": [
                "Verify thermostat display is on",
                "Check circuit breaker in panel"
            ],
            "caution": "Do not attempt gas line repairs yourself."
        }
        """.trimIndent()

        val advice = GeminiParser.parseTroubleshootJson(adviceJson)
        assertEquals("Check furnace pilot and thermostat battery", advice.summary)
        assertEquals(2, advice.steps.size)
        assertEquals("Do not attempt gas line repairs yourself.", advice.caution)
    }
}
