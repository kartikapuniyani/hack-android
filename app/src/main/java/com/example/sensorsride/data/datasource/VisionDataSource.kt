package com.example.sensorsride.data.datasource

import android.util.Log
import com.example.sensorsride.data.DetectionApiService
import com.example.sensorsride.domain.VisionRepository
import com.example.sensorsride.utils.ApiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class VisionDataSource @Inject constructor(
    val apiService: DetectionApiService
) : VisionRepository{

    override fun sendAnomalyDetectedVideoStream(file: File, signedUrl : String): Flow<ApiResult<Unit>> {
       return flow {
           emit(ApiResult.Loading)
           Log.d("SensorRide","sendAnomalyDetectedStream :${signedUrl}")
           val requestFile = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())

           val multiPartBody = MultipartBody.Part.createFormData("fileName", file.name, requestFile)

           val response =  apiService.updateVisionDetectedData(signedUrl, multiPartBody)
           if (response.isSuccessful) {
               emit(ApiResult.Success(Unit))
           }else{
               emit(ApiResult.Error(response.message().toString(), response.code()))
           }


       }
    }

}