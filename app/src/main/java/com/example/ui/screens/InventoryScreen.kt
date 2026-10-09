package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.entities.ItemEntity
import com.example.data.repository.InventoryRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    inventoryRepository: InventoryRepository,
    geminiService: com.example.ai.gemini.GeminiService,
    onNavigateToItemDetail: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var showAiChatSheet by remember { mutableStateOf(false) }
    var showVoiceCaptureSheet by remember { mutableStateOf(false) }
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

    val displayedItems = remember(allItems, searchResults, searchQuery, selectedCategoryFilter) {
        val base = if (searchQuery.isBlank()) allItems else searchResults
        if (selectedCategoryFilter == "All") {
            base
        } else {
            base.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
        }
    }

    val categories = listOf("All", "Tools", "Electronics", "Kitchen", "Storage", "Office", "Home")

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            "Selected (${selectedItemIds.size})",
                            fontWeight = FontWeight.Bold
                        )
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
                            Text(if (selectedItemIds.size == displayedItems.size) "Deselect All" else "Select All")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            "Inventory (${allItems.size})",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        FilledTonalButton(
                            onClick = { isSelectionMode = true },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("select_items_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (isSelectionMode) {
                ExtendedFloatingActionButton(
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                    text = { Text("Research Selected (${selectedItemIds.size})", fontWeight = FontWeight.Bold) },
                    modifier = Modifier.testTag("bulk_research_button"),
                    expanded = true
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice Capture: Tell Home AI [ 🎙 ]
                    FloatingActionButton(
                        onClick = { showVoiceCaptureSheet = true },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("inventory_voice_capture_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Tell Home AI",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // AI Chat / Add via AI: Ask Home AI [ ✨ Add via AI ]
                    ExtendedFloatingActionButton(
                        onClick = { showAiChatSheet = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        text = { Text("Add via AI", fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("inventory_ai_chat_button")
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("inventory_search_bar"),
                placeholder = { Text("Search items, brands, serials...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            // Category Filter Chips
            androidx.compose.foundation.lazy.LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Items List or Empty State
            if (displayedItems.isEmpty()) {
                com.example.ui.designsystem.EmptyStateCard(
                    icon = Icons.Default.CameraAlt,
                    title = if (searchQuery.isEmpty()) "Your home is empty." else "No matching items",
                    description = if (searchQuery.isEmpty()) "Scan your first item and Home AI will start remembering." else "Try searching by brand, model, or room.",
                    actionButtonText = if (searchQuery.isEmpty()) "Scan Something" else null,
                    onActionClick = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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

                        com.example.ui.designsystem.ItemGridCard(
                            item = item,
                            imagePath = primaryImage?.thumbnailPath ?: primaryImage?.localPath,
                            locationPath = locPath,
                            onClick = { onNavigateToItemDetail(item.id) },
                            isSelectionMode = isSelectionMode,
                            isSelected = selectedItemIds.contains(item.id),
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

    // Batch Research Modal Dialog
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
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(if (isBatchResearching) "Bulk Researching Items..." else "Bulk Research Complete!")
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
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Researching item ${batchProgress.first} of ${batchProgress.second}:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            currentBatchItemName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Synthesizing official manuals & 3-5 forums (Reddit, iFixit, YouTube) for common failure points, tips, and replacement parts...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        Text(
                            "✅ Successfully completed deep research for $batchSuccessCount items!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "All selected items have been populated with official user manuals, common forum issues & fixes, pro maintenance tips, and scheduled maintenance tasks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    enabled = !isBatchResearching
                ) {
                    Text(if (isBatchResearching) "Researching..." else "Done")
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
}

@Composable
fun InventoryItemCard(
    item: ItemEntity,
    inventoryRepository: InventoryRepository,
    onClick: () -> Unit
) {
    var locationPath by remember { mutableStateOf("Loading...") }
    val images by inventoryRepository.observeImages(item.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val primaryImage = images.firstOrNull()

    LaunchedEffect(item.currentLocationId) {
        locationPath = inventoryRepository.getLocationPath(item.currentLocationId)
    }

    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("inventory_item_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Item Thumbnail
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (primaryImage != null && File(primaryImage.localPath).exists()) {
                    AsyncImage(
                        model = File(primaryImage.thumbnailPath ?: primaryImage.localPath),
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Category,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (!item.brand.isNullOrEmpty() || !item.model.isNullOrEmpty()) {
                    Text(
                        text = listOfNotNull(item.brand, item.model).joinToString(" "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = locationPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Text(
                    text = "Scanned ${dateFormat.format(Date(item.lastScannedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
