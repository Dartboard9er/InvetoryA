package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val locations by inventoryRepository.allLocations.collectAsStateWithLifecycle(initialValue = emptyList())

    var activeImageFile by remember(imageFile) { mutableStateOf(imageFile) }
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

    // Inline API key entry state if no key is configured
    var inlineApiKeyText by remember { mutableStateOf(geminiService.getApiKey()) }
    var isKeyMissing by remember { mutableStateOf(geminiService.getApiKey().isEmpty()) }

    // Photo picker launcher to let user select any photo from gallery
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val imported = File.createTempFile("scan_import_", ".jpg", context.cacheDir)
                    context.contentResolver.openInputStream(it)?.use { input ->
                        imported.outputStream().use { out -> input.copyTo(out) }
                    }
                    activeImageFile = imported
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not load image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Reusable analyze function
    fun performAiAnalysis(targetFile: File = activeImageFile) {
        coroutineScope.launch {
            isAnalyzing = true
            errorMessage = null

            if (!targetFile.exists() || targetFile.length() == 0L) {
                try {
                    com.example.util.SampleScanGenerator.generateSampleImageFile(targetFile, 0)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val apiKey = geminiService.getApiKey()
            if (apiKey.isEmpty()) {
                isAnalyzing = false
                isKeyMissing = true
                errorMessage = "Gemini API key is required for automated item extraction. Enter your key below to auto-identify."
                if (nameText.isEmpty()) nameText = "Scanned Item"
                return@launch
            }

            isKeyMissing = false
            val result = geminiService.analyzeSingleItem(targetFile)
            isAnalyzing = false
            if (result.isSuccess) {
                val res = result.getOrNull()!!
                scanResult = res
                nameText = res.name
                brandText = res.brand ?: ""
                modelText = if (!res.modelNumber.isNullOrEmpty() && !res.model.isNullOrEmpty() && res.model != res.modelNumber) {
                    "${res.model} (${res.modelNumber})"
                } else {
                    res.model ?: res.modelNumber ?: ""
                }
                serialText = res.serialNumber ?: ""
                categoryText = res.category
                conditionText = res.condition ?: "Good"
                notesText = res.description ?: ""

                // Duplicate detection check against Room
                val matches = inventoryRepository.findDuplicateCandidates(res)
                duplicateMatches = matches
            } else {
                val err = result.exceptionOrNull()?.message ?: "AI analysis failed."
                errorMessage = err
                if (nameText.isEmpty()) nameText = "Scanned Item"
            }
        }
    }

    // Run analysis whenever active image changes
    LaunchedEffect(activeImageFile) {
        performAiAnalysis(activeImageFile)
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
                    imageFile = activeImageFile,
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
                        model = activeImageFile,
                        contentDescription = "Scanned Photo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Change Photo", style = MaterialTheme.typography.labelSmall)
                        }

                        FilledTonalButton(
                            onClick = { performAiAnalysis(activeImageFile) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-analyze", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Test Items switcher for previewing different items
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Quick Sample Scans:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(com.example.util.SampleScanGenerator.SAMPLE_OBJECTS.size) { idx ->
                    val sample = com.example.util.SampleScanGenerator.SAMPLE_OBJECTS[idx]
                    AssistChip(
                        onClick = {
                            coroutineScope.launch {
                                val newSample = File.createTempFile("sample_test_", ".jpg", context.cacheDir)
                                com.example.util.SampleScanGenerator.generateSampleImageFile(newSample, idx)
                                activeImageFile = newSample
                            }
                        },
                        label = { Text(sample.brand, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // AI Extraction Highlight Banner when scanResult is present
            scanResult?.let { res ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("ai_extraction_summary_card")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "✨ AI Extracted Item Info",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${res.name} • ${res.brand ?: "Brand"} • ${res.model ?: res.category}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Inline API Key entry if key is not configured
            if (isKeyMissing) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("missing_api_key_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Enter Gemini API Key",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "To have Home AI automatically extract item name, brand, model, serial number, and specifications from your photos, paste your Gemini API key below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = inlineApiKeyText,
                            onValueChange = { inlineApiKeyText = it },
                            placeholder = { Text("AIzaSy...") },
                            modifier = Modifier.fillMaxWidth().testTag("inline_api_key_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (inlineApiKeyText.isNotBlank()) {
                                    geminiService.saveApiKey(inlineApiKeyText)
                                    isKeyMissing = false
                                    errorMessage = null
                                    performAiAnalysis(activeImageFile)
                                    Toast.makeText(context, "API Key saved! Analyzing image now...", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().testTag("inline_save_key_button"),
                            enabled = inlineApiKeyText.isNotBlank()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Key & Auto-Identify Item")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else if (!errorMessage.isNullOrEmpty()) {
                // AI Error Message Banner with retry
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "AI Extraction Notice",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { performAiAnalysis(activeImageFile) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Retry AI Identification")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

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
                                            imageFile = activeImageFile,
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
            Spacer(modifier = Modifier.height(4.dp))

            // Detected text chips from item label or barcode
            scanResult?.visibleText?.let { texts ->
                if (texts.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            "Detected Text on Item/Label (Tap to apply):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(texts.size) { idx ->
                                val txt = texts[idx]
                                SuggestionChip(
                                    onClick = {
                                        if (serialText.isEmpty()) {
                                            serialText = txt
                                        } else if (modelText.isEmpty()) {
                                            modelText = txt
                                        } else if (brandText.isEmpty()) {
                                            brandText = txt
                                        } else {
                                            notesText = (notesText + " " + txt).trim()
                                        }
                                    },
                                    label = { Text(txt, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }
            }
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
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Description & Notes") },
                placeholder = { Text("Visual details, color, features, accessories...") },
                modifier = Modifier.fillMaxWidth().testTag("item_notes_input"),
                maxLines = 3
            )
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
                            condition = conditionText,
                            description = notesText.ifEmpty { currentRes.description }
                        )

                        val savedItem = inventoryRepository.saveNewItemFromScan(
                            scanResult = finalRes,
                            imageFile = activeImageFile,
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
