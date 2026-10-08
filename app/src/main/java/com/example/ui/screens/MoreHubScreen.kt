package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.gemini.GeminiService
import com.example.data.local.files.LocalFileManager
import com.example.data.repository.BackupRepository
import com.example.data.repository.InventoryRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreHubScreen(
    inventoryRepository: InventoryRepository,
    geminiService: GeminiService,
    backupRepository: BackupRepository,
    fileManager: LocalFileManager,
    onNavigateToLocations: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToQueue: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val items by inventoryRepository.allActiveItems.collectAsStateWithLifecycle(initialValue = emptyList())
    val locations by inventoryRepository.allLocations.collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingJobs by inventoryRepository.pendingJobs.collectAsStateWithLifecycle(initialValue = emptyList())

    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("More", fontWeight = FontWeight.Bold) }
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
            // Household Hub Section
            Text(
                "Your Home",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    MoreNavRow(
                        icon = Icons.Default.Place,
                        title = "Locations & Rooms",
                        subtitle = "${locations.size} mapped spaces (${items.size} items stored)",
                        onClick = onNavigateToLocations
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    MoreNavRow(
                        icon = Icons.Default.PendingActions,
                        title = "AI Processing Queue",
                        subtitle = if (pendingJobs.isNotEmpty()) "${pendingJobs.size} scans waiting for internet" else "All scans completed",
                        onClick = onNavigateToQueue
                    )
                }
            }

            // Intelligence & Management Section
            Text(
                "Intelligence & System",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column {
                    MoreNavRow(
                        icon = Icons.Default.Settings,
                        title = "Gemini API & AI Settings",
                        subtitle = if (geminiService.getApiKey().isNotEmpty()) "Connected • BYOK Active" else "Setup API Key for AI extractions",
                        onClick = onNavigateToSettings
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    MoreNavRow(
                        icon = Icons.Default.Backup,
                        title = "Backup, Export & Storage",
                        subtitle = "Export ZIP archives, CSV spreadsheets, or clear cache",
                        onClick = onNavigateToSettings
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    MoreNavRow(
                        icon = Icons.Default.Info,
                        title = "About Home AI",
                        subtitle = "Version 1.0 • Privacy & Local-first architecture",
                        onClick = { showAboutDialog = true }
                    )
                }
            }

            // Architecture Assurance Banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Local-First Guarantee",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "All inventory records, photos, and history reside on this device. Cloud AI requests occur strictly on-demand.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Home AI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Home AI — Your home, remembered.", fontWeight = FontWeight.Bold)
                    Text("Native Android application powered by Jetpack Compose, Material 3, Room SQLite, CameraX, and Google Gemini API.")
                    Text("Designed with zero Firebase reliance, preserving permanent data locally on device.")
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun MoreNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
