package com.example.sensorsride.domain

import com.example.sensorsride.data.SensorDetectionRequest
import com.example.sensorsride.data.models.SensorDetectionResult
import com.example.sensorsride.utils.ApiResult
import kotlinx.coroutines.flow.Flow

interface SensorRepository {

    suspend fun updateSensorDetectedData(sensorDetectionRequest: SensorDetectionRequest): Flow<ApiResult<SensorDetectionResult>>
}