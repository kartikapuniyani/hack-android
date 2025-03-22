package com.example.sensorsride.domain

import android.content.Context
import android.provider.Settings
import com.example.sensorsride.data.DataStats
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.sqrt

fun smoothData(data: List<Float>, windowSize: Int): List<Float> {
    val smoothed = mutableListOf<Float>()
    for (i in data.indices) {
        val halfWindow = windowSize / 2
        val start = maxOf(0, i - halfWindow)
        val end = minOf(data.size - 1, i + halfWindow)
        val window = data.subList(start, end + 1)

        // Apply Gaussian-like weighting to prioritize center values
        var weightedSum = 0f
        var weightSum = 0f
        for (j in window.indices) {
            val distanceFromCenter = abs(j - (window.size / 2))
            val weight = 1.0f / (1.0f + distanceFromCenter)
            weightedSum += window[j] * weight
            weightSum += weight
        }

        smoothed.add(weightedSum / weightSum)
    }
    return smoothed
}

 fun calculateStatistics(data: List<Float>): DataStats {
    val mean = data.average().toFloat()
    val variance = data.map { (it - mean) * (it - mean) }.average().toFloat()
    val stdDev = sqrt(variance.toDouble()).toFloat()
    val max = data.maxOrNull() ?: mean
    val min = data.minOrNull() ?: mean
    val range = max - min

    return DataStats(mean, stdDev, range)
}

fun getCurrentTime(): String {
    val currentTime = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"))
    val format = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
    format.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
    return format.format(currentTime.time)
}

private const val DATE_FORMAT = "yyyyMMdd_HHmmss"

fun getTimeStamp(): String = SimpleDateFormat(DATE_FORMAT, Locale.ENGLISH).format(Date())

fun kalmanSmoothData(data: List<Float>): List<Float> {
    val smoothed = mutableListOf<Float>()

    // Initial Kalman filter state
    var estimate = data[0] // Initial estimate is the first data point
    var errorCovariance = 1.0f // Initial error covariance
    val processNoise = 0.1f // Process noise, controls how much we trust the model
    val measurementNoise = 1.0f // Measurement noise, controls how much we trust the sensor data
    var kalmanGain: Float

    for (i in data.indices) {
        // Prediction Step: Predict the next state (estimate remains constant here)
        val predictedEstimate = estimate
        var predictedErrorCovariance = errorCovariance + processNoise

        // Update Step: Incorporate the new measurement (sensor data)
        kalmanGain = predictedErrorCovariance / (predictedErrorCovariance + measurementNoise)

        // Update the estimate using the Kalman Gain and the difference between the predicted and actual measurement
        estimate = predictedEstimate + kalmanGain * (data[i] - predictedEstimate)

        // Update the error covariance to reflect the new estimate's uncertainty
        errorCovariance = (1 - kalmanGain) * predictedErrorCovariance

        // Store the smoothed estimate
        smoothed.add(estimate)
    }

    return smoothed
}

fun generateFileName(context: Context, prefix: String = "video"): String {
    // Get device ID (ANDROID_ID)
    val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    // Get current timestamp
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

    // Construct the filename
    return "${prefix}_${deviceId}_$timestamp.mp4"
}





