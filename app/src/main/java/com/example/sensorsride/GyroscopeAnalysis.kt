package com.example.sensorsride


data class GyroscopeAnalysis(
    val avgX: Float,
    val avgY: Float,
    val maxX: Float,
    val maxY: Float,
    val minX: Float,
    val minY: Float,
    val significantPotholeRotation: Boolean,
    val significantBumpRotation: Boolean
) {

}
