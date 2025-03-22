package com.example.sensorsride.presentation

import android.content.Context
import android.location.Location
import android.os.Environment
import android.util.Log
import android.view.Surface.ROTATION_0
import androidx.camera.core.CameraSelector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sensorsride.data.AccelerometerData
import com.example.sensorsride.data.DetectionResult
import com.example.sensorsride.data.GyroScopeData
import com.example.sensorsride.data.LocationData
import com.example.sensorsride.data.SensorDetectionRequest
import com.example.sensorsride.data.models.SensorDetectionResult
import com.example.sensorsride.domain.LocationRepository
import com.example.sensorsride.domain.SensorRepository
import com.example.sensorsride.domain.VisionRepository
import com.example.sensorsride.domain.generateFileName
import com.example.sensorsride.domain.getTimeStamp
import com.example.sensorsride.utils.ApiResult
import com.google.protobuf.Api
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Queue
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor(
   val  locationRepository: LocationRepository,
   val  sensorRepository: SensorRepository,
   val visionRepository: VisionRepository,
    val context: Context
): ViewModel() {

    val currentIndex = MutableStateFlow(0)
    lateinit var outputFile: File
    var appId: String? = null
    var isMergingRequired = false
    var videoFileName = ""
    private val _videoUri = MutableStateFlow<String?>(null)
    val videoUri: StateFlow<String?> get() = _videoUri
    var lensFacing = CameraSelector.LENS_FACING_BACK
    var isEditable = true
    var isGalleryEnabled = false
    var isRotateEnabled = false
    var isVideoFromGallery = false
    var appointmentId = ""
    var currentOrientation = MutableStateFlow(0)
    var rotation = ROTATION_0

    val dataRecorderState  = MutableStateFlow(-1)

    val shouldStartRecording = MutableStateFlow(false)

    private var lastUpdatedLocation: Pair<ApiResult<Location?>, ApiResult<Location?>> =
        Pair(ApiResult.None, ApiResult.None)

    private var sensorDetectionApiRes : MutableStateFlow<ApiResult<SensorDetectionResult>> = MutableStateFlow(ApiResult.None)

    private var lastSingUrlForFile : String? = ""

    companion object {
        const val VIDEO_FILE_PREFIX = "VIDEO_"
        const val VIDEO_FILE_SUFFIX = ".mp4"
    }

    fun setVideoUri(uri: String) {
        _videoUri.value = uri
    }

    fun updateOutputFile(context: Context): File {
        videoFileName = generateFileName(context)
        val externalStorageDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            appointmentId
        )
        if (!externalStorageDir.exists()) {
            externalStorageDir.mkdirs()
        }
        outputFile = File(externalStorageDir, videoFileName)
        return outputFile
    }

    fun startRecordingPeriodically(){
        dataRecorderState.tryEmit(1)
        viewModelScope.launch {
            while (dataRecorderState.value==1) {
                shouldStartRecording.tryEmit(true)
                delay(5000)  // Wait for 5 seconds before running the task again
                shouldStartRecording.tryEmit(false)
            }
        }

    }

    fun stopRecordingPeriodically(){
        dataRecorderState.tryEmit(0)
    }

    fun compressAndSaveVideo() {
        if (outputFile.exists() && lastSingUrlForFile?.isNotEmpty() == true){
            viewModelScope.launch {
                visionRepository.sendAnomalyDetectedVideoStream(
                    file = outputFile, signedUrl = lastSingUrlForFile.toString()
                ).collect{
                    it
                }
            }
        }
        Log.d("Video-VM","compress and save video called :${outputFile.absolutePath} ${outputFile.totalSpace}")
    }

    fun fetchLastUpdatedLocation(index:Int = 0, anomalyDetectedWindow: MutableList<DetectionResult>? ) {
        Log.d("TAG", "fetch latest called :$index ${anomalyDetectedWindow?.size}")
        viewModelScope.launch {
            locationRepository.getLastUpdatedLocation().collect {
                Log.d("TAG", "fetchLastUpdatedLocation: $it")
                if (index ==0){
                    lastUpdatedLocation.copy(first = ApiResult.Success(it))
                }else{
                    lastUpdatedLocation.copy(second =  ApiResult.Success(it))
                    sendCapturedSensorData(anomalyDetectedWindow?: mutableListOf())
                }
            }
        }
    }

    fun sendCapturedSensorData(anomalyDetectedWindow: MutableList<DetectionResult>) {
        val location = (lastUpdatedLocation.first as? ApiResult.Success)?.data
        if (anomalyDetectedWindow.size==0){
            return
        }
        viewModelScope.launch {
            sensorRepository.updateSensorDetectedData(
                SensorDetectionRequest(
                    anomalyType = anomalyDetectedWindow.last().patternType,
                    accelValues = anomalyDetectedWindow.flatMap { detectionResult ->
                        detectionResult.inputYAccValues.map { yAcc ->
                            AccelerometerData(
                                xAxis = 0.0,
                                yAxis = yAcc.toDouble(),
                                zAxis = 0.0,
                                timeStamp = System.currentTimeMillis()
                            )
                        }
                    },
                    gyroValues = anomalyDetectedWindow.flatMap { detectionResult ->
                        detectionResult.inputGyroPitchValue.map { gyroPitch ->
                            GyroScopeData(
                                xAxis = gyroPitch.toDouble(),
                                yAxis = 0.0,
                                zAxis = 0.0,
                                timeStamp = System.currentTimeMillis()
                            )
                        }
                    },
                    location = LocationData(
                        latitude = location?.latitude ?:0.0,
                        longitude = location?.longitude ?:0.0,
                        accuracy = location?.accuracy?.toDouble()?:0.0,
                        timeStamp = System.currentTimeMillis(),
                    ),
                    fileName = generateFileName(context)
                )
            ).collect{
                sensorDetectionApiRes.tryEmit(it)
                if(it is ApiResult.Success){
                    lastSingUrlForFile = it.data.signedUrl
                }
                Log.d("SensoRide","${lastSingUrlForFile}")
            }
        }
        Log.e("SensorRide","send Capture sensor data called ! ${anomalyDetectedWindow.map { it.confidence }}")
    }

}