package com.example.sensorsride.data

data class DetectionResult(
    val detected: Boolean,
    val confidence: Float,
    val detectionIndex: Int = -1,
    val patternType: String = "",
    val inputYAccValues: List<Float>,
    val detectionType : String ="",
    val inputGyroPitchValue : List<Float> = emptyList()
)
