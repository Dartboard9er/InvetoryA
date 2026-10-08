package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.gemini.GeminiService
import com.example.ai.gemini.SingleItemScanResult
import com.example.data.local.entities.AiMemoryEntity
import com.example.data.local.entities.ItemEntity
import com.example.data.local.entities.LocationEntity
import com.example.data.repository.InventoryRepository
import com.example.util.SampleScanGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class InterpretedIntentType {
    ITEM_NOTE,
    RELOCATION,
    STATUS_NOTE,
    FIND_REQUEST,
    NEW_ITEM
}

data class InterpretedVoiceMemory(
    val title: String,
    val type: InterpretedIntentType,
    val summary: String,
    val rawTranscript: String,
    val targetItem: ItemEntity? = null,
    val targetLocation: LocationEntity? = null,
    val parsedItemResult: SingleItemScanResult? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCaptureSheet(
    inventoryRepository: InventoryRepository,
    geminiService: GeminiService,
    onDismiss: () -> Unit,
    onNavigateToItemDetail: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val speechManager = remember { SpeechManager(context) }

    val partialText by speechManager.partialText.collectAsStateWithLifecycle()
    val finalText by speechManager.finalText.collectAsStateWithLifecycle()
    val isListening by speechManager.isListening.collectAsStateWithLifecycle()
    val speechError by speechManager.errorMessage.collectAsStateWithLifecycle()
    val isOnDevice by speechManager.isOnDevice.collectAsStateWithLifecycle()

    var editableTranscript by remember { mutableStateOf("") }
    var isInterpreting by remember { mutableStateOf(false) }
    var interpretationResult by remember { mutableStateOf<InterpretedVoiceMemory?>(null) }
    var actionSavedMessage by remember { mutableStateOf<String?>(null) }

    val allItems by inventoryRepository.allActiveItems.collectAsStateWithLifecycle(initialValue = emptyList())
    val allLocations by inventoryRepository.allLocations.collectAsStateWithLifecycle(initialValue = emptyList())

    // Keep editableTranscript synced when new speech results arrive
    LaunchedEffect(finalText, partialText) {
        if (isListening) {
            val combined = if (finalText.isNotBlank() && partialText.isNotBlank()) {
                "$finalText $partialText"
            } else if (finalText.isNotBlank()) {
                finalText
            } else {
                partialText
            }
            if (combined.isNotBlank()) {
                editableTranscript = combined
            }
        } else if (finalText.isNotBlank() && editableTranscript.isBlank()) {
            editableTranscript = finalText
        }
    }

    // Permission launcher for RECORD_AUDIO
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            speechManager.startListening()
        }
    }

    // Start listening on launch if permission granted
    LaunchedEffect(Unit) {
        if (hasAudioPermission) {
            speechManager.startListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.stopListening()
        }
    }

    // Logic to interpret memory clip with Gemini or local heuristic
    fun interpretTranscript(transcript: String) {
        if (transcript.isBlank()) return
        speechManager.stopListening()
        isInterpreting = true
        actionSavedMessage = null

        coroutineScope.launch {
            val interpretation = withContext(Dispatchers.IO) {
                // Find potential matched item and location
                val matchedItem = allItems.firstOrNull { item ->
                    transcript.contains(item.name, ignoreCase = true) ||
                            (!item.brand.isNullOrEmpty() && transcript.contains(item.brand, ignoreCase = true)) ||
                            (!item.model.isNullOrEmpty() && transcript.contains(item.model, ignoreCase = true))
                }

                val matchedLoc = allLocations.firstOrNull { loc ->
                    transcript.contains(loc.name, ignoreCase = true)
                }

                val apiKey = geminiService.getApiKey()
                if (apiKey.isNotEmpty()) {
                    try {
                        val prompt = buildString {
                            append("You are Home AI's Voice Memory Clip interpreter. ")
                            append("The user spoke: \"$transcript\". ")
                            append("Available items in home: ${allItems.take(15).joinToString { it.name }}. ")
                            append("Available locations: ${allLocations.joinToString { it.name }}. ")
                            append("Determine the intent. Valid intents: ITEM_NOTE (information about an existing item), ")
                            append("RELOCATION (user moved an item somewhere), STATUS_NOTE (e.g. low batteries, broken, replaced filter), ")
                            append("NEW_ITEM (user added an item to a location), FIND_REQUEST (user asking where something is). ")
                            append("Return ONLY JSON:\n")
                            append("{\n")
                            append("  \"title\": \"Short headline, e.g. Relocated Drill or Low Salt Alert\",\n")
                            append("  \"intent\": \"ITEM_NOTE | RELOCATION | STATUS_NOTE | NEW_ITEM | FIND_REQUEST\",\n")
                            append("  \"summary\": \"Concise 1-sentence note of what to remember\",\n")
                            append("  \"matchedItemName\": \"${matchedItem?.name ?: ""}\",\n")
                            append("  \"matchedLocationName\": \"${matchedLoc?.name ?: ""}\"\n")
                            append("}")
                        }

                        val jsonResp = geminiService.parseMemoryIntent(prompt)
                        if (jsonResp != null) {
                            val intentStr = jsonResp.optString("intent", "ITEM_NOTE")
                            val intentType = try {
                                InterpretedIntentType.valueOf(intentStr)
                            } catch (_: Exception) {
                                InterpretedIntentType.ITEM_NOTE
                            }

                            return@withContext InterpretedVoiceMemory(
                                title = jsonResp.optString("title", "Voice Memory Clip"),
                                type = intentType,
                                summary = jsonResp.optString("summary", transcript),
                                rawTranscript = transcript,
                                targetItem = matchedItem,
                                targetLocation = matchedLoc
                            )
                        }
                    } catch (_: Exception) {
                        // fallback to local below
                    }
                }

                // Local intelligent heuristic fallback (100% offline)
                val lower = transcript.lowercase()
                val type = when {
                    lower.contains("moved") || lower.contains("put") || lower.contains("placed") -> InterpretedIntentType.RELOCATION
                    lower.contains("broken") || lower.contains("empty") || lower.contains("low") || lower.contains("replace") || lower.contains("filter") -> InterpretedIntentType.STATUS_NOTE
                    lower.contains("where") || lower.contains("find") -> InterpretedIntentType.FIND_REQUEST
                    matchedItem == null && (lower.contains("bought") || lower.contains("new") || lower.contains("added")) -> InterpretedIntentType.NEW_ITEM
                    else -> InterpretedIntentType.ITEM_NOTE
                }

                val title = when (type) {
                    InterpretedIntentType.RELOCATION -> "Location Update"
                    InterpretedIntentType.STATUS_NOTE -> "Maintenance / Status Note"
                    InterpretedIntentType.FIND_REQUEST -> "Inventory Query"
                    InterpretedIntentType.NEW_ITEM -> "New Item Recorded"
                    InterpretedIntentType.ITEM_NOTE -> "Household Memory Clip"
                }

                InterpretedVoiceMemory(
                    title = title,
                    type = type,
                    summary = transcript,
                    rawTranscript = transcript,
                    targetItem = matchedItem,
                    targetLocation = matchedLoc
                )
            }

            interpretationResult = interpretation
            isInterpreting = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            speechManager.stopListening()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Tell Home AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Speak naturally. I'll turn it into a memory.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        speechManager.stopListening()
                        onDismiss()
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Body Area
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Listening State Indicator (NO WAVEFORM, NO EQUALIZER as strictly required)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isListening) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Listening...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.outline)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (editableTranscript.isNotBlank()) "Paused" else "Ready",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        if (isOnDevice) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "On-Device Speech",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Error message banner if speech service unavailable
                if (speechError != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = speechError ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // The Words: LIVE TRANSCRIPT (Primary visual focus)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_transcript_card"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIVE TRANSCRIPT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                if (editableTranscript.isNotBlank()) {
                                    TextButton(
                                        onClick = {
                                            speechManager.reset()
                                            editableTranscript = ""
                                            interpretationResult = null
                                            actionSavedMessage = null
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Editable live text field so user can adjust what was heard
                            OutlinedTextField(
                                value = editableTranscript,
                                onValueChange = {
                                    editableTranscript = it
                                },
                                placeholder = {
                                    Text(
                                        "«I moved the spare furnace filters into the basement storage room on the left shelf.»",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("voice_transcript_input"),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 26.sp
                                ),
                                shape = RoundedCornerShape(12.dp),
                                minLines = 3,
                                maxLines = 6
                            )
                        }
                    }
                }

                // Quick Prompt Starters if empty
                if (editableTranscript.isBlank()) {
                    item {
                        Column {
                            Text(
                                text = "TRY SAYING:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            listOf(
                                "I moved the socket set to the bottom drawer of the rolling workbench.",
                                "Water softener is low on salt. Add two bags this weekend.",
                                "The air filter size for the upstairs furnace is 20x25x4.",
                                "Left spare house keys in the green bowl by the front door."
                            ).forEach { example ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            editableTranscript = example
                                            speechManager.stopListening()
                                            interpretTranscript(example)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.FormatQuote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = example,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Processing Indicator
                if (isInterpreting) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Interpreting clip and connecting with household knowledge...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Interpretation Result Card
                interpretationResult?.let { interp ->
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("memory_clip_result_card"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = when (interp.type) {
                                                InterpretedIntentType.RELOCATION -> Icons.Default.Place
                                                InterpretedIntentType.STATUS_NOTE -> Icons.Default.Warning
                                                InterpretedIntentType.NEW_ITEM -> Icons.Default.AddCircle
                                                InterpretedIntentType.FIND_REQUEST -> Icons.Default.Search
                                                InterpretedIntentType.ITEM_NOTE -> Icons.Default.Memory
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = interp.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = interp.type.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = interp.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Linked item if identified
                                interp.targetItem?.let { item ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Inventory2,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = "Attached to item: ${item.name}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Category: ${item.category}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Linked location if identified
                                interp.targetLocation?.let { loc ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Place,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Location: ${loc.name} (${loc.type})",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                // Action confirmation button
                                if (actionSavedMessage == null) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                // If this is a new item, create and save the item in inventory
                                                var createdItemId: String? = null
                                                if (interp.type == InterpretedIntentType.NEW_ITEM) {
                                                    val targetLoc = interp.targetLocation ?: allLocations.firstOrNull()
                                                    val targetFile = File.createTempFile("voice_item_", ".jpg", context.cacheDir)
                                                    SampleScanGenerator.generateSampleImageFile(targetFile, (0..5).random())

                                                    var itemName = interp.summary.take(40).trim()
                                                    val stripWords = listOf("i bought a ", "i bought an ", "bought a ", "bought an ", "bought ", "added a ", "added an ", "added ")
                                                    for (w in stripWords) {
                                                        if (itemName.startsWith(w, ignoreCase = true)) {
                                                            itemName = itemName.substring(w.length).trim()
                                                            break
                                                        }
                                                    }
                                                    if (itemName.isBlank() || itemName == "New Item Recorded") {
                                                        itemName = "Household Item"
                                                    }

                                                    val parsed = interp.parsedItemResult ?: SingleItemScanResult(
                                                        name = itemName.replaceFirstChar { it.uppercase() },
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
                                                        description = interp.rawTranscript,
                                                        visibleText = emptyList(),
                                                        accessories = emptyList(),
                                                        confidence = 0.9f,
                                                        aiSummary = "Added via voice memory clip"
                                                    )
                                                    val savedItem = inventoryRepository.saveNewItemFromScan(
                                                        scanResult = parsed,
                                                        imageFile = targetFile,
                                                        locationId = targetLoc?.id
                                                    )
                                                    createdItemId = savedItem.id
                                                    actionSavedMessage = "✅ Added '${savedItem.name}' to your inventory at ${targetLoc?.name ?: "Home"}!"
                                                }

                                                // Save to AI Memory table
                                                val memory = AiMemoryEntity(
                                                    id = UUID.randomUUID().toString(),
                                                    type = interp.type.name,
                                                    itemId = createdItemId ?: interp.targetItem?.id,
                                                    locationId = interp.targetLocation?.id,
                                                    content = interp.summary,
                                                    importance = 0.8f,
                                                    source = "VOICE_CLIP"
                                                )
                                                inventoryRepository.saveMemory(memory)

                                                // If it's a relocation and we have an item and location, update item location
                                                if (interp.type == InterpretedIntentType.RELOCATION && interp.targetItem != null && interp.targetLocation != null) {
                                                    inventoryRepository.updateItem(
                                                        interp.targetItem.copy(
                                                            currentLocationId = interp.targetLocation.id,
                                                            notes = if (interp.targetItem.notes.isNullOrEmpty()) interp.summary else "${interp.targetItem.notes}\n${interp.summary}"
                                                        )
                                                    )
                                                    actionSavedMessage = "✅ Moved '${interp.targetItem.name}' to ${interp.targetLocation.name}!"
                                                } else if (actionSavedMessage == null) {
                                                    actionSavedMessage = "Saved to Home AI Brain! Your household memory has been permanently recorded."
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("save_voice_memory_button"),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Save Memory to Inventory", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = actionSavedMessage ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Action Bar: Mic Control & Interpret Button
            Surface(
                tonalElevation = 3.dp,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Mic toggle button
                    FloatingActionButton(
                        onClick = {
                            if (isListening) {
                                speechManager.stopListening()
                            } else {
                                if (hasAudioPermission) {
                                    speechManager.startListening()
                                } else {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        containerColor = if (isListening) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isListening) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("voice_capture_mic_toggle")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop Recording" else "Start Recording",
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Interpret / Process Clip Button
                    Button(
                        onClick = {
                            interpretTranscript(editableTranscript)
                        },
                        enabled = editableTranscript.isNotBlank() && !isInterpreting,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("voice_capture_interpret_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isInterpreting) "Interpreting..." else "Remember This",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
