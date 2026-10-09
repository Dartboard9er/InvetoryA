package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.ItemEntity
import com.example.data.local.entities.LocationEntity
import com.example.data.repository.InventoryRepository
import com.example.ui.components.NetworkStatusBadge
import com.example.util.NetworkMonitor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    inventoryRepository: InventoryRepository,
    onNavigateToItemDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnlineFlow.collectAsStateWithLifecycle(initialValue = networkMonitor.isCurrentlyOnline())

    val locations by inventoryRepository.allLocations.collectAsStateWithLifecycle(initialValue = emptyList())
    val items by inventoryRepository.allActiveItems.collectAsStateWithLifecycle(initialValue = emptyList())

    var showAddLocationDialog by remember { mutableStateOf(false) }
    var selectedLocationFilter by remember { mutableStateOf<String?>(null) }

    val filteredItems = remember(items, selectedLocationFilter) {
        if (selectedLocationFilter == null) emptyList() else items.filter { it.currentLocationId == selectedLocationFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Locations & Rooms", fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp)
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${locations.size}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    NetworkStatusBadge(
                        isOnline = isOnline,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    FilledTonalButton(
                        onClick = { showAddLocationDialog = true },
                        modifier = Modifier.padding(end = 8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Room", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Location Hierarchy Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedLocationFilter == null,
                        onClick = { selectedLocationFilter = null },
                        label = { Text("All Spaces") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                items(locations) { loc ->
                    val count = items.count { it.currentLocationId == loc.id }
                    FilterChip(
                        selected = selectedLocationFilter == loc.id,
                        onClick = {
                            selectedLocationFilter = if (selectedLocationFilter == loc.id) null else loc.id
                        },
                        label = { Text("${loc.name} ($count)") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // If a specific location is selected, show header with stored items
            if (selectedLocationFilter != null) {
                val activeLoc = locations.find { it.id == selectedLocationFilter }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Stored in ${activeLoc?.name ?: "Room"} (${filteredItems.size} items)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(onClick = { selectedLocationFilter = null }) {
                            Text("Clear Filter")
                        }
                    }
                }
            }

            // Hierarchical Cards List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // If a location is selected, list its items directly!
                if (selectedLocationFilter != null) {
                    if (filteredItems.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No items currently stored here. Move items here from their detail page or during scan review.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredItems, key = { it.id }) { itm ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToItemDetail(itm.id) },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Inventory2,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(itm.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        if (!itm.brand.isNullOrEmpty() || !itm.model.isNullOrEmpty()) {
                                            Text(
                                                listOfNotNull(itm.brand, itm.model).joinToString(" "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Text(
                            "ALL SPACES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // All Locations Cards
                items(locations) { loc ->
                    var parentPath by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(loc.parentLocationId) {
                        if (loc.parentLocationId != null) {
                            parentPath = inventoryRepository.getLocationPath(loc.parentLocationId)
                        }
                    }

                    val itemsInLoc = items.filter { it.currentLocationId == loc.id }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedLocationFilter = if (selectedLocationFilter == loc.id) null else loc.id
                            }
                            .testTag("location_card_${loc.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedLocationFilter == loc.id)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        border = if (selectedLocationFilter == loc.id)
                            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        else
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when (loc.type.uppercase()) {
                                        "WORKBENCH" -> Icons.Default.Handyman
                                        "DRAWER" -> Icons.Default.Inbox
                                        "ROOM" -> Icons.Default.MeetingRoom
                                        "SHELF" -> Icons.Default.Inventory2
                                        else -> Icons.Default.Place
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = loc.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (!parentPath.isNullOrEmpty()) "Inside $parentPath • ${loc.type}" else loc.type,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "${itemsInLoc.size} item${if (itemsInLoc.size == 1) "" else "s"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddLocationDialog) {
        var name by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("ROOM") }
        var selectedParentId by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddLocationDialog = false },
            title = { Text("Add New Location", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Location Name (e.g. Workshop Workbench)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = type,
                        onValueChange = { type = it },
                        label = { Text("Type (ROOM, CABINET, DRAWER, SHELF)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            coroutineScope.launch {
                                inventoryRepository.addLocation(name.trim(), type.trim().uppercase(), selectedParentId)
                                showAddLocationDialog = false
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Location")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddLocationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
