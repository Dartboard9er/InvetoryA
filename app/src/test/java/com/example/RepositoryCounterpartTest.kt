package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.HomeAiDatabase
import com.example.data.local.entities.*
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
 * Tests verifying symmetrical operations (counterparts) and error/edge cases across
 * Room DAOs, entities, and database state transitions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryCounterpartTest {

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
    // 1. ITEM LIFECYCLE COUNTERPARTS
    // ==========================================

    @Test
    fun testItemLifecycleInsertArchiveRestoreDelete() = runBlocking {
        val itemDao = database.itemDao()
        val itemId = "drill_001"
        val initialItem = ItemEntity(
            id = itemId,
            name = "Makita Driver Drill",
            normalizedName = "makita driver drill",
            category = "Tools",
            currentLocationId = "loc_garage",
            quantity = 1,
            status = "ACTIVE"
        )

        // Counterpart 1: Insert -> Active list contains item
        itemDao.insertItem(initialItem)
        var activeItems = itemDao.getAllActiveItems().first()
        var archivedItems = itemDao.getArchivedItems().first()
        assertEquals(1, activeItems.size)
        assertEquals(0, archivedItems.size)
        assertEquals("Makita Driver Drill", activeItems.first().name)

        // Counterpart 2: Archive -> Removed from active, added to archived
        itemDao.updateItem(initialItem.copy(status = "ARCHIVED", updatedAt = System.currentTimeMillis()))
        activeItems = itemDao.getAllActiveItems().first()
        archivedItems = itemDao.getArchivedItems().first()
        assertEquals(0, activeItems.size)
        assertEquals(1, archivedItems.size)
        assertEquals(itemId, archivedItems.first().id)

        // Counterpart 3: Restore -> Returns to active, removed from archived
        itemDao.updateItem(initialItem.copy(status = "ACTIVE", updatedAt = System.currentTimeMillis()))
        activeItems = itemDao.getAllActiveItems().first()
        archivedItems = itemDao.getArchivedItems().first()
        assertEquals(1, activeItems.size)
        assertEquals(0, archivedItems.size)

        // Counterpart 4: Delete -> Completely removed from DB
        itemDao.deleteItemById(itemId)
        val retrieved = itemDao.getItemById(itemId)
        assertNull(retrieved)
        activeItems = itemDao.getAllActiveItems().first()
        assertEquals(0, activeItems.size)
    }

    // ==========================================
    // 2. LOCATION & RELOCATION COUNTERPARTS
    // ==========================================

    @Test
    fun testLocationHierarchyAndRelocationCounterparts() = runBlocking {
        val locDao = database.locationDao()
        val itemDao = database.itemDao()
        val historyDao = database.itemLocationHistoryDao()

        val room = LocationEntity(id = "room_basement", name = "Basement", type = "ROOM", parentLocationId = null)
        val shelfA = LocationEntity(id = "shelf_a", name = "Shelf A", type = "SHELF", parentLocationId = "room_basement")
        val shelfB = LocationEntity(id = "shelf_b", name = "Shelf B", type = "SHELF", parentLocationId = "room_basement")

        locDao.insertLocation(room)
        locDao.insertLocation(shelfA)
        locDao.insertLocation(shelfB)

        // Hierarchy verification
        val subLocations = locDao.getSubLocations("room_basement").first()
        assertEquals(2, subLocations.size)

        // Item start at Shelf A
        val itemId = "box_filters"
        val item = ItemEntity(
            id = itemId,
            name = "Furnace Filters 20x25x4",
            normalizedName = "furnace filters",
            category = "HVAC",
            currentLocationId = "shelf_a"
        )
        itemDao.insertItem(item)

        // Relocate to Shelf B
        val moveTime1 = System.currentTimeMillis()
        itemDao.updateItem(item.copy(currentLocationId = "shelf_b", updatedAt = moveTime1))
        historyDao.insertHistory(
            ItemLocationHistoryEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                locationId = "shelf_b",
                timestamp = moveTime1,
                source = "USER_MOVE",
                note = "Moved to Shelf B"
            )
        )

        val updatedItem = itemDao.getItemById(itemId)
        assertEquals("shelf_b", updatedItem?.currentLocationId)

        // Revert move back to Shelf A (symmetrical counterpart)
        val moveTime2 = moveTime1 + 5000
        itemDao.updateItem(item.copy(currentLocationId = "shelf_a", updatedAt = moveTime2))
        historyDao.insertHistory(
            ItemLocationHistoryEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                locationId = "shelf_a",
                timestamp = moveTime2,
                source = "USER_REVERT",
                note = "Moved back to Shelf A"
            )
        )

        val revertedItem = itemDao.getItemById(itemId)
        assertEquals("shelf_a", revertedItem?.currentLocationId)

        val histories = historyDao.getHistoryForItem(itemId).first()
        assertEquals(2, histories.size)
        assertEquals("shelf_a", histories[0].locationId)
        assertEquals("shelf_b", histories[1].locationId)
    }

    // ==========================================
    // 3. MAINTENANCE TASK COUNTERPARTS
    // ==========================================

    @Test
    fun testMaintenanceTaskScheduleCompleteReopenDelete() = runBlocking {
        val taskDao = database.maintenanceTaskDao()
        val now = System.currentTimeMillis()
        val taskId = "task_air_filter"
        val task = MaintenanceTaskEntity(
            id = taskId,
            itemId = "furnace_01",
            title = "Replace 20x25x4 filter",
            description = "High efficiency MERV 11 filter replacement",
            intervalDays = 90,
            nextDueDate = now + (90L * 24 * 60 * 60 * 1000),
            isCompleted = false
        )

        // 1. Schedule -> upcoming
        taskDao.insertTask(task)
        var upcoming = taskDao.getUpcomingTasks(now).first()
        assertEquals(1, upcoming.size)
        assertEquals(taskId, upcoming.first().id)

        // 2. Complete counterpart -> isCompleted = true (removed from upcoming)
        val completedTask = task.copy(isCompleted = true, lastCompletedDate = now)
        taskDao.updateTask(completedTask)
        upcoming = taskDao.getUpcomingTasks(now).first()
        assertEquals(0, upcoming.size)

        // All tasks still records it
        val allTasks = taskDao.getAllTasks().first()
        assertEquals(1, allTasks.size)
        assertTrue(allTasks.first().isCompleted)

        // 3. Reopen counterpart -> isCompleted = false (returns to upcoming)
        val reopenedTask = completedTask.copy(isCompleted = false)
        taskDao.updateTask(reopenedTask)
        upcoming = taskDao.getUpcomingTasks(now).first()
        assertEquals(1, upcoming.size)
        assertFalse(upcoming.first().isCompleted)

        // 4. Overdue counterpart -> nextDueDate in past
        val overdueTask = task.copy(isCompleted = false, nextDueDate = now - 10000)
        taskDao.updateTask(overdueTask)
        val overdue = taskDao.getOverdueTasks(now).first()
        assertEquals(1, overdue.size)
        assertEquals(taskId, overdue.first().id)

        // 5. Delete counterpart
        taskDao.deleteTask(taskId)
        assertEquals(0, taskDao.getAllTasks().first().size)
    }

    // ==========================================
    // 4. AI JOB QUEUE COUNTERPARTS
    // ==========================================

    @Test
    fun testAiJobStateTransitionsSuccessAndFailureCounterparts() = runBlocking {
        val jobDao = database.aiJobDao()
        val jobId = "job_scan_44"

        // Enqueue: PENDING
        val job = AiJobEntity(
            id = jobId,
            type = "SCAN_PROCESSING",
            status = "PENDING",
            payloadPath = "/storage/scans/test.jpg"
        )
        jobDao.insertJob(job)
        var pendingJobs = jobDao.getPendingJobs().first()
        assertEquals(1, pendingJobs.size)
        assertEquals("PENDING", pendingJobs.first().status)

        // Transition: PROCESSING
        val processingJob = job.copy(status = "PROCESSING", lastAttemptAt = System.currentTimeMillis())
        jobDao.updateJob(processingJob)
        val nextJob = jobDao.getNextPendingJob()
        assertNull("No jobs should be PENDING when job is PROCESSING", nextJob)

        // Counterpart A: SUCCESS -> COMPLETED
        val completedJob = processingJob.copy(status = "COMPLETED", errorMessage = null)
        jobDao.updateJob(completedJob)
        pendingJobs = jobDao.getPendingJobs().first()
        assertEquals(0, pendingJobs.size)

        // Counterpart B: FAILURE -> FAILED with retry count & error
        val failedJob = processingJob.copy(
            status = "FAILED",
            errorMessage = "Image resolution too low",
            retryCount = 1
        )
        jobDao.updateJob(failedJob)
        pendingJobs = jobDao.getPendingJobs().first()
        assertEquals("FAILED job is not in pending jobs", 0, pendingJobs.size)

        // Delete job counterpart
        jobDao.deleteJob(jobId)
        val recentJobs = jobDao.getRecentJobs().first()
        assertEquals(0, recentJobs.size)
    }

    // ==========================================
    // 5. ITEM RELATIONSHIPS & CUSTOM FIELDS COUNTERPARTS
    // ==========================================

    @Test
    fun testItemRelationshipAndCustomFieldsCounterparts() = runBlocking {
        val relDao = database.itemRelationshipDao()
        val fieldDao = database.customFieldDao()

        val relId = "rel_drill_battery"
        val relationship = ItemRelationshipEntity(
            id = relId,
            sourceItemId = "drill_01",
            targetItemId = "battery_01",
            relationshipType = "POWERED_BY",
            notes = "Compatible with 20V Max batteries"
        )

        // Insert relationship
        relDao.insertRelationship(relationship)
        // Query from source
        val fromSource = relDao.getRelationshipsForItem("drill_01").first()
        assertEquals(1, fromSource.size)
        // Symmetrical query from target
        val fromTarget = relDao.getRelationshipsForItem("battery_01").first()
        assertEquals(1, fromTarget.size)

        // Delete relationship counterpart
        relDao.deleteRelationship(relId)
        assertEquals(0, relDao.getRelationshipsForItem("drill_01").first().size)
        assertEquals(0, relDao.getRelationshipsForItem("battery_01").first().size)

        // Custom field insert & delete counterpart
        val fieldId = "cf_voltage"
        val field = CustomFieldEntity(
            id = fieldId,
            itemId = "drill_01",
            fieldName = "Voltage",
            fieldValue = "20V"
        )
        fieldDao.insertField(field)
        val fields = fieldDao.getFieldsForItem("drill_01").first()
        assertEquals(1, fields.size)
        assertEquals("20V", fields.first().fieldValue)

        fieldDao.deleteField(fieldId)
        assertEquals(0, fieldDao.getFieldsForItem("drill_01").first().size)
    }

    // ==========================================
    // 6. ERROR CASES & BOUNDARY COUNTERPARTS
    // ==========================================

    @Test
    fun testDuplicateIdInsertReplacesWithoutCollisionCrash() = runBlocking {
        val itemDao = database.itemDao()
        val itemOriginal = ItemEntity(
            id = "same_id_123",
            name = "Original Name",
            normalizedName = "original name",
            category = "Tools"
        )
        val itemDuplicate = ItemEntity(
            id = "same_id_123",
            name = "Updated Replacement Name",
            normalizedName = "updated replacement name",
            category = "Hardware"
        )

        // OnConflictStrategy.REPLACE should update smoothly
        itemDao.insertItem(itemOriginal)
        itemDao.insertItem(itemDuplicate)

        val retrieved = itemDao.getItemById("same_id_123")
        assertNotNull(retrieved)
        assertEquals("Updated Replacement Name", retrieved?.name)
        assertEquals(1, itemDao.getAllActiveItems().first().size)
    }

    @Test
    fun testNonExistentQueryAndItemRetrievalReturnsGracefulDefaults() = runBlocking {
        val itemDao = database.itemDao()
        assertNull(itemDao.getItemById("non_existent_id_9999"))

        val searchResults = itemDao.searchItems("xyz_not_in_inventory_query").first()
        assertTrue(searchResults.isEmpty())
    }

    @Test
    fun testBoundaryValuesHandledSafely() = runBlocking {
        val itemDao = database.itemDao()
        val boundaryItem = ItemEntity(
            id = "boundary_item",
            name = "Free Resistor Pack",
            normalizedName = "free resistor pack",
            category = "Electronics",
            quantity = 50000,
            purchasePrice = 0.0,
            notes = "Special characters: & < > \" ' \n \t \uD83D\uDD27 \uD83D\uDEE0"
        )
        itemDao.insertItem(boundaryItem)

        val retrieved = itemDao.getItemById("boundary_item")
        assertNotNull(retrieved)
        assertEquals(50000, retrieved?.quantity)
        assertEquals(0.0, retrieved?.purchasePrice ?: -1.0, 0.001)
        assertTrue(retrieved?.notes?.contains("\uD83D\uDD27") == true)
    }
}
