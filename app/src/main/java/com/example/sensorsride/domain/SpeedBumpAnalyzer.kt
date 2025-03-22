package com.example.sensorsride.domain

import android.content.Context
import com.example.sensorsride.data.DetectionResult
import java.io.File
import kotlin.math.abs

object SpeedBumpAnalyzer {
    fun detectSpeedBump(accelerationValues: List<Float>, baseline: Float = 9.8f): DetectionResult {
        if (accelerationValues.size < 10) return DetectionResult(detected = false, confidence = 0.0f, inputYAccValues = accelerationValues)

        // Smooth the acceleration data
        val smoothedData = kalmanSmoothData(accelerationValues)

        // Compute thresholds based on data statistics
        val stats = calculateStatistics(smoothedData)
        val elevationThreshold = maxOf(stats.stdDev * 1.5f, stats.range * 0.12f)
        val durationMinimum = 3 // Minimum number of samples for the elevation phase
        val durationMaximum = 20 // Maximum number of samples for the entire bump pattern

        // Speed bump patterns
        val patternResults = listOf(
            detectClassicSpeedBump(smoothedData, baseline, elevationThreshold, accelerationValues),
            detectExtendedSpeedBump(smoothedData, baseline, elevationThreshold * 0.7f, accelerationValues),
            detectDoubleBump(smoothedData, baseline, elevationThreshold * 0.8f, accelerationValues)
        )

        // Find the highest confidence pattern
        return patternResults.maxByOrNull { it.confidence } ?:
        DetectionResult(detected = false, confidence = 0.0f, inputYAccValues = accelerationValues)
    }

    // Detect classic speed bump pattern: gradual rise -> sustained elevation -> gradual descent
    private fun detectClassicSpeedBump(
        data: List<Float>,
        baseline: Float,
        elevationThreshold: Float,
        accelerationValues: List<Float>
    ): DetectionResult {
        var state = "Normal"
        var confidence = 0.0f
        var riseStartIndex = 0
        var elevationStartIndex = 0
        var elevationEndIndex = 0
        var maxElevation = 0.0f
        var sustainedElevationCount = 0

        for (i in 1 until data.size) {
            val currentValue = data[i]
            val previousValue = data[i-1]

            when (state) {
                "Normal" -> {
                    // Check for gradual rise beginning
                    if (currentValue > previousValue && currentValue > baseline + (elevationThreshold * 0.3f)) {
                        state = "Rising"
                        riseStartIndex = i
                    }
                }
                "Rising" -> {
                    // Continue monitoring the rise
                    if (currentValue > baseline + elevationThreshold) {
                        state = "Elevated"
                        elevationStartIndex = i
                        maxElevation = currentValue - baseline
                    } else if (currentValue < previousValue && i - riseStartIndex > 6) {
                        // If we start descending before significant elevation, reset
                        state = "Normal"
                    }
                }
                "Elevated" -> {
                    // Track sustained elevation
                    if (currentValue > baseline + (elevationThreshold * 0.7f)) {
                        sustainedElevationCount++
                        maxElevation = maxOf(maxElevation, currentValue - baseline)
                    } else if (currentValue < baseline + (elevationThreshold * 0.5f)) {
                        // Started descending
                        state = "Descending"
                        elevationEndIndex = i
                    }
                }
                "Descending" -> {
                    // Check if we've returned to normal
                    if (abs(currentValue - baseline) < elevationThreshold * 0.3f) {
                        // Calculate total pattern duration
                        val patternDuration = i - riseStartIndex

                        // Speed bumps typically have a symmetric pattern with:
                        // 1. Gradual rise
                        // 2. Short sustained elevation
                        // 3. Gradual descent

                        // Calculate confidence based on pattern quality
                        val elevationQuality = maxElevation / elevationThreshold
                        val sustainedQuality = minOf(1.0f, sustainedElevationCount / 3.0f)
                        val durationQuality = if (patternDuration in 8..20) 1.0f else 0.7f
                        val symmetryFactor = calculateSymmetry(data, riseStartIndex, elevationStartIndex, elevationEndIndex, i)

                        confidence = (elevationQuality * 0.3f + sustainedQuality * 0.2f +
                                durationQuality * 0.2f + symmetryFactor * 0.3f) * 0.95f

                        return DetectionResult(
                            detected = true,
                            confidence = confidence,
                            detectionIndex = riseStartIndex,
                            patternType = "ClassicBump",
                            inputYAccValues = accelerationValues
                        )
                    } else if (i - elevationEndIndex > 10) {
                        // Reset if descent takes too long
                        state = "Normal"
                    }
                }
            }
        }

        return DetectionResult(detected = false, confidence = 0.0f, inputYAccValues = accelerationValues)
    }

    // Detect elongated speed bumps (like speed tables or longer humps)
    private fun detectExtendedSpeedBump(
        data: List<Float>,
        baseline: Float,
        elevationThreshold: Float,
        accelerationValues: List<Float>
    ): DetectionResult {
        var elevatedCount = 0
        var maxElevation = 0.0f
        var startIndex = -1
        var endIndex = -1
        var risingPhaseDetected = false
        var descendingPhaseDetected = false

        for (i in 1 until data.size - 1) {
            val prevValue = data[i-1]
            val currentValue = data[i]
            val nextValue = if (i < data.size - 1) data[i+1] else currentValue

            val elevation = currentValue - baseline

            // Detect rising edge
            if (!risingPhaseDetected && currentValue > prevValue &&
                elevation > elevationThreshold * 0.5f && startIndex == -1) {
                startIndex = i
                risingPhaseDetected = true
            }

            // Count sustained elevation
            if (startIndex != -1 && elevation > elevationThreshold * 0.7f) {
                elevatedCount++
                maxElevation = maxOf(maxElevation, elevation)
            }

            // Detect descending edge after sustained elevation
            if (risingPhaseDetected && elevatedCount >= 5 && currentValue < nextValue &&
                elevation > elevationThreshold * 0.5f && !descendingPhaseDetected) {
                endIndex = i
                descendingPhaseDetected = true
            }

            // Check if pattern is complete
            if (descendingPhaseDetected && abs(currentValue - baseline) < elevationThreshold * 0.3f) {
                // Extended bumps have longer elevated phase
                val totalDuration = i - startIndex

                if (elevatedCount >= 5 && totalDuration in 10..30) {
                    val elevationQuality = maxElevation / elevationThreshold
                    val durationQuality = if (elevatedCount in 5..15) 1.0f else 0.8f
                    val confidence = (elevationQuality * 0.6f + durationQuality * 0.4f) * 0.9f

                    return DetectionResult(
                        detected = true,
                        confidence = confidence,
                        detectionIndex = startIndex,
                        patternType = "ExtendedBump",
                        inputYAccValues = accelerationValues
                    )
                }

                // Reset if the pattern doesn't match extended bump criteria
                elevatedCount = 0
                maxElevation = 0.0f
                startIndex = -1
                endIndex = -1
                risingPhaseDetected = false
                descendingPhaseDetected = false
            }
        }

        return DetectionResult(detected = false, confidence = 0.0f, inputYAccValues = accelerationValues)
    }

    // Detect double speed bumps (two bumps close together)
    private fun detectDoubleBump(
        data: List<Float>,
        baseline: Float,
        elevationThreshold: Float,
        accelerationValues: List<Float>
    ): DetectionResult {
        var bumpCount = 0
        var firstBumpIndex = -1
        var lastBumpIndex = -1
        var currentBumpStartIndex = -1
        var inBump = false
        var maxElevation = 0.0f
        var totalElevation = 0.0f

        for (i in 1 until data.size - 1) {
            val prevValue = data[i-1]
            val currentValue = data[i]
            val nextValue = if (i < data.size - 1) data[i+1] else currentValue

            val elevation = currentValue - baseline

            // Start of a bump
            if (!inBump && elevation > elevationThreshold * 0.6f && currentValue > prevValue) {
                inBump = true
                currentBumpStartIndex = i
            }

            // Track maximum elevation during the bump
            if (inBump) {
                maxElevation = maxOf(maxElevation, elevation)
            }

            // End of a bump
            if (inBump && elevation < elevationThreshold * 0.3f && i - currentBumpStartIndex >= 3) {
                inBump = false
                bumpCount++
                totalElevation += maxElevation

                if (bumpCount == 1) {
                    firstBumpIndex = currentBumpStartIndex
                }
                lastBumpIndex = i
                maxElevation = 0.0f

                // Check for double bump pattern
                if (bumpCount == 2 && lastBumpIndex - firstBumpIndex <= 30) {
                    val avgElevation = totalElevation / 2
                    val separationQuality = if (lastBumpIndex - firstBumpIndex in 10..25) 1.0f else 0.7f
                    val elevationQuality = avgElevation / elevationThreshold
                    val confidence = (elevationQuality * 0.6f + separationQuality * 0.4f) * 0.92f

                    return DetectionResult(
                        detected = true,
                        confidence = confidence,
                        detectionIndex = firstBumpIndex,
                        patternType = "DoubleBump",
                        inputYAccValues = accelerationValues
                    )
                }
            }

            // Reset if we're in a bump for too long
            if (inBump && i - currentBumpStartIndex > 15) {
                inBump = false
                maxElevation = 0.0f
            }

            // Reset if too much time between bumps
            if (bumpCount == 1 && !inBump && i - lastBumpIndex > 30) {
                bumpCount = 0
                totalElevation = 0.0f
                firstBumpIndex = -1
            }
        }

        return DetectionResult(detected = false, confidence = 0.0f, inputYAccValues = accelerationValues)
    }

    // Calculate symmetry of the bump pattern (good speed bumps are roughly symmetric)
    private fun calculateSymmetry(
        data: List<Float>,
        riseStart: Int,
        elevationStart: Int,
        elevationEnd: Int,
        descentEnd: Int
    ): Float {
        // Check for valid indices
        if (riseStart >= elevationStart || elevationStart >= elevationEnd || elevationEnd >= descentEnd) {
            return 0.5f // Default symmetry if indices are invalid
        }

        val risePhaseLength = elevationStart - riseStart
        val descentPhaseLength = descentEnd - elevationEnd

        // Perfect symmetry would have equal rise and descent phases
        val lengthRatio = minOf(risePhaseLength.toFloat() / descentPhaseLength,
            descentPhaseLength.toFloat() / risePhaseLength)

        // Calculate average rate of change during rise and descent
        var riseRate = 0.0f
        for (i in riseStart until elevationStart) {
            if (i > riseStart) {
                riseRate += abs(data[i] - data[i-1])
            }
        }
        riseRate /= maxOf(1, risePhaseLength - 1)

        var descentRate = 0.0f
        for (i in elevationEnd until descentEnd) {
            if (i > elevationEnd) {
                descentRate += abs(data[i] - data[i-1])
            }
        }
        descentRate /= maxOf(1, descentPhaseLength - 1)

        // Rate ratio (how similar are the speeds of rise and descent)
        val rateRatio = minOf(riseRate / maxOf(0.0001f, descentRate),
            descentRate / maxOf(0.0001f, riseRate))

        // Combine length and rate symmetry (0.0 to 1.0)
        return (lengthRatio * 0.7f + rateRatio * 0.3f)
    }

    private var tempVideoFile: File? = null

    fun getTempVideoFile(context: Context): File {
        if (tempVideoFile == null || !tempVideoFile!!.exists()) {
            tempVideoFile = File.createTempFile("temp_video", ".mp4", context.cacheDir)
        }
        return tempVideoFile!!
    }

}