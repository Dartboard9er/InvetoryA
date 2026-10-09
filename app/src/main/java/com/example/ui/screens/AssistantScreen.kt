package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.gemini.GeminiService
import com.example.ai.modes.AssistantMode
import com.example.data.local.entities.ChatMessageEntity
import com.example.data.repository.AssistantRepository
import com.example.data.repository.InventoryRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    assistantRepository: AssistantRepository,
    inventoryRepository: InventoryRepository? = null,
    geminiService: GeminiService? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedMode by remember { mutableStateOf(AssistantMode.HOME_AI) }
    var isInConversation by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }

    val messages by assistantRepository.getMessagesForMode(selectedMode).collectAsStateWithLifecycle(initialValue = emptyList())
    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Photo picker launcher for visual queries
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { pickedUri ->
            coroutineScope.launch {
                val tempFile = File.createTempFile("asst_photo_", ".jpg", context.cacheDir)
                context.contentResolver.openInputStream(pickedUri)?.use { input ->
                    tempFile.outputStream().use { out -> input.copyTo(out) }
                }
                isInConversation = true
                isThinking = true
                if (geminiService != null) {
                    val result = geminiService.analyzeSingleItem(tempFile)
                    if (result.isSuccess) {
                        val res = result.getOrNull()!!
                        if (inventoryRepository != null) {
                            val saved = inventoryRepository.saveNewItemFromScan(
                                scanResult = res,
                                imageFile = tempFile,
                                locationId = null
                            )
                            assistantRepository.sendMessage("I added '${saved.name}' (${saved.brand ?: "Item"}) to inventory from your photo.", selectedMode)
                        } else {
                            assistantRepository.sendMessage("I identified this item from your photo: '${res.name}' (${res.brand ?: ""}) in ${res.condition ?: "Good"} condition.", selectedMode)
                        }
                    } else {
                        assistantRepository.sendMessage("Analyzed photo. Details: ${result.exceptionOrNull()?.message}", selectedMode)
                    }
                } else {
                    assistantRepository.sendMessage("Attached photo of item.", selectedMode)
                }
                isThinking = false
            }
        }
    }

    // Android Speech Recognizer launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrEmpty()) {
            inputText = spoken
            isInConversation = true
            coroutineScope.launch {
                isThinking = true
                assistantRepository.sendMessage(spoken, selectedMode)
                isThinking = false
                listState.animateScrollToItem(messages.size.coerceAtLeast(0))
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val modeQuickActions = remember(selectedMode) {
        when (selectedMode) {
            AssistantMode.COOKING -> listOf(
                "🍳 What's for dinner?",
                "🥫 Use what I have",
                "🛒 What do I need?",
                "📦 Kitchen stock"
            )
            AssistantMode.GARAGE -> listOf(
                "🔎 Find a Tool",
                "🛠️ What Do I Need?",
                "📖 Equipment Manuals",
                "🔧 Due Maintenance"
            )
            AssistantMode.MAINTENANCE -> listOf(
                "🛠️ What's Due?",
                "⚠️ What Needs Attention?",
                "📖 Open a Manual",
                "🧰 Check Supplies"
            )
            AssistantMode.FIND -> listOf(
                "Where is my drill?",
                "Where are AA batteries?",
                "Where is the pressure washer?",
                "What did I use to mount TV?"
            )
            AssistantMode.HOME_AI -> listOf(
                "Where is my drill?",
                "What can I cook tonight?",
                "What maintenance is due?",
                "Show TV manual"
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showModeSheet = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(selectedMode.emoji, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    selectedMode.title,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                selectedMode.scopeBadge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isInConversation) {
                        IconButton(onClick = { isInConversation = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Modes")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch { assistantRepository.clearHistory(selectedMode) }
                    }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Chat")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isInConversation && messages.isEmpty()) {
                // ASSISTANT LANDING SCREEN (Mode Selection & Quick Start)
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column {
                            Text(
                                text = "Home AI",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "What are we doing?",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Mode Cards
                    item {
                        Text(
                            text = "INTELLIGENT CONTEXT MODES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.1.sp
                        )
                    }

                    items(AssistantMode.entries) { mode ->
                        ModeCard(
                            mode = mode,
                            isSelected = selectedMode == mode,
                            onClick = {
                                selectedMode = mode
                                isInConversation = true
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Automatic Mode Detection", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        "Ask anything below. Home AI routes cooking queries to kitchen stock and maintenance to warranties automatically.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ACTIVE CONVERSATION
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Scope Header notice
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(selectedMode.emoji, style = MaterialTheme.typography.titleMedium)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${selectedMode.title} Active • ${selectedMode.scopeBadge}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        items(messages, key = { it.id }) { msg ->
                            ChatMessageBubble(msg)
                        }

                        if (isThinking) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        "Searching ${selectedMode.scopeBadge.lowercase()} memory...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick action chips for active mode
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(modeQuickActions) { q ->
                    SuggestionChip(
                        onClick = {
                            isInConversation = true
                            coroutineScope.launch {
                                isThinking = true
                                assistantRepository.sendMessage(q, selectedMode)
                                isThinking = false
                            }
                        },
                        label = { Text(q, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Universal Adaptive Input Bar
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Photo Attachment Input
                    IconButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("assistant_attach_photo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Attach Photo",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Microphone Voice Input
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Home AI in ${selectedMode.title} mode...")
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.testTag("voice_assistant_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text(selectedMode.placeholder) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("assistant_input_field"),
                        maxLines = 3,
                        shape = RoundedCornerShape(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            val q = inputText.trim()
                            if (q.isNotEmpty()) {
                                inputText = ""
                                isInConversation = true
                                coroutineScope.launch {
                                    isThinking = true
                                    assistantRepository.sendMessage(q, selectedMode)
                                    isThinking = false
                                }
                            }
                        },
                        enabled = inputText.isNotBlank() && !isThinking,
                        modifier = Modifier
                            .background(
                                if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                            .testTag("assistant_send_button")
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

    // Mode Switcher Modal Bottom Sheet
    if (showModeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModeSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    "Switch Assistant Context Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Each mode restricts context retrieval to keep AI focused.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(16.dp))

                AssistantMode.entries.forEach { mode ->
                    Surface(
                        color = if (selectedMode == mode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedMode = mode
                                showModeSheet = false
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mode.emoji, style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.title, fontWeight = FontWeight.Bold)
                                Text(mode.shortDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Badge {
                                Text(mode.scopeBadge)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun ModeCard(
    mode: AssistantMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("mode_card_${mode.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Text(mode.emoji, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = mode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = mode.scopeBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = mode.shortDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun ChatMessageBubble(msg: ChatMessageEntity) {
    val isUser = msg.role.equals("USER", ignoreCase = true)
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val formattedTime = remember(msg.timestamp) { timeFormat.format(Date(msg.timestamp)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = if (isUser) 0.dp else 1.dp
            ) {
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = formattedTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

