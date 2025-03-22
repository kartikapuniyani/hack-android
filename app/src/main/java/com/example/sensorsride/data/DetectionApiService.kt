package com.example.sensorsride.data

import com.example.sensorsride.data.models.SensorDetectionResult
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.QueryMap
import retrofit2.http.Url

interface DetectionApiService {

    // PUT request with optional body
    @POST
    suspend fun updateSensorDetectedData(
        @Url url: String,
        @Body body: SensorDetectionRequest? = null,
    ): Response<SensorDetectionResult>



    @Multipart
    @PUT
    suspend fun updateVisionDetectedData(
        @Url url : String,
        @Part file: MultipartBody.Part
    ): Response<Unit>
}