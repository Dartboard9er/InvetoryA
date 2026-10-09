package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
    val profile by inventoryRepository.observeProfile(itemId).collectAsStateWithLifecycle(initialValue = null)
    val documents by inventoryRepository.observeDocuments(itemId).collectAsStateWithLifecycle(initialValue = emptyList())
    val maintenanceTasks by inventoryRepository.observeTasksForItem(itemId).collectAsStateWithLifecycle(initialValue = emptyList())

    var locationPath by remember { mutableStateOf("Unassigned") }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var showTroubleshootDialog by remember { mutableStateOf(false) }
    var showResearchDialog by remember { mutableStateOf(false) }
    var isResearching by remember { mutableStateOf(false) }
    var researchError by remember { mutableStateOf<String?>(null) }
    var latestResearchResult by remember { mutableStateOf<com.example.ai.gemini.ItemResearchResult?>(null) }

    LaunchedEffect(item?.currentLocationId) {
        locationPath = inventoryRepository.getLocationPath(item?.currentLocationId)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                val pair = fileManager.saveImportedImageForItem(itemId, it)
                val imgFile = File(pair.first)
                inventoryRepository.updateExistingItemObservation(
                    itemId = itemId,
                    imageFile = imgFile,
                    locationId = item?.currentLocationId,
                    note = "Additional photo added"
                )
                // Analyze new photo to enrich item information if possible
                if (geminiService.getApiKey().isNotEmpty()) {
                    val analysis = geminiService.analyzeSingleItem(imgFile)
                    if (analysis.isSuccess) {
                        val res = analysis.getOrNull()!!
                        item?.let { currentItem ->
                            val updated = currentItem.copy(
                                brand = currentItem.brand ?: res.brand,
                                model = currentItem.model ?: res.model,
                                serialNumber = currentItem.serialNumber ?: res.serialNumber ?: res.modelNumber,
                                condition = res.condition ?: currentItem.condition,
                                notes = if (currentItem.notes.isNullOrEmpty()) res.description else currentItem.notes
                            )
                            inventoryRepository.updateItem(updated)
                            Toast.makeText(context, "AI extracted additional details from photo!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
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
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag("delete_item_button")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Item",
                            tint = MaterialTheme.colorScheme.error
                        )
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
                FilledTonalButton(
                    onClick = {
                        coroutineScope.launch {
                            showResearchDialog = true
                            isResearching = true
                            researchError = null
                            val res = inventoryRepository.researchAndApplyToItem(itemId)
                            isResearching = false
                            if (res.isSuccess) {
                                latestResearchResult = res.getOrNull()
                            } else {
                                researchError = res.exceptionOrNull()?.message ?: "Research failed"
                            }
                        }
                    },
                    modifier = Modifier.weight(1.3f).testTag("research_item_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Research Intel", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(0.85f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Photo", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = { showTroubleshootDialog = true },
                    modifier = Modifier.weight(0.85f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
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

            // Manuals & Forum Intelligence Section
            SectionHeader(title = "Manuals & Forum Intelligence", icon = Icons.Default.MenuBook)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth().testTag("forum_intel_section_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (profile != null && profile!!.researchStatus == "KNOWLEDGE_READY") {
                        val p = profile!!

                        // Official Manual Card if present
                        if (!p.manualUrl.isNullOrEmpty() || documents.any { it.documentType == "MANUAL" }) {
                            val manualDoc = documents.find { it.documentType == "MANUAL" }
                            val mUrl = p.manualUrl ?: manualDoc?.sourceUrl
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                manualDoc?.name ?: "Official User Manual",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        if (!mUrl.isNullOrEmpty()) {
                                            FilledTonalButton(
                                                onClick = {
                                                    try {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(mUrl))
                                                        context.startActivity(intent)
                                                    } catch (_: Exception) {
                                                        Toast.makeText(context, "Manual URL: $mUrl", Toast.LENGTH_LONG).show()
                                                    }
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Open", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                    if (!manualDoc?.summary.isNullOrEmpty()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            manualDoc!!.summary!!,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }

                        // Common Issues from Forums
                        if (!p.commonProblems.isNullOrEmpty()) {
                            Text(
                                "Common Issues & Fixes (From 3-5 Forums):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val issuesList = p.commonProblems!!.split("\n---\n")
                            issuesList.forEach { iss ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(iss, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        // Pro Maintenance Tips
                        if (!p.maintenanceSummary.isNullOrEmpty()) {
                            Text(
                                "Community Pro Tips:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(p.maintenanceSummary!!, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        // Recommended Parts
                        if (!p.consumablesNeeded.isNullOrEmpty()) {
                            Text(
                                "Recommended Parts & Consumables:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                p.consumablesNeeded!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        // Re-research button
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    showResearchDialog = true
                                    isResearching = true
                                    researchError = null
                                    val res = inventoryRepository.researchAndApplyToItem(itemId)
                                    isResearching = false
                                    if (res.isSuccess) {
                                        latestResearchResult = res.getOrNull()
                                    } else {
                                        researchError = res.exceptionOrNull()?.message ?: "Research failed"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Refresh Online Forum Intel", style = MaterialTheme.typography.labelSmall)
                        }

                    } else {
                        // Empty state: prompt to research
                        Text(
                            "Get official user manuals, common issues, and pro tips synthesized from 3-5 online forums (Reddit, iFixit, YouTube teardowns, community boards).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    showResearchDialog = true
                                    isResearching = true
                                    researchError = null
                                    val res = inventoryRepository.researchAndApplyToItem(itemId)
                                    isResearching = false
                                    if (res.isSuccess) {
                                        latestResearchResult = res.getOrNull()
                                    } else {
                                        researchError = res.exceptionOrNull()?.message ?: "Research failed"
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("start_item_research_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Research Manuals & Tips Now")
                        }
                    }
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

    // AI Research Progress / Report Dialog
    if (showResearchDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isResearching) showResearchDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(if (isResearching) "Researching Online Intel..." else "Research Complete!")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isResearching) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Scanning manufacturer databases & 3-5 consumer forums for '${itm.name}'...",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Synthesizing manuals, common failure points, pro tips, and parts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else if (researchError != null) {
                        Text(
                            text = "Research notice: $researchError",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        val res = latestResearchResult
                        Text(
                            "✅ Successfully gathered manuals & forum intel for '${itm.name}' and populated your item records!",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (res != null) {
                            if (!res.manualTitle.isNullOrEmpty()) {
                                Text("📖 Manual: ${res.manualTitle}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            if (res.forumSources.isNotEmpty()) {
                                Text("🌐 Sources: ${res.forumSources.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (res.commonIssues.isNotEmpty()) {
                                Text("⚠️ Common Issues: ${res.commonIssues.size} identified from community reports", style = MaterialTheme.typography.bodySmall)
                            }
                            if (res.proTips.isNotEmpty()) {
                                Text("💡 Pro Tips: ${res.proTips.size} community maintenance tips added", style = MaterialTheme.typography.bodySmall)
                            }
                            if (res.maintenanceTasks.isNotEmpty()) {
                                Text("🔧 Maintenance: ${res.maintenanceTasks.size} scheduled tasks added to calendar", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showResearchDialog = false },
                    enabled = !isResearching
                ) {
                    Text(if (isResearching) "Please wait..." else "Done")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Item?") },
            text = {
                Text("Are you sure you want to permanently delete '${itm.name}'? All associated photos and records will be removed from your device.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            inventoryRepository.deleteItem(itm.id, deleteFiles = true)
                            showDeleteDialog = false
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_item_button")
                ) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
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
