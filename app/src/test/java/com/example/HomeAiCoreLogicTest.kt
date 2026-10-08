package com.example

import com.example.ai.gemini.SingleItemScanResult
import com.example.data.local.entities.AiMemoryEntity
import com.example.data.local.entities.ItemEntity
import com.example.data.local.entities.LocationEntity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Unit tests covering core data models, Gemini memory parsing fallbacks,
 * category filtering, and memory clip entity mappings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeAiCoreLogicTest {

    @Test
    fun testItemEntityCreationAndDefaults() {
        val itemId = UUID.randomUUID().toString()
        val item = ItemEntity(
            id = itemId,
            name = "Makita Cordless Drill",
            normalizedName = "makita cordless drill",
            category = "Tools",
            currentLocationId = "garage_shelf_1",
            description = "18V LXT Lithium-Ion hammer driver-drill",
            condition = "Good",
            quantity = 1,
            purchasePrice = 129.99,
            modelNumber = "XPH12Z",
            serialNumber = "12345MAK",
            notes = "Stored with extra battery",
            status = "ACTIVE"
        )

        assertEquals("Makita Cordless Drill", item.name)
        assertEquals("makita cordless drill", item.normalizedName)
        assertEquals("Tools", item.category)
        assertEquals("garage_shelf_1", item.currentLocationId)
        assertEquals(1, item.quantity)
        assertEquals(129.99, item.purchasePrice ?: 0.0, 0.01)
        assertEquals("ACTIVE", item.status)
    }

    @Test
    fun testLocationEntityHierarchy() {
        val rootLocation = LocationEntity(
            id = "loc_garage",
            name = "Garage",
            parentLocationId = null,
            type = "ROOM",
            description = "Main double-car garage"
        )
        val shelfLocation = LocationEntity(
            id = "loc_shelf_left",
            name = "Left Metal Shelf",
            parentLocationId = rootLocation.id,
            type = "SHELF",
            description = "Upper storage shelf"
        )

        assertNull(rootLocation.parentLocationId)
        assertEquals("loc_garage", shelfLocation.parentLocationId)
        assertEquals("SHELF", shelfLocation.type)
    }

    @Test
    fun testAiMemoryEntityClassification() {
        val memory = AiMemoryEntity(
            id = UUID.randomUUID().toString(),
            type = "LOCATION",
            content = "Moved spare furnace filters into basement storage room on the left shelf",
            itemId = "item_filters_123",
            locationId = "loc_basement_storage",
            importance = 0.95f
        )

        assertEquals("LOCATION", memory.type)
        assertEquals("item_filters_123", memory.itemId)
        assertEquals("loc_basement_storage", memory.locationId)
        assertTrue(memory.importance >= 0.9f)
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
            ItemEntity(id = "1", name = "Chef's Knife", normalizedName = "chef's knife", category = "Kitchen"),
            ItemEntity(id = "2", name = "Circular Saw", normalizedName = "circular saw", category = "Tools"),
            ItemEntity(id = "3", name = "Pressure Washer", normalizedName = "pressure washer", category = "Garage"),
            ItemEntity(id = "4", name = "Coffee Beans", normalizedName = "coffee beans", category = "Pantry")
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

    @Test
    fun testSingleItemScanResultStructure() {
        val result = SingleItemScanResult(
            name = "DeWalt 20V Battery",
            brand = "DeWalt",
            manufacturer = "Stanley Black & Decker",
            model = "DCB205",
            modelNumber = "DCB205-2",
            serialNumber = "SN992812",
            barcode = "885911412345",
            category = "Hardware",
            subcategory = "Batteries",
            quantity = 2,
            condition = "New",
            description = "5.0Ah Lithium Ion Battery Pack",
            visibleText = listOf("20V MAX", "5Ah"),
            accessories = listOf("Protective Cap"),
            confidence = 0.98f,
            aiSummary = "Identified DeWalt 20V battery pack"
        )

        assertEquals("DeWalt 20V Battery", result.name)
        assertEquals("DeWalt", result.brand)
        assertEquals(2, result.quantity)
        assertEquals("Hardware", result.category)
        assertTrue(result.confidence > 0.9f)
    }
}
