package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact pill badge indicating whether the app is currently in Cloud Online mode or Local Offline mode.
 */
@Composable
fun NetworkStatusBadge(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    hasApiKey: Boolean = true
) {
    val isCloudActive = isOnline && hasApiKey
    val backgroundColor = if (isCloudActive) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
    }

    val contentColor = if (isCloudActive) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    val dotColor = if (isCloudActive) {
        Color(0xFF2E7D32) // Fresh emerald green
    } else {
        Color(0xFFE65100) // Clear warm amber
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.testTag("network_status_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Icon(
                imageVector = if (isCloudActive) Icons.Default.CloudDone else Icons.Default.CloudOff,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = if (isCloudActive) "Online" else "Offline Mode",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                fontSize = 11.sp
            )
        }
    }
}

/**
 * Subtle banner shown at top when device is offline or functioning without API,
 * informing users that all features (adding items, taking photos, voice dictation, local database) work seamlessly.
 */
@Composable
fun OfflineNoticeBanner(
    isOnline: Boolean,
    hasApiKey: Boolean,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = !isOnline || !hasApiKey,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
            modifier = modifier
                .fillMaxWidth()
                .testTag("offline_notice_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (!isOnline) "Offline Mode Active" else "Local Mode Active (No Cloud API Required)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = if (!isOnline) {
                            "You can still add items, capture photos, and dictate with voice. Everything saves locally to your device."
                        } else {
                            "Add items, photos, and voice notes freely. Data is stored 100% privately on your device."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
