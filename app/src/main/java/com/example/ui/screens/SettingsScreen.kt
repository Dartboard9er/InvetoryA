package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai.gemini.AIConfiguration
import com.example.ai.gemini.GeminiService
import com.example.data.local.files.LocalFileManager
import com.example.data.repository.BackupRepository
import kotlinx.coroutines.launch
import java.io.File
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    geminiService: GeminiService,
    backupRepository: BackupRepository,
    fileManager: LocalFileManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var apiKeyText by remember { mutableStateOf(geminiService.getApiKey()) }
    var isTestingKey by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf<String?>(null) }

    var storageUsage by remember { mutableStateOf(fileManager.calculateStorageUsage()) }

    var isBackingUp by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }

    val decimalFormat = remember { DecimalFormat("#,##0.00") }

    fun formatBytes(bytes: Long): String {
        return if (bytes < 1024 * 1024) {
            "${bytes / 1024} KB"
        } else {
            "${decimalFormat.format(bytes.toDouble() / (1024 * 1024))} MB"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Privacy", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // BYOK Gemini API Section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bring Your Own Gemini API Key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Home AI is local-first. Your inventory stays on your device. When you scan an item or ask the assistant, selected images/text are sent to Gemini using your key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = { apiKeyText = it },
                        label = { Text("Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
                        modifier = Modifier.fillMaxWidth().testTag("gemini_api_key_input"),
                        singleLine = true,
                        trailingIcon = {
                            if (apiKeyText.isNotBlank()) {
                                IconButton(onClick = {
                                    apiKeyText = ""
                                    geminiService.clearApiKey()
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                geminiService.saveApiKey(apiKeyText)
                                Toast.makeText(context, "API Key saved securely", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).testTag("save_api_key_button")
                        ) {
                            Text("Save Key")
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    isTestingKey = true
                                    connectionStatus = null
                                    geminiService.saveApiKey(apiKeyText)
                                    val res = geminiService.testConnection()
                                    isTestingKey = false
                                    connectionStatus = if (res.isSuccess) {
                                        "Connected: ${res.getOrNull()}"
                                    } else {
                                        "Error: ${res.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("test_api_key_button")
                        ) {
                            if (isTestingKey) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Test Key")
                            }
                        }
                    }

                    connectionStatus?.let { status ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (status.startsWith("Connected")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Configured Models: Vision: ${AIConfiguration.MODEL_FAST_VISION} • Reasoning: ${AIConfiguration.MODEL_REASONING}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Storage Dashboard
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("On-Device Local Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    DetailRow(label = "Total App Data", value = formatBytes(storageUsage.totalBytes))
                    DetailRow(label = "Photographs", value = formatBytes(storageUsage.photosBytes))
                    DetailRow(label = "Videos & Sweeps", value = formatBytes(storageUsage.videosBytes))
                    DetailRow(label = "Room Database", value = formatBytes(storageUsage.databaseBytes))
                    DetailRow(label = "Temporary Cache", value = formatBytes(storageUsage.temporaryBytes))

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            fileManager.clearTemporaryFiles()
                            storageUsage = fileManager.calculateStorageUsage()
                            Toast.makeText(context, "Temporary cache cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Temporary Cache")
                    }
                }
            }

            // Backup & Restore
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backup & Portability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Because Home AI does NOT use a cloud backend, always export a ZIP backup before uninstalling the application or switching devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isBackingUp = true
                                    backupStatusMessage = "Creating ZIP package..."
                                    val backupFile = File(context.cacheDir, "HomeAI_Backup_${System.currentTimeMillis()}.zip")
                                    val res = backupRepository.exportBackup(backupFile)
                                    isBackingUp = false
                                    if (res.isSuccess) {
                                        backupStatusMessage = "Backup ready: ${backupFile.name} (${formatBytes(backupFile.length())})"
                                    } else {
                                        backupStatusMessage = "Backup failed: ${res.exceptionOrNull()?.message}"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_backup_button")
                        ) {
                            if (isBackingUp) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Export Backup")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val csvRes = backupRepository.exportCsv()
                                    if (csvRes.isSuccess) {
                                        backupStatusMessage = "CSV generated (${csvRes.getOrNull()?.lines()?.size ?: 0} rows)"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("export_csv_button")
                        ) {
                            Text("Export CSV")
                        }
                    }

                    backupStatusMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Local-First Privacy Notice
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Privacy & Architecture Guarantee", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• Zero Firebase SDKs\n• Zero third-party trackers\n• Permanent data saved only in Room SQLite and local files on this device\n• Offline-first: scanning and search work in Airplane mode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
