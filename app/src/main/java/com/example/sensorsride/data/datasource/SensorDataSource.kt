package com.example.sensorsride.data.datasource

import android.util.Log
import com.example.sensorsride.data.DetectionApiService
import com.example.sensorsride.data.SensorDetectionRequest
import com.example.sensorsride.data.models.SensorDetectionResult
import com.example.sensorsride.domain.SensorRepository
import com.example.sensorsride.utils.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

private const val PUT_SENSOR_DETECTION_DATA_URL = "api/potholes/detect"
class SensorDataSource @Inject constructor(
    val apiService: DetectionApiService
) : SensorRepository {
    override suspend fun updateSensorDetectedData(sensorDetectionRequest: SensorDetectionRequest): Flow<ApiResult<SensorDetectionResult>> {

        return flow {
            Log.d("SensorRide","updateSensor called :${sensorDetectionRequest}")
            emit(ApiResult.Loading)
            val response = apiService.updateSensorDetectedData(
                PUT_SENSOR_DETECTION_DATA_URL,
                sensorDetectionRequest
            )


            Log.d("SensorRide","updateSensor called :${response.isSuccessful} ${response.message()}")

            if (response.isSuccessful && response.body()!=null){
                emit(
                    ApiResult.Success(
                        SensorDetectionResult(
                            message = response.body()?.message.orEmpty(),
                            signedUrl = response.body()?.signedUrl.orEmpty(),
                            confirmed = response.body()?.confirmed ?: false,
                            pothole = response.body()?.pothole ?: false
                        )
                    )
                )
            }else{
                emit(ApiResult.Error(response.message(), response.code()))
            }
        }

    }
}