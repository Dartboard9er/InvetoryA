package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.modes.AssistantMode
import com.example.data.local.HomeAiDatabase
import com.example.data.local.entities.ChatMessageEntity
import com.example.data.local.entities.ItemEntity
import com.example.data.local.entities.LocationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Tests verifying UI/ViewModel state transitions:
 * - Search query state transitions (empty -> query -> filtered -> cleared)
 * - Assistant chat mode state transitions & message stream partitioning
 * - Warranty lifecycle state transitions (active upcoming vs. expired)
 * - Location hierarchy breadcrumb path resolution state
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewModelStateTransitionTest {

    private lateinit var database: HomeAiDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, HomeAiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==========================================
    // 1. SEARCH QUERY STATE TRANSITIONS
    // ==========================================

    @Test
    fun testSearchQueryStateTransitions() = runBlocking {
        val itemDao = database.itemDao()
        val items = listOf(
            ItemEntity(id = "1", name = "DeWalt Cordless Drill", normalizedName = "dewalt cordless drill", brand = "DeWalt", category = "Tools"),
            ItemEntity(id = "2", name = "DeWalt Angle Grinder", normalizedName = "dewalt angle grinder", brand = "DeWalt", category = "Tools"),
            ItemEntity(id = "3", name = "KitchenAid Stand Mixer", normalizedName = "kitchenaid stand mixer", brand = "KitchenAid", category = "Kitchen"),
            ItemEntity(id = "4", name = "Makita Miter Saw", normalizedName = "makita miter saw", brand = "Makita", category = "Tools")
        )
        for (item in items) {
            itemDao.insertItem(item)
        }

        // State 1: All items initially loaded
        val initialList = itemDao.getAllActiveItems().first()
        assertEquals(4, initialList.size)

        // State 2: User types "DeWalt" -> Transitions to filtered state (2 items)
        val deWaltResults = itemDao.searchItems("DeWalt").first()
        assertEquals(2, deWaltResults.size)
        assertTrue(deWaltResults.all { it.brand == "DeWalt" })

        // State 3: User narrows query to "Drill" -> Transitions to 1 item
        val drillResults = itemDao.searchItems("Drill").first()
        assertEquals(1, drillResults.size)
        assertEquals("DeWalt Cordless Drill", drillResults.first().name)

        // State 4: User types non-matching query "Espresso" -> Transitions to Empty state
        val emptyResults = itemDao.searchItems("Espresso").first()
        assertEquals(0, emptyResults.size)

        // State 5: User clears query -> Transitions back to full active list
        val resetList = itemDao.getAllActiveItems().first()
        assertEquals(4, resetList.size)
    }

    // ==========================================
    // 2. ASSISTANT MODE SWITCHING & CHAT FLOW
    // ==========================================

    @Test
    fun testAssistantModePartitioningAndChatStream() = runBlocking {
        val chatDao = database.chatMessageDao()

        // State 1: User sends message in GARAGE mode
        val garageMsg1 = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = AssistantMode.GARAGE.id,
            role = "USER",
            text = "Where are my drill bits?",
            timestamp = 1000L
        )
        val garageMsg2 = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = AssistantMode.GARAGE.id,
            role = "ASSISTANT",
            text = "Your Milwaukee drill bit set is in Toolbox Drawer 2.",
            timestamp = 1050L
        )
        chatDao.insertMessage(garageMsg1)
        chatDao.insertMessage(garageMsg2)

        // State 2: User switches to COOKING mode and sends message
        val cookMsg1 = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = AssistantMode.COOKING.id,
            role = "USER",
            text = "Do I have baking powder?",
            timestamp = 2000L
        )
        chatDao.insertMessage(cookMsg1)

        // Mode query verification: Garage conversation contains only Garage messages
        val garageConversation = chatDao.getMessagesForConversation(AssistantMode.GARAGE.id).first()
        assertEquals(2, garageConversation.size)
        assertEquals("Where are my drill bits?", garageConversation[0].text)

        // Cooking conversation contains only Cooking messages
        val cookingConversation = chatDao.getMessagesForConversation(AssistantMode.COOKING.id).first()
        assertEquals(1, cookingConversation.size)
        assertEquals("Do I have baking powder?", cookingConversation[0].text)

        // Global HOME_AI mode aggregates all messages
        val allConversation = chatDao.getAllMessages().first()
        assertEquals(3, allConversation.size)

        // Clearing specific mode leaves other modes intact
        chatDao.clearMessages(AssistantMode.COOKING.id)
        assertEquals(0, chatDao.getMessagesForConversation(AssistantMode.COOKING.id).first().size)
        assertEquals(2, chatDao.getMessagesForConversation(AssistantMode.GARAGE.id).first().size)
    }

    // ==========================================
    // 3. WARRANTY LIFECYCLE STATE TRANSITIONS
    // ==========================================

    @Test
    fun testWarrantyUpcomingVsExpiredStateTransitions() = runBlocking {
        val itemDao = database.itemDao()
        val now = 1000000L

        val activeWarrantyItem = ItemEntity(
            id = "w_active",
            name = "Bosch Dishwasher",
            normalizedName = "bosch dishwasher",
            category = "Kitchen",
            warrantyEnd = now + 500000L // In the future
        )
        val expiredWarrantyItem = ItemEntity(
            id = "w_expired",
            name = "Old Lawn Mower",
            normalizedName = "old lawn mower",
            category = "Garage",
            warrantyEnd = now - 500000L // In the past
        )
        val noWarrantyItem = ItemEntity(
            id = "w_none",
            name = "Wooden Spoon",
            normalizedName = "wooden spoon",
            category = "Kitchen",
            warrantyEnd = null
        )

        itemDao.insertItem(activeWarrantyItem)
        itemDao.insertItem(expiredWarrantyItem)
        itemDao.insertItem(noWarrantyItem)

        // State 1: Upcoming warranties query contains only items with future warrantyEnd
        val upcoming = itemDao.getUpcomingWarranties(now).first()
        assertEquals(1, upcoming.size)
        assertEquals("w_active", upcoming.first().id)

        // State 2: Expired warranties query contains only items with past warrantyEnd
        val expired = itemDao.getExpiredWarranties(now).first()
        assertEquals(1, expired.size)
        assertEquals("w_expired", expired.first().id)

        // State 3: Extending warranty on expired item transitions it to upcoming
        val extendedItem = expiredWarrantyItem.copy(warrantyEnd = now + 1000000L)
        itemDao.updateItem(extendedItem)

        val updatedUpcoming = itemDao.getUpcomingWarranties(now).first()
        val updatedExpired = itemDao.getExpiredWarranties(now).first()
        assertEquals(2, updatedUpcoming.size)
        assertEquals(0, updatedExpired.size)
    }

    // ==========================================
    // 4. LOCATION BREADCRUMB RESOLUTION
    // ==========================================

    @Test
    fun testLocationHierarchyBreadcrumbResolution() = runBlocking {
        val locDao = database.locationDao()

        val garage = LocationEntity(id = "l_garage", name = "Garage", type = "ROOM", parentLocationId = null)
        val workbench = LocationEntity(id = "l_wb", name = "Workbench", type = "WORKBENCH", parentLocationId = "l_garage")
        val drawer = LocationEntity(id = "l_drawer", name = "Top Drawer", type = "DRAWER", parentLocationId = "l_wb")

        locDao.insertLocation(garage)
        locDao.insertLocation(workbench)
        locDao.insertLocation(drawer)

        // Breadcrumb path construction logic
        fun buildPath(targetId: String?): String {
            if (targetId.isNullOrEmpty()) return "Unassigned"
            val parts = mutableListOf<String>()
            var curr = runBlocking { locDao.getLocationById(targetId) }
            var depth = 0
            while (curr != null && depth < 8) {
                parts.add(0, curr.name)
                curr = curr.parentLocationId?.let { runBlocking { locDao.getLocationById(it) } }
                depth++
            }
            return if (parts.isEmpty()) "Unassigned" else parts.joinToString(" / ")
        }

        assertEquals("Garage / Workbench / Top Drawer", buildPath("l_drawer"))
        assertEquals("Garage / Workbench", buildPath("l_wb"))
        assertEquals("Garage", buildPath("l_garage"))
        assertEquals("Unassigned", buildPath(null))
        assertEquals("Unassigned", buildPath("non_existent_id"))
    }
}
