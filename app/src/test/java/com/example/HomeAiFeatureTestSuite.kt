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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeAiFeatureTestSuite {

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

    @Test
    fun testRoomDatabaseItemInsertAndObserve() = runBlocking {
        val itemDao = database.itemDao()
        val item = ItemEntity(
            id = "test_item_1",
            name = "DeWalt 20V Drill",
            normalizedName = "dewalt 20v drill",
            brand = "DeWalt",
            category = "Tools",
            currentLocationId = "loc_garage",
            quantity = 1,
            status = "ACTIVE"
        )
        itemDao.insertItem(item)

        val retrieved = itemDao.getItemById("test_item_1")
        assertNotNull(retrieved)
        assertEquals("DeWalt 20V Drill", retrieved?.name)
        assertEquals("Tools", retrieved?.category)

        val activeList = itemDao.getAllActiveItems().first()
        assertEquals(1, activeList.size)
        assertEquals("test_item_1", activeList.first().id)
    }

    @Test
    fun testChatMessagePersistenceAcrossModes() = runBlocking {
        val chatDao = database.chatMessageDao()

        val msg1 = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = "home_ai",
            role = "USER",
            text = "Add hammer to workbench",
            timestamp = System.currentTimeMillis()
        )
        val msg2 = ChatMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = "garage",
            role = "ASSISTANT",
            text = "Hammer added to workbench!",
            timestamp = System.currentTimeMillis() + 100
        )

        chatDao.insertMessage(msg1)
        chatDao.insertMessage(msg2)

        // All messages should be retrieved when in home_ai mode
        val allMessages = chatDao.getAllMessages().first()
        assertEquals(2, allMessages.size)

        // Specific mode message
        val garageMessages = chatDao.getMessagesForConversation("garage").first()
        assertEquals(1, garageMessages.size)
        assertEquals("Hammer added to workbench!", garageMessages.first().text)
    }

    @Test
    fun testLocationHierarchyAndLookup() = runBlocking {
        val locationDao = database.locationDao()
        val loc1 = LocationEntity(
            id = "loc_garage",
            name = "Garage",
            parentLocationId = null,
            type = "ROOM"
        )
        val loc2 = LocationEntity(
            id = "loc_workbench",
            name = "Workbench",
            parentLocationId = "loc_garage",
            type = "WORKBENCH"
        )

        locationDao.insertLocation(loc1)
        locationDao.insertLocation(loc2)

        val locations = locationDao.getLocationsList()
        assertEquals(2, locations.size)
        assertTrue(locations.any { it.id == "loc_garage" })
        assertTrue(locations.any { it.id == "loc_workbench" })
    }

    @Test
    fun testModeDetectionFromQuery() {
        assertEquals(AssistantMode.COOKING, AssistantMode.detectModeFromQuery("What recipe can I cook tonight?"))
        assertEquals(AssistantMode.GARAGE, AssistantMode.detectModeFromQuery("Where is my DeWalt impact drill?"))
        assertEquals(AssistantMode.MAINTENANCE, AssistantMode.detectModeFromQuery("When is the furnace filter replacement due?"))
        assertEquals(AssistantMode.FIND, AssistantMode.detectModeFromQuery("Where did I put my spare keys?"))
        assertEquals(AssistantMode.HOME_AI, AssistantMode.detectModeFromQuery("Hello Home AI, give me an overview"))
    }
}
