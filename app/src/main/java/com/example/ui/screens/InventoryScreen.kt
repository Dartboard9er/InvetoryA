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
            TopAppBar(
                title = {
                    Text(
                        "Inventory (${allItems.size})",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        floatingActionButton = {
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
                            onClick = { onNavigateToItemDetail(item.id) }
                        )
                    }
                }
            }
        }
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
