package com.example.sensorsride.data.models

import androidx.annotation.Keep

@Keep
data class SensorDetectionResult(
    val  message : String,
    val signedUrl : String,
    val confirmed : Boolean,
    val pothole : Boolean
)
