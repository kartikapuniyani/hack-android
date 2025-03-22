package com.example.sensorsride.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.sensorsride.POTHOLE

@Composable
fun DetectionEventItem(
    type: String,
    timestamp: String,
    patternType: String,
    confidence: Float
) {
    val backgroundColor = when (type) {
        "POTHOLE" -> Color(0xFFFFCDD2) // Light red for potholes
        "SPEED_BUMP" -> Color(0xFFFFF9C4) // Light yellow for speed bumps
        else -> Color(0xFFE0E0E0) // Default gray
    }

    val borderColor = when (type) {
        "POTHOLE" -> Color(0xFFE57373) // Darker red border
        "SPEED_BUMP" -> Color(0xFFFFF176) // Darker yellow border
        else -> Color(0xFFBDBDBD) // Default darker gray
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Type icon
            Icon(
                imageVector = when (type) {
                    "POTHOLE" -> Icons.Rounded.Warning
                    "SPEED_BUMP" -> Icons.Rounded.Warning
                    else -> Icons.Rounded.Info
                },
                contentDescription = type,
                tint = when (type) {
                    "POTHOLE" -> Color(0xFFD32F2F)
                    "SPEED_BUMP" -> Color(0xFFFBC02D)
                    else -> Color.Gray
                },
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = type.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Pattern: $patternType",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Confidence indicator
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Confidence",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Text(
                            text = confidence.toString(), // Direct confidence value
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Confidence bar
                    LinearProgressIndicator(
                        progress = { confidence / 3f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            confidence >= 2f -> Color(0xFF4CAF50) // High confidence - green
                            confidence >= 1f -> Color(0xFFFFC107) // Medium confidence - amber
                            else -> Color(0xFFFF5722) // Low confidence - deep orange
                        },
                        trackColor = Color(0xFFE0E0E0)
                    )
                }
            }
        }
    }
}
