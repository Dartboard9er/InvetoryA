package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.gemini.GeminiService
import com.example.data.local.files.LocalFileManager
import com.example.data.repository.BackupRepository
import com.example.data.repository.InventoryRepository
import com.example.ui.components.NetworkStatusBadge
import com.example.util.NetworkMonitor

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
    val networkMonitor = remember { NetworkMonitor(context) }
    val isOnline by networkMonitor.isOnlineFlow.collectAsStateWithLifecycle(initialValue = networkMonitor.isCurrentlyOnline())
    val hasApiKey = remember(geminiService) { geminiService.getApiKey().isNotBlank() }

    val items by inventoryRepository.allActiveItems.collectAsStateWithLifecycle(initialValue = emptyList())
    val locations by inventoryRepository.allLocations.collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingJobs by inventoryRepository.pendingJobs.collectAsStateWithLifecycle(initialValue = emptyList())
    val readyProfiles by inventoryRepository.allReadyProfiles.collectAsStateWithLifecycle(initialValue = emptyList())

    var showAboutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "More & Settings",
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                },
                actions = {
                    NetworkStatusBadge(
                        isOnline = isOnline,
                        hasApiKey = hasApiKey,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Executive Household Overview Grid
            Text(
                "HOUSEHOLD OVERVIEW",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatMetricCard(
                    title = "Items",
                    count = "${items.size}",
                    icon = Icons.Default.Inventory2,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Rooms",
                    count = "${locations.size}",
                    icon = Icons.Default.Place,
                    modifier = Modifier.weight(1f)
                )
                StatMetricCard(
                    title = "Intel",
                    count = "${readyProfiles.size}",
                    icon = Icons.Default.AutoAwesome,
                    modifier = Modifier.weight(1f)
                )
            }

            // Spaces & Mapping Section
            Text(
                "SPACES & PIPELINE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.1.sp
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column {
                    MoreNavRow(
                        icon = Icons.Default.MeetingRoom,
                        title = "Locations & Rooms",
                        subtitle = "${locations.size} mapped spaces (${items.size} items assigned)",
                        badgeText = if (locations.isEmpty()) "Setup" else null,
                        onClick = onNavigateToLocations
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    MoreNavRow(
                        icon = Icons.Default.PendingActions,
                        title = "AI Processing Queue",
                        subtitle = if (pendingJobs.isNotEmpty()) "${pendingJobs.size} scans waiting for upload" else "All scans completed",
                        badgeText = if (pendingJobs.isNotEmpty()) "${pendingJobs.size} queued" else "Clear",
                        badgeContainerColor = if (pendingJobs.isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        onClick = onNavigateToQueue
                    )
                }
            }

            // Intelligence & System Section
            Text(
                "INTELLIGENCE & SYSTEM",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.1.sp
            )

            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            ) {
                Column {
                    MoreNavRow(
                        icon = Icons.Default.Settings,
                        title = "Gemini API & AI Settings",
                        subtitle = if (geminiService.getApiKey().isNotEmpty()) "Connected • Gemini 2.5 Flash BYOK Active" else "Setup API Key for AI deep research",
                        badgeText = if (geminiService.getApiKey().isNotEmpty()) "Active" else "Action Needed",
                        badgeContainerColor = if (geminiService.getApiKey().isNotEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                        onClick = onNavigateToSettings
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    MoreNavRow(
                        icon = Icons.Default.Backup,
                        title = "Backup & Data Export",
                        subtitle = "Export ZIP archives, CSV spreadsheets, or clear cache",
                        onClick = onNavigateToSettings
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    MoreNavRow(
                        icon = Icons.Default.Info,
                        title = "About Home AI",
                        subtitle = "Version 1.0 • Privacy & Local-first SQLite architecture",
                        onClick = { showAboutDialog = true }
                    )
                }
            }

            // Local-First Guarantee Banner
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            "Local-First Guarantee",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "All photos, manuals, and inventory records are preserved locally on this device. Cloud AI requests occur strictly on-demand.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("About Home AI", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Home AI — Your home, remembered.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("A modern, local-first inventory & knowledge extraction system for Android built with Jetpack Compose, Material 3, Room SQLite, CameraX, and Google Gemini.")
                    Text("All physical items, locations, user manuals, and forum intelligence are saved on-device for permanent reliability.")
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }, shape = RoundedCornerShape(12.dp)) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun StatMetricCard(
    title: String,
    count: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MoreNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgeContainerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (badgeText != null) {
            Surface(
                color = badgeContainerColor,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(horizontal = 6.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}
