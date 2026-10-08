package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun AiScanningOverlay(
    imageFile: File,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_transition")
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanner_line"
    )

    var phaseText by remember { mutableStateOf("Looking at your item...") }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(800)
        phaseText = "Identifying brand & model..."
        kotlinx.coroutines.delay(1200)
        phaseText = "Checking existing inventory..."
        kotlinx.coroutines.delay(1200)
        phaseText = "Almost done..."
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black)
    ) {
        AsyncImage(
            model = imageFile,
            contentDescription = "Captured Scan",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Dark holographic overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
        )

        // Sweeping scanner line
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val totalHeight = maxHeight
            val currentLineY = totalHeight * offsetY

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = currentLineY)
                    .height(3.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                MaterialTheme.colorScheme.primary,
                                Color.White,
                                MaterialTheme.colorScheme.primary,
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Status Card at Bottom of Photo
        Surface(
            color = Color.Black.copy(alpha = 0.8f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = phaseText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
