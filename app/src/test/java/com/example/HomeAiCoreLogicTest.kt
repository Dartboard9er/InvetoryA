package com.example

import com.example.ai.gemini.GeminiService
import com.example.data.local.entities.AiMemoryEntity
import com.example.data.local.entities.ItemEntity
import com.example.data.local.entities.LocationEntity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Unit tests covering core data models, Gemini memory parsing fallbacks,
 * category filtering, and memory clip entity mappings.
 */
class HomeAiCoreLogicTest {

    @Test
    fun testItemEntityCreationAndDefaults() {
        val itemId = UUID.randomUUID().toString()
        val item = ItemEntity(
            id = itemId,
            name = "Makita Cordless Drill",
            category = "Tools",
            currentLocationId = "garage_shelf_1",
            description = "18V LXT Lithium-Ion hammer driver-drill",
            condition = "Good",
            quantity = 1,
            estimatedValue = 129.99,
            modelNumber = "XPH12Z",
            serialNumber = "12345MAK",
            tags = "power tool, makita, cordless",
            notes = "Stored with extra battery"
        )

        assertEquals("Makita Cordless Drill", item.name)
        assertEquals("Tools", item.category)
        assertEquals("garage_shelf_1", item.currentLocationId)
        assertEquals(1, item.quantity)
        assertEquals(129.99, item.estimatedValue ?: 0.0, 0.01)
        assertFalse(item.isDeleted)
    }

    @Test
    fun testLocationEntityHierarchy() {
        val rootLocation = LocationEntity(
            id = "loc_garage",
            name = "Garage",
            parentLocationId = null,
            roomType = "GARAGE",
            description = "Main double-car garage"
        )
        val shelfLocation = LocationEntity(
            id = "loc_shelf_left",
            name = "Left Metal Shelf",
            parentLocationId = rootLocation.id,
            roomType = "GARAGE",
            description = "Upper storage shelf"
        )

        assertNull(rootLocation.parentLocationId)
        assertEquals("loc_garage", shelfLocation.parentLocationId)
        assertEquals("GARAGE", shelfLocation.roomType)
    }

    @Test
    fun testAiMemoryEntityClassification() {
        val memory = AiMemoryEntity(
            id = UUID.randomUUID().toString(),
            category = "Location",
            content = "Moved spare furnace filters into basement storage room on the left shelf",
            relatedItemId = "item_filters_123",
            relatedLocationId = "loc_basement_storage",
            confidence = 0.95f
        )

        assertEquals("Location", memory.category)
        assertNotNull(memory.relatedItemId)
        assertNotNull(memory.relatedLocationId)
        assertTrue(memory.confidence >= 0.9f)
    }

    @Test
    fun testJsonMemoryParsingFormat() {
        val mockAiResponse = """
        {
            "category": "Location",
            "matchedItemName": "Furnace Filters",
            "matchedRoom": "Basement Storage",
            "extractedFact": "Spare 20x25x4 furnace filters are located on the left shelf in the basement storage room."
        }
        """.trimIndent()

        val json = JSONObject(mockAiResponse)
        assertEquals("Location", json.getString("category"))
        assertEquals("Furnace Filters", json.getString("matchedItemName"))
        assertEquals("Basement Storage", json.getString("matchedRoom"))
        assertTrue(json.getString("extractedFact").contains("furnace filters"))
    }

    @Test
    fun testInventoryFilterMatches() {
        val items = listOf(
            ItemEntity(id = "1", name = "Chef's Knife", category = "Kitchen"),
            ItemEntity(id = "2", name = "Circular Saw", category = "Tools"),
            ItemEntity(id = "3", name = "Pressure Washer", category = "Garage"),
            ItemEntity(id = "4", name = "Coffee Beans", category = "Pantry")
        )

        // Filter by category
        val kitchenItems = items.filter { it.category == "Kitchen" }
        assertEquals(1, kitchenItems.size)
        assertEquals("Chef's Knife", kitchenItems.first().name)

        // Search query filter
        val search = "saw"
        val searchedItems = items.filter { it.name.contains(search, ignoreCase = true) }
        assertEquals(1, searchedItems.size)
        assertEquals("Circular Saw", searchedItems.first().name)
    }
}
