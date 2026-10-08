package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ai.gemini.GeminiService
import com.example.data.local.entities.ItemEntity
import com.example.data.local.files.LocalFileManager
import com.example.data.repository.InventoryRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: String,
    inventoryRepository: InventoryRepository,
    geminiService: GeminiService,
    fileManager: LocalFileManager,
    onNavigateBack: () -> Unit,
    onScanAgain: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val item by inventoryRepository.observeItem(itemId).collectAsStateWithLifecycle(initialValue = null)
    val images by inventoryRepository.observeImages(itemId).collectAsStateWithLifecycle(initialValue = emptyList())
    val observations by inventoryRepository.observeObservations(itemId).collectAsStateWithLifecycle(initialValue = emptyList())
    val history by inventoryRepository.observeHistory(itemId).collectAsStateWithLifecycle(initialValue = emptyList())
    val troubleshooting by inventoryRepository.observeTroubleshooting(itemId).collectAsStateWithLifecycle(initialValue = emptyList())
    val customFields by inventoryRepository.observeCustomFields(itemId).collectAsStateWithLifecycle(initialValue = emptyList())
    val locations by inventoryRepository.allLocations.collectAsStateWithLifecycle(initialValue = emptyList())

    var locationPath by remember { mutableStateOf("Unassigned") }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var showTroubleshootDialog by remember { mutableStateOf(false) }

    LaunchedEffect(item?.currentLocationId) {
        locationPath = inventoryRepository.getLocationPath(item?.currentLocationId)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val pair = fileManager.saveImportedImageForItem(itemId, it)
                inventoryRepository.updateExistingItemObservation(
                    itemId = itemId,
                    imageFile = File(pair.first),
                    locationId = item?.currentLocationId,
                    note = "Additional photo added"
                )
            }
        }
    }

    val sdf = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US) }

    if (item == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val itm = item!!

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(itm.name, fontWeight = FontWeight.Bold, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMoveDialog = true }) {
                        Icon(Icons.Default.DriveFileMove, contentDescription = "Move")
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            inventoryRepository.archiveItem(itm.id)
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.Archive, contentDescription = "Archive")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Photo Gallery Carousel
            if (images.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                ) {
                    items(images) { img ->
                        Box(
                            modifier = Modifier
                                .width(220.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = File(img.localPath),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                            ) {
                                Text(
                                    text = img.imageType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current Location Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CURRENT LOCATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = locationPath,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    FilledTonalButton(
                        onClick = { showMoveDialog = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Move")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Pills Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Photo", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = { showTroubleshootDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fix Issue", style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Identification Section
            SectionHeader(title = "Identification", icon = Icons.Default.Info)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailRow(label = "Brand", value = itm.brand ?: "Unknown")
                    DetailRow(label = "Model", value = itm.model ?: "Unknown")
                    DetailRow(label = "Model Number", value = itm.modelNumber ?: "None")
                    DetailRow(label = "Serial Number", value = itm.serialNumber ?: "Not recorded")
                    DetailRow(label = "Category", value = itm.category)
                    DetailRow(label = "Condition", value = itm.condition ?: "Good")
                    DetailRow(label = "AI Confidence", value = "${(itm.aiConfidence * 100).toInt()}%")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Visual Memory Summary
            if (!itm.aiSummary.isNullOrEmpty()) {
                SectionHeader(title = "Home AI Visual Memory", icon = Icons.Default.Psychology)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = itm.aiSummary!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Custom Fields Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(title = "Custom Fields", icon = Icons.Default.Tune)
                TextButton(onClick = { showAddFieldDialog = true }) {
                    Text("+ Add Field")
                }
            }
            if (customFields.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        customFields.forEach { f ->
                            DetailRow(label = f.fieldName, value = f.fieldValue)
                        }
                    }
                }
            } else {
                Text(
                    "No custom attributes defined yet (e.g. Battery Platform, Filter Size).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Location History Timeline
            SectionHeader(title = "Location History", icon = Icons.Default.History)
            if (history.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        history.forEach { h ->
                            var path by remember { mutableStateOf("...") }
                            LaunchedEffect(h.locationId) {
                                path = inventoryRepository.getLocationPath(h.locationId)
                            }
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(top = 6.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(path, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${sdf.format(Date(h.timestamp))} • ${h.source}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    if (!h.note.isNullOrEmpty()) {
                                        Text(h.note, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Troubleshooting History
            if (troubleshooting.isNotEmpty()) {
                SectionHeader(title = "Troubleshooting History", icon = Icons.Default.Handyman)
                troubleshooting.forEach { t ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Problem: ${t.problem}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Fixed by: ${t.successfulSolution ?: t.actionsTaken ?: "Not recorded"}", style = MaterialTheme.typography.bodySmall)
                            Text("Date: ${sdf.format(Date(t.timestamp))} • Result: ${t.result}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }

    // Move Location Dialog
    if (showMoveDialog) {
        var selectedLoc by remember { mutableStateOf(itm.currentLocationId) }
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move '${itm.name}'") },
            text = {
                Column {
                    Text("Select new location:")
                    Spacer(modifier = Modifier.height(12.dp))
                    LocationDropdown(
                        locations = locations,
                        selectedLocationId = selectedLoc,
                        onLocationSelected = { selectedLoc = it }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    selectedLoc?.let {
                        coroutineScope.launch {
                            inventoryRepository.moveItem(itm.id, it)
                            showMoveDialog = false
                        }
                    }
                }) {
                    Text("Confirm Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Add Field Dialog
    if (showAddFieldDialog) {
        var fieldName by remember { mutableStateOf("") }
        var fieldValue by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFieldDialog = false },
            title = { Text("Add Custom Field") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = fieldName,
                        onValueChange = { fieldName = it },
                        label = { Text("Field Name (e.g. Battery, Size)") }
                    )
                    OutlinedTextField(
                        value = fieldValue,
                        onValueChange = { fieldValue = it },
                        label = { Text("Value (e.g. 20V MAX, 65-inch)") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (fieldName.isNotBlank()) {
                        coroutineScope.launch {
                            inventoryRepository.addCustomField(itm.id, fieldName.trim(), fieldValue.trim())
                            showAddFieldDialog = false
                        }
                    }
                }) { Text("Save Field") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFieldDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Troubleshooting Dialog
    if (showTroubleshootDialog) {
        var problemText by remember { mutableStateOf("") }
        var fixText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTroubleshootDialog = false },
            title = { Text("Log Maintenance / Fix") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = problemText,
                        onValueChange = { problemText = it },
                        label = { Text("Problem (e.g. Printer paper jam)") }
                    )
                    OutlinedTextField(
                        value = fixText,
                        onValueChange = { fixText = it },
                        label = { Text("Solution that worked (e.g. Clean paper path)") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (problemText.isNotBlank()) {
                        coroutineScope.launch {
                            inventoryRepository.recordTroubleshooting(
                                itemId = itm.id,
                                problem = problemText.trim(),
                                actionsTaken = fixText.trim(),
                                result = "SUCCESS",
                                successfulSolution = fixText.trim()
                            )
                            showTroubleshootDialog = false
                        }
                    }
                }) { Text("Remember Solution") }
            },
            dismissButton = {
                TextButton(onClick = { showTroubleshootDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
