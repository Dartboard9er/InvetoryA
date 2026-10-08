package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.example.ui.screens.*
import com.example.ui.theme.HomeAiTheme
import java.io.File

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Scan : Screen("scan", "Scan", Icons.Default.CameraAlt)
    object Inventory : Screen("inventory", "Inventory", Icons.Default.Inventory2)
    object Assistant : Screen("assistant", "Assistant", Icons.Default.Psychology)
    object More : Screen("more", "More", Icons.Default.MoreHoriz)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as HomeAiApplication
        val inventoryRepo = app.inventoryRepository
        val assistantRepo = app.assistantRepository
        val backupRepo = app.backupRepository
        val geminiService = app.geminiService
        val fileManager = app.fileManager

        setContent {
            HomeAiTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val primaryNavItems = listOf(
                    Screen.Scan,
                    Screen.Inventory,
                    Screen.Assistant,
                    Screen.More
                )

                // Cache temp scan file across navigation
                var currentScanFile by remember { mutableStateOf<File?>(null) }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isExpanded = maxWidth >= 600.dp

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Adaptive Navigation Rail on tablets / wide screens
                        if (isExpanded && currentRoute in primaryNavItems.map { it.route }) {
                            NavigationRail(
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Spacer(modifier = Modifier.height(16.dp))
                                primaryNavItems.forEach { screen ->
                                    val isSelected = currentRoute == screen.route
                                    NavigationRailItem(
                                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                                        label = { Text(screen.title) },
                                        selected = isSelected,
                                        modifier = Modifier.testTag("nav_rail_${screen.route}"),
                                        onClick = {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Scaffold(
                            modifier = Modifier.weight(1f),
                            bottomBar = {
                                // Bottom Navigation Bar on compact phones
                                if (!isExpanded && currentRoute in primaryNavItems.map { it.route }) {
                                    NavigationBar {
                                        primaryNavItems.forEach { screen ->
                                            val isSelected = currentRoute == screen.route
                                            NavigationBarItem(
                                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                                label = { Text(screen.title) },
                                                selected = isSelected,
                                                modifier = Modifier.testTag("nav_item_${screen.route}"),
                                                onClick = {
                                                    navController.navigate(screen.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        ) { innerPadding ->
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Scan.route,
                                modifier = Modifier.padding(innerPadding)
                            ) {
                                composable(Screen.Scan.route) {
                                    ScanHomeScreen(
                                        inventoryRepository = inventoryRepo,
                                        onNavigateToScanReview = { photoFile ->
                                            currentScanFile = photoFile
                                            navController.navigate("scan_review")
                                        },
                                        onNavigateToRoomSweep = {
                                            navController.navigate("room_sweep")
                                        },
                                        onNavigateToItemDetail = { itemId ->
                                            navController.navigate("item_detail/$itemId")
                                        }
                                    )
                                }

                                composable("scan_review") {
                                    val file = currentScanFile ?: File(cacheDir, "sample.jpg")
                                    ScanReviewScreen(
                                        imageFile = file,
                                        inventoryRepository = inventoryRepo,
                                        geminiService = geminiService,
                                        onNavigateBack = { navController.popBackStack() },
                                        onItemSaved = { itemId ->
                                            navController.navigate("item_detail/$itemId") {
                                                popUpTo(Screen.Scan.route)
                                            }
                                        }
                                    )
                                }

                                composable("room_sweep") {
                                    RoomSweepScreen(
                                        inventoryRepository = inventoryRepo,
                                        geminiService = geminiService,
                                        fileManager = fileManager,
                                        onNavigateBack = { navController.popBackStack() },
                                        onFinishSweep = {
                                            navController.navigate(Screen.Inventory.route) {
                                                popUpTo(Screen.Scan.route)
                                            }
                                        }
                                    )
                                }

                                composable(Screen.Inventory.route) {
                                    InventoryScreen(
                                        inventoryRepository = inventoryRepo,
                                        geminiService = geminiService,
                                        onNavigateToItemDetail = { itemId ->
                                            navController.navigate("item_detail/$itemId")
                                        }
                                    )
                                }

                                composable("item_detail/{itemId}") { backStackEntry ->
                                    val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
                                    ItemDetailScreen(
                                        itemId = itemId,
                                        inventoryRepository = inventoryRepo,
                                        geminiService = geminiService,
                                        fileManager = fileManager,
                                        onNavigateBack = { navController.popBackStack() },
                                        onScanAgain = { photoFile ->
                                            currentScanFile = photoFile
                                            navController.navigate("scan_review")
                                        }
                                    )
                                }

                                composable(Screen.Assistant.route) {
                                    AssistantScreen(
                                        assistantRepository = assistantRepo
                                    )
                                }

                                composable(Screen.More.route) {
                                    MoreHubScreen(
                                        inventoryRepository = inventoryRepo,
                                        geminiService = geminiService,
                                        backupRepository = backupRepo,
                                        fileManager = fileManager,
                                        onNavigateToLocations = { navController.navigate("locations") },
                                        onNavigateToSettings = { navController.navigate("settings") },
                                        onNavigateToQueue = { navController.navigate("queue") }
                                    )
                                }

                                composable("locations") {
                                    LocationsScreen(
                                        inventoryRepository = inventoryRepo,
                                        onNavigateToItemDetail = { itemId ->
                                            navController.navigate("item_detail/$itemId")
                                        }
                                    )
                                }

                                composable("settings") {
                                    SettingsScreen(
                                        geminiService = geminiService,
                                        backupRepository = backupRepo,
                                        fileManager = fileManager
                                    )
                                }

                                composable("queue") {
                                    ProcessingQueueScreen(
                                        inventoryRepository = inventoryRepo,
                                        onNavigateBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
