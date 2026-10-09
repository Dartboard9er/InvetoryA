package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.repository.InventoryRepository
import com.example.ui.designsystem.EmptyStateCard
import com.example.ui.designsystem.ItemGridCard
import com.example.ui.designsystem.ItemListCard
import com.example.ui.components.NetworkStatusBadge
import com.example.ui.components.OfflineNoticeBanner
import com.example.ui.components.AddItemOfflineDialog
import com.example.util.NetworkMonitor
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    inventoryRepository: InventoryRepository,
    geminiService: com.example.ai.gemini.GeminiService,
    onNavigateToItemDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnlineFlow.collectAsStateWithLifecycle(initialValue = networkMonitor.isCurrentlyOnline())
    val hasApiKey = remember(geminiService) { geminiService.getApiKey().isNotBlank() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var isGridView by remember { mutableStateOf(false) } // Default to streamlined, readable list
    var showAiChatSheet by remember { mutableStateOf(false) }
    var showVoiceCaptureSheet by remember { mutableStateOf(false) }
    var showAddOfflineDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Multi-selection & Batch Research State
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItemIds = remember { mutableStateListOf<String>() }
    var showBatchResearchDialog by remember { mutableStateOf(false) }
    var isBatchResearching by remember { mutableStateOf(false) }
    var batchProgress by remember { mutableStateOf(0 to 0) }
    var currentBatchItemName by remember { mutableStateOf("") }
    var batchSuccessCount by remember { mutableStateOf(0) }

    val allItems by inventoryRepository.allActiveItems.collectAsStateWithLifecycle(initialValue = emptyList())
    val searchResults by inventoryRepository.searchItems(searchQuery).collectAsStateWithLifecycle(initialValue = emptyList())
    val readyProfiles by inventoryRepository.allReadyProfiles.collectAsStateWithLifecycle(initialValue = emptyList())
    val readyProfileItemIds = remember(readyProfiles) { readyProfiles.map { it.itemId }.toSet() }

    val displayedItems = remember(allItems, searchResults, searchQuery, selectedCategoryFilter, readyProfileItemIds) {
        val base = if (searchQuery.isBlank()) allItems else searchResults
        when (selectedCategoryFilter) {
            "All" -> base
            "Intel Ready" -> base.filter { readyProfileItemIds.contains(it.id) }
            else -> base.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
        }
    }

    val categories = listOf("All", "Intel Ready", "Tools", "Electronics", "Kitchen", "Storage", "Office", "Home")

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Selected (${selectedItemIds.size})",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedItemIds.clear()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            if (selectedItemIds.size == displayedItems.size) {
                                selectedItemIds.clear()
                            } else {
                                selectedItemIds.clear()
                                selectedItemIds.addAll(displayedItems.map { it.id })
                            }
                        }) {
                            Text(
                                if (selectedItemIds.size == displayedItems.size) "Deselect All" else "Select All",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Inventory",
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${allItems.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        // Network status badge (Online vs Offline Mode)
                        NetworkStatusBadge(
                            isOnline = isOnline,
                            hasApiKey = hasApiKey,
                            modifier = Modifier.padding(end = 4.dp)
                        )

                        // Quick Add Item Button (100% Offline, no API required)
                        IconButton(
                            onClick = { showAddOfflineDialog = true },
                            modifier = Modifier.testTag("add_item_offline_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircle,
                                contentDescription = "Add Item Directly",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Toggle between Streamlined List and Compact Grid
                        IconButton(
                            onClick = { isGridView = !isGridView },
                            modifier = Modifier.testTag("toggle_view_mode_button")
                        ) {
                            Icon(
                                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Select Mode Button
                        FilledTonalButton(
                            onClick = { isSelectionMode = true },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("select_items_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        }
                    }
                )
            }
        },
        bottomBar = {
            // Full-Width Docked Action Bar in Selection Mode: Uses full screen width cleanly
            if (isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                isSelectionMode = false
                                selectedItemIds.clear()
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (selectedItemIds.isNotEmpty()) {
                                    showBatchResearchDialog = true
                                    isBatchResearching = true
                                    batchProgress = 0 to selectedItemIds.size
                                    batchSuccessCount = 0
                                    coroutineScope.launch {
                                        val success = inventoryRepository.batchResearchItems(selectedItemIds.toList()) { cur, total, name ->
                                            batchProgress = cur to total
                                            currentBatchItemName = name
                                        }
                                        batchSuccessCount = success
                                        isBatchResearching = false
                                    }
                                }
                            },
                            enabled = selectedItemIds.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("bulk_research_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (selectedItemIds.isEmpty()) "Select Items" else "Research Selected (${selectedItemIds.size})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                // Sleek primary AI assistant action
                ExtendedFloatingActionButton(
                    onClick = { showAiChatSheet = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    text = { Text("Add via AI", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("inventory_ai_chat_button")
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Offline / Local Status Notice Banner
            OfflineNoticeBanner(
                isOnline = isOnline,
                hasApiKey = hasApiKey
            )

            // Streamlined Search Bar with Integrated Voice Capture Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inventory_search_bar"),
                        placeholder = {
                            Text(
                                "Search items, brands, serials...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Integrated Voice Dictation Button right inside search bar!
                    IconButton(
                        onClick = { showVoiceCaptureSheet = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                            .testTag("inventory_voice_capture_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Tell Home AI",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Streamlined Category Filter Chips Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    val count = when (cat) {
                        "All" -> allItems.size
                        "Intel Ready" -> readyProfileItemIds.size
                        else -> allItems.count { it.category.equals(cat, ignoreCase = true) }
                    }

                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategoryFilter = cat },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (cat == "Intel Ready") {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    if (count > 0 && cat != "All") "$cat ($count)" else cat,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Quick Status summary line
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${displayedItems.size} item${if (displayedItems.size == 1) "" else "s"}${if (selectedCategoryFilter != "All") " in $selectedCategoryFilter" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (readyProfileItemIds.isNotEmpty()) {
                    Text(
                        text = "✨ ${readyProfileItemIds.size} researched",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Items List or Grid or Empty State
            if (displayedItems.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.Inventory2,
                    title = if (searchQuery.isEmpty()) "Your inventory is empty" else "No matching items",
                    description = if (searchQuery.isEmpty()) "Scan an item or tap 'Add via AI' to start organizing your home." else "Try searching by brand, model number, or room.",
                    actionButtonText = if (searchQuery.isEmpty()) "Add First Item" else "Clear Search",
                    onActionClick = {
                        if (searchQuery.isNotEmpty()) {
                            searchQuery = ""
                            selectedCategoryFilter = "All"
                        } else {
                            showAiChatSheet = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isGridView) {
                // Balanced 2-Column Grid View
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayedItems.size, key = { displayedItems[it].id }) { idx ->
                        val item = displayedItems[idx]
                        var locPath by remember { mutableStateOf("...") }
                        val images by inventoryRepository.observeImages(item.id).collectAsStateWithLifecycle(initialValue = emptyList())
                        val primaryImage = images.firstOrNull()

                        LaunchedEffect(item.currentLocationId) {
                            locPath = inventoryRepository.getLocationPath(item.currentLocationId)
                        }

                        ItemGridCard(
                            item = item,
                            imagePath = primaryImage?.thumbnailPath ?: primaryImage?.localPath,
                            locationPath = locPath,
                            onClick = { onNavigateToItemDetail(item.id) },
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedItemIds.contains(item.id),
                            hasIntelProfile = readyProfileItemIds.contains(item.id),
                            onSelectionToggle = {
                                if (selectedItemIds.contains(item.id)) {
                                    selectedItemIds.remove(item.id)
                                } else {
                                    selectedItemIds.add(item.id)
                                }
                            }
                        )
                    }
                }
            } else {
                // Streamlined, Uncluttered Full-Width List View
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayedItems, key = { it.id }) { item ->
                        var locPath by remember { mutableStateOf("...") }
                        val images by inventoryRepository.observeImages(item.id).collectAsStateWithLifecycle(initialValue = emptyList())
                        val primaryImage = images.firstOrNull()

                        LaunchedEffect(item.currentLocationId) {
                            locPath = inventoryRepository.getLocationPath(item.currentLocationId)
                        }

                        ItemListCard(
                            item = item,
                            imagePath = primaryImage?.thumbnailPath ?: primaryImage?.localPath,
                            locationPath = locPath,
                            onClick = { onNavigateToItemDetail(item.id) },
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedItemIds.contains(item.id),
                            hasIntelProfile = readyProfileItemIds.contains(item.id),
                            onSelectionToggle = {
                                if (selectedItemIds.contains(item.id)) {
                                    selectedItemIds.remove(item.id)
                                } else {
                                    selectedItemIds.add(item.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Batch Research Progress & Completion Dialog
    if (showBatchResearchDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isBatchResearching) showBatchResearchDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    if (isBatchResearching) "Bulk Researching Items..." else "Research Complete!",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isBatchResearching) {
                        LinearProgressIndicator(
                            progress = {
                                if (batchProgress.second > 0) {
                                    batchProgress.first.toFloat() / batchProgress.second.toFloat()
                                } else 0f
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Researching item ${batchProgress.first} of ${batchProgress.second}:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            currentBatchItemName.ifEmpty { "Gathering technical data..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Scanning official user manuals & community forums for common failure points, tips, and service guides...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "✅ Successfully researched $batchSuccessCount item${if (batchSuccessCount == 1) "" else "s"}!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Items have been populated with official manuals, maintenance tasks, and forum intelligence.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBatchResearchDialog = false
                        isSelectionMode = false
                        selectedItemIds.clear()
                    },
                    enabled = !isBatchResearching,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (isBatchResearching) "Researching..." else "Done", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showAiChatSheet) {
        com.example.ui.components.InventoryAiChatSheet(
            inventoryRepository = inventoryRepository,
            geminiService = geminiService,
            onDismiss = { showAiChatSheet = false },
            onItemViewDetail = { id ->
                showAiChatSheet = false
                onNavigateToItemDetail(id)
            }
        )
    }

    if (showVoiceCaptureSheet) {
        com.example.ui.components.VoiceCaptureSheet(
            inventoryRepository = inventoryRepository,
            geminiService = geminiService,
            onDismiss = { showVoiceCaptureSheet = false },
            onNavigateToItemDetail = { id ->
                showVoiceCaptureSheet = false
                onNavigateToItemDetail(id)
            }
        )
    }

    if (showAddOfflineDialog) {
        AddItemOfflineDialog(
            inventoryRepository = inventoryRepository,
            onDismiss = { showAddOfflineDialog = false },
            onItemAdded = { newItemId ->
                showAddOfflineDialog = false
                onNavigateToItemDetail(newItemId)
            }
        )
    }
}
