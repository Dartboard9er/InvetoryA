package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entities.AiJobEntity
import com.example.data.repository.InventoryRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessingQueueScreen(
    inventoryRepository: InventoryRepository,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val pendingJobs by inventoryRepository.pendingJobs.collectAsStateWithLifecycle(initialValue = emptyList())
    val sdf = remember { SimpleDateFormat("MMM d, h:mm a", Locale.US) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Processing Queue", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "When offline, camera scans are saved safely on your phone. They will automatically process when internet access returns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (pendingJobs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Queue is all caught up!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "All scans have been analyzed and indexed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pendingJobs) { job ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Photo Scan Job",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Created ${sdf.format(Date(job.createdAt))} • Status: ${job.status}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            inventoryRepository.processQueuedScanJob(job)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
