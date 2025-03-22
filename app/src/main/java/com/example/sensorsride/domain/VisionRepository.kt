package com.example.sensorsride.domain

import com.example.sensorsride.utils.ApiResult
import kotlinx.coroutines.flow.Flow
import java.io.File

interface VisionRepository {

    fun sendAnomalyDetectedVideoStream(file: File, signedUrl : String): Flow<ApiResult<Unit>>
}