package com.example.sensorsride.domain

import com.example.sensorsride.data.DetectionResult
import kotlin.math.abs


object PotholeAnalyzer {

    fun detectPothole(accelerationValues: List<Float>, baseline: Float = 9.8f): DetectionResult {
        if (accelerationValues.size < 10) return DetectionResult(detected = false, confidence = 0.0f, inputYAccValues = accelerationValues)

        // Smooth the acceleration data
        val smoothedData = kalmanSmoothData(accelerationValues)

        // Compute thresholds based on data statistics
        val stats = calculateStatistics(smoothedData)
        val dropThreshold = maxOf(stats.stdDev * 1.5f, stats.range * 0.12f)
        val recoveryThreshold = maxOf(stats.stdDev * 1.8f, stats.range * 0.15f)
        val normalRange = maxOf(stats.stdDev * 0.8f, stats.range * 0.04f)

        // Pothole signature detection with multi-pattern support
        val patternResults = listOf(
            detectClassicPotholePattern(smoothedData, baseline, dropThreshold, recoveryThreshold, normalRange, accelerationValues),
            detectWidePotholePattern(smoothedData, baseline, dropThreshold * 0.7f, recoveryThreshold, accelerationValues),
            detectCompoundPotholePattern(smoothedData, baseline, dropThreshold, recoveryThreshold, accelerationValues)
        )

        // Find the highest confidence pattern
        return patternResults.maxByOrNull { it.confidence } ?:
        DetectionResult(detected = false, confidence = 0.0f, inputYAccValues = accelerationValues)
    }

    // Detect the classic pothole pattern: sharp drop -> sharp recovery -> normalization
    private fun detectClassicPotholePattern(
        data: List<Float>,
        baseline: Float,
        dropThreshold: Float,
        recoveryThreshold: Float,
        normalRange: Float,
        accelerationValues: List<Float>
    ): DetectionResult {
        var state = "Normal"
        var confidence = 0.0f
        var dropIndex = 0
        var recoveryStrength = 0.0f
        var dropStrength = 0.0f

        for (i in 1 until data.size) {
            val currentValue = data[i]

            when (state) {
                "Normal" -> {
                    // Check for significant drop
                    val drop = baseline - currentValue
                    if (drop > dropThreshold) {
                        state = "Drop"
                        dropIndex = i
                        dropStrength = drop / dropThreshold
                    }
                }
                "Drop" -> {
                    // Once drop is detected, check for recovery
                    val recovery = currentValue - baseline
                    if (recovery > recoveryThreshold * 0.7f) {
                        state = "Recovery"
                        recoveryStrength = recovery / recoveryThreshold
                    } else if (i - dropIndex > 15) {
                        // Reset if recovery doesn't happen within reasonable time
                        state = "Normal"
                    }
                }
                "Recovery" -> {
                    // Check if we return to normal range around baseline
                    if (abs(currentValue - baseline) < normalRange) {
                        // Calculate confidence based on pattern quality
                        val patternDuration = i - dropIndex
                        val durationFactor = if (patternDuration in 3..12) 1.0f else 0.7f
                        confidence = (dropStrength * 0.4f + recoveryStrength * 0.4f + durationFactor * 0.2f) * 0.95f
                        return DetectionResult(
                            detected = true,
                            confidence = confidence,
                            detectionIndex = dropIndex,
                            patternType = "Classic",
                            inputYAccValues = accelerationValues
                        )
                    } else if (i - dropIndex > 20) {
                        // Reset if normalization doesn't happen in reasonable time
                        state = "Normal"
                    }
                }
            }
        }

        return DetectionResult(detected = false, confidence = 0.0f, inputYAccValues = accelerationValues)
    }

    // Detect wide potholes with longer, less severe drops and recoveries
    private fun detectWidePotholePattern(
        data: List<Float>,
        baseline: Float,
        dropThreshold: Float,
        recoveryThreshold: Float,
        accelerationValues: List<Float>
    ): DetectionResult {
        var sustainedDropCount = 0
        var maxDropStrength = 0.0f
        var dropStartIndex = -1

        for (i in 1 until data.size) {
            val currentValue = data[i]
            val drop = baseline - currentValue

            if (drop > dropThreshold * 0.6f) {
                if (sustainedDropCount == 0) {
                    dropStartIndex = i
                }
                sustainedDropCount++
                maxDropStrength = maxOf(maxDropStrength, drop / dropThreshold)
            } else if (sustainedDropCount >= 5 && currentValue > baseline + (recoveryThreshold * 0.5f)) {
                // Recovery after sustained drop
                val patternDuration = i - dropStartIndex
                val durationQuality = if (patternDuration in 8..25) 1.0f else 0.6f
                val confidence = maxDropStrength * 0.5f + durationQuality * 0.5f * 0.85f

                return DetectionResult(
                    detected = true,
                    confidence = confidence,
                    detectionIndex = dropStartIndex,
                    patternType = "Wide",
                    inputYAccValues = accelerationValues
                )
            } else if (sustainedDropCount > 0 && sustainedDropCount < 5) {
                // Reset if drop wasn't sustained long enough
                sustainedDropCount = 0
                maxDropStrength = 0.0f
            }
        }

        return DetectionResult(detected = false, confidence = 0.0f, inputYAccValues = accelerationValues)
    }

    // Detect compound potholes (multiple dips close together)
    private fun detectCompoundPotholePattern(
        data: List<Float>,
        baseline: Float,
        dropThreshold: Float,
        recoveryThreshold: Float,
        accelerationValues: List<Float>
    ): DetectionResult {
        var dropCount = 0
        var firstDropIndex = -1
        var lastRecoveryIndex = -1
        var patternStrength = 0.0f

        for (i in 2 until data.size - 1) {
            val prev = data[i-1]
            val current = data[i]
            val next = data[i+1]

            // Detect rapid direction changes
            val isLocalMinimum = current < prev && current < next && (baseline - current) > dropThreshold * 0.7f
            val isLocalMaximum = current > prev && current > next && (current - baseline) > recoveryThreshold * 0.6f

            if (isLocalMinimum) {
                if (dropCount == 0) {
                    firstDropIndex = i
                }
                dropCount++
                patternStrength += (baseline - current) / dropThreshold
            } else if (isLocalMaximum) {
                lastRecoveryIndex = i
            }

            // Check if we have a compound pattern (multiple dips within a short window)
            if (dropCount >= 2 && lastRecoveryIndex > firstDropIndex && (i - firstDropIndex) <= 25) {
                val confidence = (patternStrength / dropCount) * (dropCount * 0.15f) * 0.9f
                return DetectionResult(
                    detected = true,
                    confidence = minOf(0.95f, confidence),
                    detectionIndex = firstDropIndex,
                    patternType = "Compound",
                    inputYAccValues = accelerationValues
                )
            }
        }

        return DetectionResult(detected = false, confidence = 0.0f,inputYAccValues = accelerationValues)
    }
}