package com.example.sensorsride.data

import androidx.annotation.Keep

@Keep
data class SensorDetectionRequest(
    val anomalyType: String,
    val accelValues: List<AccelerometerData>,
    val gyroValues : List<GyroScopeData>,
    val location: LocationData,
    val fileName : String
)
