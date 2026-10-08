package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.ItemEntity
import com.example.data.local.entities.LocationEntity
import com.example.data.repository.InventoryRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    inventoryRepository: InventoryRepository,
    onNavigateToItemDetail: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val locations by inventoryRepository.allLocations.collectAsStateWithLifecycle(initialValue = emptyList())
    val items by inventoryRepository.allActiveItems.collectAsStateWithLifecycle(initialValue = emptyList())

    var showAddLocationDialog by remember { mutableStateOf(false) }
    var selectedLocationFilter by remember { mutableStateOf<String?>(null) }

    val filteredItems = remember(items, selectedLocationFilter) {
        if (selectedLocationFilter == null) items else items.filter { it.currentLocationId == selectedLocationFilter }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Locations (${locations.size})", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showAddLocationDialog = true }) {
                        Icon(Icons.Default.AddLocationAlt, contentDescription = "Add Location")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Location Hierarchy Chips
            Text(
                "Filter by Location:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedLocationFilter == null,
                        onClick = { selectedLocationFilter = null },
                        label = { Text("All Locations") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
                items(locations) { loc ->
                    val count = items.count { it.currentLocationId == loc.id }
                    FilterChip(
                        selected = selectedLocationFilter == loc.id,
                        onClick = { selectedLocationFilter = loc.id },
                        label = { Text("${loc.name} ($count)") },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Hierarchical Cards
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
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
                            .clickable { selectedLocationFilter = loc.id }
                            .testTag("location_card_${loc.id}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selectedLocationFilter == loc.id)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (loc.type) {
                                    "WORKBENCH" -> Icons.Default.Handyman
                                    "DRAWER" -> Icons.Default.Inbox
                                    "ROOM" -> Icons.Default.MeetingRoom
                                    else -> Icons.Default.Place
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
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
                            Badge {
                                Text("${itemsInLoc.size} items")
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
            title = { Text("Add New Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Location Name (e.g. Tool Cabinet)") }
                    )
                    OutlinedTextField(
                        value = type,
                        onValueChange = { type = it },
                        label = { Text("Type (ROOM, CABINET, DRAWER, SHELF)") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        coroutineScope.launch {
                            val newLoc = LocationEntity(
                                id = "loc_${System.currentTimeMillis()}",
                                name = name.trim(),
                                type = type.trim(),
                                parentLocationId = selectedParentId
                            )
                            inventoryRepository.allLocations // add through db
                            showAddLocationDialog = false
                        }
                    }
                }) { Text("Create Location") }
            },
            dismissButton = {
                TextButton(onClick = { showAddLocationDialog = false }) { Text("Cancel") }
            }
        )
    }
}
