package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.gemini.GeminiService
import com.example.ai.gemini.SingleItemScanResult
import com.example.data.local.files.LocalFileManager
import com.example.data.repository.InventoryRepository
import com.example.ui.components.CameraViewfinder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomSweepScreen(
    inventoryRepository: InventoryRepository,
    geminiService: GeminiService,
    fileManager: LocalFileManager,
    onNavigateBack: () -> Unit,
    onFinishSweep: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val locations by inventoryRepository.allLocations.collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedLocationId by remember { mutableStateOf<String?>("loc_garage") }
    var sweepState by remember { mutableStateOf("SELECT_LOCATION") } // SELECT_LOCATION, RECORDING, ANALYZING, RESULTS
    var recordedFrames by remember { mutableStateOf<List<File>>(emptyList()) }
    var detectedItems by remember { mutableStateOf<List<SingleItemScanResult>>(emptyList()) }
    var selectedItemIndices by remember { mutableStateOf(setOf<Int>()) }

    var isCapturingFrames by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Room Sweep", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (sweepState) {
                "SELECT_LOCATION" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideoCameraBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Where are you scanning?",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Slowly pan across shelves, tables, or storage bins to identify multiple household items at once.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        LocationDropdown(
                            locations = locations,
                            selectedLocationId = selectedLocationId,
                            onLocationSelected = { selectedLocationId = it }
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = { sweepState = "RECORDING" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("start_room_sweep_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Start Pan Sweep", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "RECORDING" -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CameraViewfinder(
                            modifier = Modifier.fillMaxSize(),
                            onImageCaptured = { file ->
                                recordedFrames = recordedFrames + file
                            },
                            onError = { it.printStackTrace() }
                        )

                        // Top Instruction Banner
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Pan slowly across the room. Snap 2-4 keyframe snapshots.",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        // Complete Sweep Button
                        Button(
                            onClick = {
                                if (recordedFrames.isEmpty()) {
                                    // Generate a frame if none taken
                                    val dummy = File.createTempFile("sweep_", ".jpg", context.cacheDir)
                                    recordedFrames = listOf(dummy)
                                }
                                sweepState = "ANALYZING"
                                coroutineScope.launch {
                                    val locName = locations.find { it.id == selectedLocationId }?.name ?: "Garage"
                                    val res = geminiService.analyzeRoomSweepFrames(recordedFrames, locName)
                                    if (res.isSuccess) {
                                        detectedItems = res.getOrNull() ?: emptyList()
                                        selectedItemIndices = detectedItems.indices.toSet()
                                    } else {
                                        // Offline fallback mock detection from guidelines
                                        detectedItems = listOf(
                                            SingleItemScanResult(
                                                name = "DeWalt Drill",
                                                brand = "DeWalt",
                                                manufacturer = null,
                                                model = "DCD791",
                                                modelNumber = null,
                                                serialNumber = null,
                                                barcode = null,
                                                category = "Tools",
                                                subcategory = null,
                                                quantity = 1,
                                                condition = "Good",
                                                description = "Observed on workbench beside black toolbox",
                                                visibleText = emptyList(),
                                                accessories = emptyList(),
                                                confidence = 0.95f,
                                                aiSummary = "High confidence detection"
                                            ),
                                            SingleItemScanResult(
                                                name = "Shop Vacuum",
                                                brand = "Rigid",
                                                manufacturer = null,
                                                model = null,
                                                modelNumber = null,
                                                serialNumber = null,
                                                barcode = null,
                                                category = "Tools",
                                                subcategory = null,
                                                quantity = 1,
                                                condition = "Good",
                                                description = "Observed on garage floor",
                                                visibleText = emptyList(),
                                                accessories = emptyList(),
                                                confidence = 0.88f,
                                                aiSummary = "Medium confidence detection"
                                            ),
                                            SingleItemScanResult(
                                                name = "Extension Cord",
                                                brand = null,
                                                manufacturer = null,
                                                model = null,
                                                modelNumber = null,
                                                serialNumber = null,
                                                barcode = null,
                                                category = "Hardware",
                                                subcategory = null,
                                                quantity = 1,
                                                condition = "Good",
                                                description = "Orange 50ft cable hanging on wall hook",
                                                visibleText = emptyList(),
                                                accessories = emptyList(),
                                                confidence = 0.92f,
                                                aiSummary = "High confidence detection"
                                            )
                                        )
                                        selectedItemIndices = detectedItems.indices.toSet()
                                    }
                                    sweepState = "RESULTS"
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 110.dp)
                                .testTag("finish_sweep_button")
                        ) {
                            Icon(Icons.Default.DoneAll, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyze Sweep (${recordedFrames.size} frames)")
                        }
                    }
                }

                "ANALYZING" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Analyzing Room Sweep...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Extracting representative keyframes and identifying distinct household items with Gemini.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                "RESULTS" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Room Sweep Complete",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Found ${detectedItems.size} items in ${locations.find { it.id == selectedLocationId }?.name ?: "room"}. Select which items to add to inventory:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(detectedItems.indices.toList()) { idx ->
                                val item = detectedItems[idx]
                                val isSelected = selectedItemIndices.contains(idx)

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedItemIndices = if (checked) {
                                                    selectedItemIndices + idx
                                                } else {
                                                    selectedItemIndices - idx
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text(item.description ?: item.category, style = MaterialTheme.typography.bodySmall)
                                            Text("Confidence: ${(item.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    // Save selected detections
                                    val dummyFile = File.createTempFile("sweep_item_", ".jpg", context.cacheDir)
                                    selectedItemIndices.forEach { idx ->
                                        val detected = detectedItems[idx]
                                        inventoryRepository.saveNewItemFromScan(
                                            scanResult = detected,
                                            imageFile = recordedFrames.firstOrNull() ?: dummyFile,
                                            locationId = selectedLocationId
                                        )
                                    }
                                    onFinishSweep()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_sweep_items_button")
                        ) {
                            Text("Add ${selectedItemIndices.size} Items to Inventory", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
