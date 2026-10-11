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
import androidx.compose.material.icons.automirrored.filled.ViewList
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
    var showHeaderMenu by remember { mutableStateOf(false) }
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
                                text = "Inventory",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                softWrap = false,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${allItems.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        // Quick Add Item Button (100% Offline, direct manual entry)
                        IconButton(
                            onClick = { showAddOfflineDialog = true },
                            modifier = Modifier.testTag("add_item_offline_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
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
                                imageVector = if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGridView) "Switch to List View" else "Switch to Grid View",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Select Mode / Batch Operation Button
                        IconButton(
                            onClick = { isSelectionMode = true },
                            modifier = Modifier.testTag("select_items_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Select Items",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Overflow Menu for additional actions and narrow screen support
                        Box {
                            IconButton(
                                onClick = { showHeaderMenu = true },
                                modifier = Modifier.testTag("inventory_header_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More Options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showHeaderMenu,
                                onDismissRequest = { showHeaderMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Select Items") },
                                    leadingIcon = { Icon(Icons.Default.Checklist, contentDescription = null) },
                                    onClick = {
                                        showHeaderMenu = false
                                        isSelectionMode = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add Item Directly") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = {
                                        showHeaderMenu = false
                                        showAddOfflineDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isGridView) "Switch to List View" else "Switch to Grid View") },
                                    leadingIcon = {
                                        Icon(
                                            if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showHeaderMenu = false
                                        isGridView = !isGridView
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Add via AI Assistant") },
                                    leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                                    onClick = {
                                        showHeaderMenu = false
                                        showAiChatSheet = true
                                    }
                                )
                            }
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("inventory_search_bar"),
                placeholder = {
                    Text(
                        "Search items, brands, locations, serials...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { showVoiceCaptureSheet = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f))
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
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            )

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

            // Quick Status summary line with Network Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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

                NetworkStatusBadge(
                    isOnline = isOnline,
                    hasApiKey = hasApiKey
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Items List or Grid or Empty State
            if (displayedItems.isEmpty()) {
                if (searchQuery.isNotEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Default.SearchOff,
                        title = "No items found",
                        description = "We couldn't find anything matching \"$searchQuery\". Try checking the spelling or search by room, category, or serial number.",
                        actionButtonText = "Clear Search",
                        onActionClick = {
                            searchQuery = ""
                            selectedCategoryFilter = "All"
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (selectedCategoryFilter != "All") {
                    EmptyStateCard(
                        icon = Icons.Default.Category,
                        title = "No items in $selectedCategoryFilter",
                        description = "You don't have any items categorized under \"$selectedCategoryFilter\" yet.",
                        actionButtonText = "View All Items",
                        onActionClick = {
                            selectedCategoryFilter = "All"
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    EmptyStateCard(
                        icon = Icons.Default.Inventory2,
                        title = "Your inventory is empty",
                        description = "Start cataloging your home! Scan an item with your camera, add items directly, or tell Home AI via voice.",
                        actionButtonText = "Add First Item",
                        onActionClick = {
                            showAiChatSheet = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (isGridView) {
                // Balanced 2-Column Grid View
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 116.dp),
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
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 116.dp),
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
