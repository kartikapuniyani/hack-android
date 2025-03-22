package com.example.sensorsride.data

import androidx.annotation.Keep

@Keep
data class LocationData(
    val latitude: Double,
    val longitude : Double,
    val accuracy : Double,
    val timeStamp : Long
)
