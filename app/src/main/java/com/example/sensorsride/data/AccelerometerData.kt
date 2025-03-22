package com.example.sensorsride.data

import androidx.annotation.Keep

@Keep
data class AccelerometerData(
    val xAxis: Double,
    val yAxis: Double,
    val zAxis: Double,
    val timeStamp: Long
)
