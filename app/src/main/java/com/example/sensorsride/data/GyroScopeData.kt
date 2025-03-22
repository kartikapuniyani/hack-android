package com.example.sensorsride.data

import androidx.annotation.Keep

@Keep
data class GyroScopeData(
    val xAxis: Double,
    val yAxis: Double,
    val zAxis: Double,
    val timeStamp: Long
)
