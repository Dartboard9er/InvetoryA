package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ai.gemini.SingleItemScanResult
import com.example.data.local.entities.ItemEntity
import com.example.data.repository.InventoryRepository
import com.example.util.SampleScanGenerator
import kotlinx.coroutines.launch
import java.io.File

/**
 * Direct dialog allowing the user to add an item to the inventory offline without requiring an API.
 * Supports:
 * - Entering Name, Brand, Model, Serial, Category, Condition, and Notes
 * - Selecting or capturing a photo (offline local file storage)
 * - Dictating any field via voice transcription
 * - Selecting a storage location/room
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemOfflineDialog(
    inventoryRepository: InventoryRepository,
    initialVoiceName: String = "",
    onDismiss: () -> Unit,
    onItemAdded: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val locations by inventoryRepository.allLocations.collectAsStateWithLifecycle(initialValue = emptyList())

    var nameText by remember { mutableStateOf(initialVoiceName) }
    var brandText by remember { mutableStateOf("") }
    var modelText by remember { mutableStateOf("") }
    var serialText by remember { mutableStateOf("") }
    var categoryText by remember { mutableStateOf("Tools") }
    var conditionText by remember { mutableStateOf("Good") }
    var notesText by remember { mutableStateOf("") }
    var selectedLocationId by remember { mutableStateOf(locations.firstOrNull()?.id ?: "loc_workbench") }

    var selectedImageFile by remember { mutableStateOf<File?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Voice dictation modal trigger
    var showVoiceDictationForField by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                try {
                    val destFile = File.createTempFile("offline_item_", ".jpg", context.cacheDir)
                    context.contentResolver.openInputStream(it)?.use { input ->
                        destFile.outputStream().use { out -> input.copyTo(out) }
                    }
                    selectedImageFile = destFile
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AddCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Add Item",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Photo Section
                Text(
                    "Item Photo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline
                )

                if (selectedImageFile != null && selectedImageFile!!.exists()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = selectedImageFile,
                            contentDescription = "Item Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { selectedImageFile = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove photo", modifier = Modifier.size(18.dp))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("offline_pick_photo_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Select Photo", fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    val sampleFile = File.createTempFile("sample_img_", ".jpg", context.cacheDir)
                                    SampleScanGenerator.generateSampleImageFile(sampleFile, (0..5).random())
                                    selectedImageFile = sampleFile
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sample Art", fontSize = 12.sp)
                        }
                    }
                }

                // Name with Voice Button
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Item Name *") },
                    placeholder = { Text("e.g. Cordless Drill, Espresso Machine...") },
                    modifier = Modifier.fillMaxWidth().testTag("add_item_name_input"),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = { showVoiceDictationForField = "name" },
                            modifier = Modifier.testTag("dictate_name_button")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Dictate name", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )

                // Brand & Model
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = brandText,
                        onValueChange = { brandText = it },
                        label = { Text("Brand") },
                        placeholder = { Text("DeWalt, Sony...") },
                        modifier = Modifier.weight(1f).testTag("add_item_brand_input"),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = modelText,
                        onValueChange = { modelText = it },
                        label = { Text("Model") },
                        placeholder = { Text("DCD791...") },
                        modifier = Modifier.weight(1f).testTag("add_item_model_input"),
                        singleLine = true
                    )
                }

                // Serial Number
                OutlinedTextField(
                    value = serialText,
                    onValueChange = { serialText = it },
                    label = { Text("Serial Number") },
                    placeholder = { Text("Optional serial or barcode") },
                    modifier = Modifier.fillMaxWidth().testTag("add_item_serial_input"),
                    singleLine = true
                )

                // Category & Condition
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = categoryText,
                        onValueChange = { categoryText = it },
                        label = { Text("Category") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = conditionText,
                        onValueChange = { conditionText = it },
                        label = { Text("Condition") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Storage Location Selector
                Text(
                    "Storage Location",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline
                )

                var expandedLocDropdown by remember { mutableStateOf(false) }
                val selectedLocObj = locations.find { it.id == selectedLocationId } ?: locations.firstOrNull()

                ExposedDropdownMenuBox(
                    expanded = expandedLocDropdown,
                    onExpandedChange = { expandedLocDropdown = !expandedLocDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedLocObj?.name ?: "Workbench",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLocDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expandedLocDropdown,
                        onDismissRequest = { expandedLocDropdown = false }
                    ) {
                        locations.forEach { loc ->
                            DropdownMenuItem(
                                text = { Text("${loc.name} (${loc.type})") },
                                onClick = {
                                    selectedLocationId = loc.id
                                    expandedLocDropdown = false
                                }
                            )
                        }
                    }
                }

                // Description & Notes with Voice Dictation
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Description & Notes") },
                    placeholder = { Text("Details, notes, accessories...") },
                    modifier = Modifier.fillMaxWidth().testTag("add_item_notes_input"),
                    minLines = 2,
                    maxLines = 4,
                    trailingIcon = {
                        IconButton(
                            onClick = { showVoiceDictationForField = "notes" },
                            modifier = Modifier.testTag("dictate_notes_button")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Dictate notes", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameText.isNotBlank()) {
                        isSaving = true
                        coroutineScope.launch {
                            val finalFile = selectedImageFile ?: run {
                                val autoFile = File.createTempFile("offline_item_", ".jpg", context.cacheDir)
                                SampleScanGenerator.generateSampleImageFile(autoFile, (0..5).random())
                                autoFile
                            }

                            val scanResult = SingleItemScanResult(
                                name = nameText.trim(),
                                brand = brandText.trim().ifEmpty { null },
                                manufacturer = null,
                                model = modelText.trim().ifEmpty { null },
                                modelNumber = null,
                                serialNumber = serialText.trim().ifEmpty { null },
                                barcode = null,
                                category = categoryText.trim().ifEmpty { "General" },
                                subcategory = null,
                                quantity = 1,
                                condition = conditionText.trim().ifEmpty { "Good" },
                                description = notesText.trim().ifEmpty { "Added offline" },
                                visibleText = emptyList(),
                                accessories = emptyList(),
                                confidence = 1.0f,
                                aiSummary = "Offline item"
                            )

                            val saved = inventoryRepository.saveNewItemFromScan(
                                scanResult = scanResult,
                                imageFile = finalFile,
                                locationId = selectedLocationId
                            )
                            isSaving = false
                            onItemAdded(saved.id)
                        }
                    }
                },
                enabled = nameText.isNotBlank() && !isSaving,
                modifier = Modifier.testTag("save_offline_item_confirm_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Text("Save to Inventory")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Voice Dictation Sub-Dialog
    if (showVoiceDictationForField != null) {
        val field = showVoiceDictationForField!!
        VoiceFieldDictationDialog(
            fieldName = if (field == "name") "Item Name" else "Description & Notes",
            onTranscriptionReceived = { text ->
                if (field == "name") {
                    nameText = text
                } else {
                    notesText = if (notesText.isBlank()) text else "$notesText $text"
                }
                showVoiceDictationForField = null
            },
            onDismiss = { showVoiceDictationForField = null }
        )
    }
}

/**
 * Lightweight, direct speech recognition dialog for dictating into a specific field.
 */
@Composable
fun VoiceFieldDictationDialog(
    fieldName: String,
    onTranscriptionReceived: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val speechManager = remember { SpeechManager(context) }
    val partialText by speechManager.partialText.collectAsStateWithLifecycle()
    val finalText by speechManager.finalText.collectAsStateWithLifecycle()
    val isListening by speechManager.isListening.collectAsStateWithLifecycle()
    val errorMsg by speechManager.errorMessage.collectAsStateWithLifecycle()

    var spokenText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        speechManager.startListening()
    }

    LaunchedEffect(finalText, partialText) {
        val combined = if (finalText.isNotBlank() && partialText.isNotBlank()) {
            "$finalText $partialText"
        } else if (finalText.isNotBlank()) {
            finalText
        } else {
            partialText
        }
        if (combined.isNotBlank()) spokenText = combined
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.stopListening()
        }
    }

    AlertDialog(
        onDismissRequest = {
            speechManager.stopListening()
            onDismiss()
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Dictate $fieldName", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isListening) "Listening... Speak clearly into your microphone" else "Speech captured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                OutlinedTextField(
                    value = spokenText,
                    onValueChange = { spokenText = it },
                    placeholder = { Text("Your words will appear here...") },
                    modifier = Modifier.fillMaxWidth().testTag("voice_field_input"),
                    minLines = 3,
                    maxLines = 5
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            if (isListening) {
                                speechManager.stopListening()
                            } else {
                                speechManager.startListening()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop" else "Record"
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    speechManager.stopListening()
                    if (spokenText.isNotBlank()) {
                        onTranscriptionReceived(spokenText.trim())
                    } else {
                        onDismiss()
                    }
                },
                enabled = spokenText.isNotBlank()
            ) {
                Text("Use This Text")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    speechManager.stopListening()
                    onDismiss()
                }
            ) {
                Text("Cancel")
            }
        }
    )
}
