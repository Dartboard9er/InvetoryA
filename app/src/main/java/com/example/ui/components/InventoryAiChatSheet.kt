package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.gemini.GeminiService
import com.example.ai.gemini.SingleItemScanResult
import com.example.data.local.entities.ItemEntity
import com.example.data.repository.InventoryRepository
import com.example.util.SampleScanGenerator
import kotlinx.coroutines.launch
import java.io.File

data class ChatItemMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val parsedItem: SingleItemScanResult? = null,
    val savedItemId: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryAiChatSheet(
    inventoryRepository: InventoryRepository,
    geminiService: GeminiService,
    onDismiss: () -> Unit,
    onItemViewDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val locations by inventoryRepository.allLocations.collectAsStateWithLifecycle(initialValue = emptyList())

    var messages by remember {
        mutableStateOf(
            listOf(
                ChatItemMessage(
                    isUser = false,
                    text = "Hi! Describe an item you want to add to your inventory. For example:\n• \"I put a box of 100 AA batteries in the garage shelf\"\n• \"Added my DeWalt drill DCD791 to the workbench drawer 2\"\n• \"Sony 65-inch OLED TV in living room\""
                )
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Add via AI Conversation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Turn natural language into structured inventory",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider()

            // Message History
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start
                    ) {
                        Surface(
                            color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = msg.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // If this message parsed an item, display confirmation card
                                msg.parsedItem?.let { parsed ->
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    parsed.name,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            }
                                            if (!parsed.brand.isNullOrEmpty() || !parsed.model.isNullOrEmpty()) {
                                                Text(
                                                    "${parsed.brand ?: ""} ${parsed.model ?: ""}".trim(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            Text(
                                                "Category: ${parsed.category}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )

                                            Spacer(modifier = Modifier.height(10.dp))

                                            if (msg.savedItemId == null) {
                                                Button(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            // Match location if mentioned
                                                            val matchedLoc = locations.find { loc ->
                                                                msg.text.contains(loc.name, ignoreCase = true)
                                                            } ?: locations.firstOrNull()

                                                            // Generate a local sample illustration image for the item
                                                            val targetFile = File.createTempFile("chat_item_", ".jpg", context.cacheDir)
                                                            SampleScanGenerator.generateSampleImageFile(targetFile, (0..5).random())

                                                            val saved = inventoryRepository.saveNewItemFromScan(
                                                                scanResult = parsed,
                                                                imageFile = targetFile,
                                                                locationId = matchedLoc?.id
                                                            )

                                                            // Update message state
                                                            messages = messages.map {
                                                                if (it.id == msg.id) it.copy(savedItemId = saved.id) else it
                                                            } + ChatItemMessage(
                                                                isUser = false,
                                                                text = "Successfully saved '${saved.name}' into your inventory at ${matchedLoc?.name ?: "home"}!"
                                                            )
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth().testTag("confirm_chat_item_button"),
                                                    contentPadding = PaddingValues(vertical = 6.dp)
                                                ) {
                                                    Text("Save to Inventory", style = MaterialTheme.typography.labelMedium)
                                                }
                                            } else {
                                                FilledTonalButton(
                                                    onClick = { onItemViewDetail(msg.savedItemId) },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentPadding = PaddingValues(vertical = 6.dp)
                                                ) {
                                                    Text("View in Inventory", style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isProcessing) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Analyzing and structuring your item...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Input Row
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("E.g. DeWalt 20V battery in garage...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_inventory_chat_input"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val prompt = inputText.trim()
                            if (prompt.isNotEmpty()) {
                                inputText = ""
                                messages = messages + ChatItemMessage(isUser = true, text = prompt)
                                coroutineScope.launch {
                                    isProcessing = true
                                    val locsStr = locations.joinToString(", ") { "${it.name} (${it.type})" }
                                    val result = geminiService.parseItemFromConversation(prompt, locsStr)
                                    isProcessing = false

                                    if (result.isSuccess) {
                                        val parsed = result.getOrNull()!!
                                        messages = messages + ChatItemMessage(
                                            isUser = false,
                                            text = "I parsed the item details. Review and tap confirm to add to your inventory:",
                                            parsedItem = parsed
                                        )
                                    } else {
                                        // Fallback manual structure if offline or no key
                                        val fallback = SingleItemScanResult(
                                            name = prompt.take(30).trim(),
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
                                            description = prompt,
                                            visibleText = emptyList(),
                                            accessories = emptyList(),
                                            confidence = 0.9f,
                                            aiSummary = "Added via conversation"
                                        )
                                        messages = messages + ChatItemMessage(
                                            isUser = false,
                                            text = "Saved note locally. Tap confirm to add:",
                                            parsedItem = fallback
                                        )
                                    }
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isProcessing,
                        modifier = Modifier
                            .background(
                                if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                            .testTag("send_inventory_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}
