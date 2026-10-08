package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ai.gemini.GeminiService
import com.example.ai.gemini.SingleItemScanResult
import com.example.data.local.entities.ItemEntity
import com.example.data.local.entities.LocationEntity
import com.example.data.repository.InventoryRepository
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanReviewScreen(
    imageFile: File,
    inventoryRepository: InventoryRepository,
    geminiService: GeminiService,
    onNavigateBack: () -> Unit,
    onItemSaved: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val locations by inventoryRepository.allLocations.collectAsStateWithLifecycle(initialValue = emptyList())

    var isAnalyzing by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var scanResult by remember { mutableStateOf<SingleItemScanResult?>(null) }
    var duplicateMatches by remember { mutableStateOf<List<ItemEntity>>(emptyList()) }
    var selectedLocationId by remember { mutableStateOf<String?>("loc_workbench") }

    // Editable fields
    var nameText by remember { mutableStateOf("") }
    var brandText by remember { mutableStateOf("") }
    var modelText by remember { mutableStateOf("") }
    var serialText by remember { mutableStateOf("") }
    var categoryText by remember { mutableStateOf("Tools") }
    var conditionText by remember { mutableStateOf("Good") }
    var notesText by remember { mutableStateOf("") }

    // Run analysis on launch
    LaunchedEffect(Unit) {
        if (!imageFile.exists() || imageFile.length() == 0L) {
            try {
                com.example.util.SampleScanGenerator.generateSampleImageFile(imageFile, 0)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        val apiKey = geminiService.getApiKey()
        if (apiKey.isEmpty()) {
            isAnalyzing = false
            nameText = "Scanned Item"
            scanResult = SingleItemScanResult(
                name = "Scanned Item",
                brand = null,
                manufacturer = null,
                model = null,
                modelNumber = null,
                serialNumber = null,
                barcode = null,
                category = "General",
                subcategory = null,
                quantity = 1,
                condition = "Good",
                description = "Saved locally without AI",
                visibleText = emptyList(),
                accessories = emptyList(),
                confidence = 1.0f,
                aiSummary = "Saved locally. Add Gemini API key in Settings for AI auto-extraction."
            )
            return@LaunchedEffect
        }

        val result = geminiService.analyzeSingleItem(imageFile)
        isAnalyzing = false
        if (result.isSuccess) {
            val res = result.getOrNull()!!
            scanResult = res
            nameText = res.name
            brandText = res.brand ?: ""
            modelText = res.model ?: ""
            serialText = res.serialNumber ?: ""
            categoryText = res.category
            conditionText = res.condition ?: "Good"

            // Duplicate detection check against Room
            coroutineScope.launch {
                val matches = inventoryRepository.findDuplicateCandidates(res)
                duplicateMatches = matches
            }
        } else {
            errorMessage = result.exceptionOrNull()?.message ?: "AI analysis failed."
            nameText = "Scanned Item"
            scanResult = SingleItemScanResult(
                name = "Scanned Item",
                brand = null,
                manufacturer = null,
                model = null,
                modelNumber = null,
                serialNumber = null,
                barcode = null,
                category = "General",
                subcategory = null,
                quantity = 1,
                condition = "Good",
                description = "Saved locally",
                visibleText = emptyList(),
                accessories = emptyList(),
                confidence = 0.5f,
                aiSummary = "Photo saved locally. AI analysis can be retried when online."
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Review", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Captured Photo Preview with Animated AI Scanner
            if (isAnalyzing) {
                com.example.ui.components.AiScanningOverlay(
                    imageFile = imageFile,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = imageFile,
                        contentDescription = "Scanned Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Duplicate Warning banner if matching item found
            if (duplicateMatches.isNotEmpty()) {
                val existing = duplicateMatches.first()
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("duplicate_detected_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FindReplace, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Possible Existing Item Found",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "This matches '${existing.name}' (${existing.brand ?: ""}) in your inventory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        inventoryRepository.updateExistingItemObservation(
                                            itemId = existing.id,
                                            imageFile = imageFile,
                                            locationId = selectedLocationId,
                                            note = "Re-scanned and confirmed"
                                        )
                                        onItemSaved(existing.id)
                                    }
                                },
                                modifier = Modifier.testTag("update_existing_item_button")
                            ) {
                                Text("This Is The Same Item")
                            }
                            OutlinedButton(
                                onClick = { duplicateMatches = emptyList() }
                            ) {
                                Text("Create As New")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // AI Extraction Confidence Badge
            scanResult?.let { res ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "AI Confidence: ${(res.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (!res.aiSummary.isNullOrEmpty()) {
                        Text(
                            text = "FACT: ${res.condition ?: "Inspected"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Editable Form
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text("Item Name") },
                modifier = Modifier.fillMaxWidth().testTag("item_name_input")
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = brandText,
                    onValueChange = { brandText = it },
                    label = { Text("Brand") },
                    modifier = Modifier.weight(1f).testTag("item_brand_input")
                )
                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it },
                    label = { Text("Model") },
                    modifier = Modifier.weight(1f).testTag("item_model_input")
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = serialText,
                onValueChange = { serialText = it },
                label = { Text("Serial Number") },
                modifier = Modifier.fillMaxWidth().testTag("item_serial_input")
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = categoryText,
                    onValueChange = { categoryText = it },
                    label = { Text("Category") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = conditionText,
                    onValueChange = { conditionText = it },
                    label = { Text("Condition") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Location Selector
            Text(
                text = "Where is this stored?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            LocationDropdown(
                locations = locations,
                selectedLocationId = selectedLocationId,
                onLocationSelected = { selectedLocationId = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save Action Button
            Button(
                onClick = {
                    coroutineScope.launch {
                        val currentRes = scanResult ?: SingleItemScanResult(
                            name = nameText,
                            brand = brandText.ifEmpty { null },
                            manufacturer = null,
                            model = modelText.ifEmpty { null },
                            modelNumber = null,
                            serialNumber = serialText.ifEmpty { null },
                            barcode = null,
                            category = categoryText,
                            subcategory = null,
                            quantity = 1,
                            condition = conditionText,
                            description = "Scanned item",
                            visibleText = emptyList(),
                            accessories = emptyList(),
                            confidence = 1.0f,
                            aiSummary = null
                        )

                        val finalRes = currentRes.copy(
                            name = nameText.ifEmpty { "Household Item" },
                            brand = brandText.ifEmpty { null },
                            model = modelText.ifEmpty { null },
                            serialNumber = serialText.ifEmpty { null },
                            category = categoryText,
                            condition = conditionText
                        )

                        val savedItem = inventoryRepository.saveNewItemFromScan(
                            scanResult = finalRes,
                            imageFile = imageFile,
                            locationId = selectedLocationId
                        )
                        onItemSaved(savedItem.id)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_item_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Confirm & Save Item", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LocationDropdown(
    locations: List<LocationEntity>,
    selectedLocationId: String?,
    onLocationSelected: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLocation = locations.find { it.id == selectedLocationId }
    val displayName = selectedLocation?.name ?: "No Location (Unassigned)"

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .testTag("location_dropdown_selector"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            DropdownMenuItem(
                text = { Text("Unassigned") },
                onClick = {
                    onLocationSelected(null)
                    expanded = false
                }
            )
            locations.forEach { loc ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(loc.name, fontWeight = FontWeight.Bold)
                            Text(
                                loc.type,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    },
                    onClick = {
                        onLocationSelected(loc.id)
                        expanded = false
                    }
                )
            }
        }
    }
}
