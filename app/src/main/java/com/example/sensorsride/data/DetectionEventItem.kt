package com.example.sensorsride.data


data class DetectionEvent(
    val title: String,
    val timestamp: String,
    val detected: Boolean,
    val confidence: Float,
    val patternType: String = "",
)