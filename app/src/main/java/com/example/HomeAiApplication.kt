package com.example

import android.app.Application
import com.example.ai.gemini.GeminiService
import com.example.data.local.HomeAiDatabase
import com.example.data.local.entities.LocationEntity
import com.example.data.local.files.LocalFileManager
import com.example.data.repository.AssistantRepository
import com.example.data.repository.BackupRepository
import com.example.data.repository.InventoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeAiApplication : Application() {

    lateinit var database: HomeAiDatabase
        private set
    lateinit var fileManager: LocalFileManager
        private set
    lateinit var geminiService: GeminiService
        private set
    lateinit var inventoryRepository: InventoryRepository
        private set
    lateinit var assistantRepository: AssistantRepository
        private set
    lateinit var backupRepository: BackupRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = HomeAiDatabase.getDatabase(this)
        fileManager = LocalFileManager(this)
        geminiService = GeminiService(this)
        inventoryRepository = InventoryRepository(database, fileManager, geminiService)
        assistantRepository = AssistantRepository(database, geminiService, inventoryRepository)
        backupRepository = BackupRepository(this, database, fileManager)

        // Seed default locations if empty
        CoroutineScope(Dispatchers.IO).launch {
            seedInitialLocations()
        }
    }

    private suspend fun seedInitialLocations() {
        val root = database.locationDao().getRootLocations()
        // Check if database has any location
        val locList = database.locationDao().getLocationById("loc_garage")
        if (locList == null) {
            val house = LocationEntity(
                id = "loc_house",
                name = "House",
                type = "PROPERTY",
                description = "Primary residence"
            )
            val garage = LocationEntity(
                id = "loc_garage",
                name = "Garage",
                parentLocationId = "loc_house",
                type = "ROOM",
                description = "Main garage storage and workspace"
            )
            val workbench = LocationEntity(
                id = "loc_workbench",
                name = "Workbench",
                parentLocationId = "loc_garage",
                type = "WORKBENCH",
                description = "Primary wooden tool bench"
            )
            val drawer2 = LocationEntity(
                id = "loc_drawer2",
                name = "Drawer 2",
                parentLocationId = "loc_workbench",
                type = "DRAWER",
                description = "Upper tool drawer"
            )
            val basement = LocationEntity(
                id = "loc_basement",
                name = "Basement",
                parentLocationId = "loc_house",
                type = "ROOM",
                description = "Lower level storage room"
            )

            database.locationDao().insertLocation(house)
            database.locationDao().insertLocation(garage)
            database.locationDao().insertLocation(workbench)
            database.locationDao().insertLocation(drawer2)
            database.locationDao().insertLocation(basement)
        }
    }
}
