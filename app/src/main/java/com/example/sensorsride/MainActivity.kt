package com.example.sensorsride

import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.sensorsride.data.DetectionEvent
import com.example.sensorsride.data.DetectionResult
import com.example.sensorsride.domain.PotholeAnalyzer
import com.example.sensorsride.domain.SpeedBumpAnalyzer
import com.example.sensorsride.domain.getCurrentTime
import com.example.sensorsride.presentation.DetectionEventItem
import com.example.sensorsride.presentation.VideoRecorderScreen
import com.example.sensorsride.presentation.VideoViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.Queue
import kotlin.math.abs

const val SPEED_BUMP = "SPEED BUMP"
const val POTHOLE = "POTHOLE"

@AndroidEntryPoint
class MainActivity : ComponentActivity(), SensorEventListener {

    // Detection thresholds for accelerometer
    // Detection thresholds for gyroscope

     val GYRO_THRESHOLD_PITCH : Float = 1.2f // Y-axis (pitch) threshold for both anomalies

     val GYRO_THRESHOLD_ROLL_POTHOLE: Float = 1.5f // X-axis (roll) threshold for potholes

    val GYRO_THRESHOLD_ROLL_BUMP: Float = 0.8f  // X-axis (roll) threshold for bumps (lower than pothole)

    // Window size and cooldown period



    // Sensor data variables
    private var baselineAcceleration = SensorManager.GRAVITY_EARTH // Baseline for "normal" road

    // Sensor variables
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    // Data windows for sensor readings
    val WINDOW_SIZE: Int = 50 // Number of samples in sliding window

    val COOLDOWN_PERIOD: Long = 1000 // Milliseconds between detections
    private val zAccelerationWindow: Queue<Float> = ArrayDeque(WINDOW_SIZE)
    private val verticalAccelerationTimeWindow : Queue<Long> = ArrayDeque(WINDOW_SIZE)
    private val gyroXWindow: Queue<Float> = ArrayDeque(WINDOW_SIZE)
    private val gyroYWindow: Queue<Float> = ArrayDeque(WINDOW_SIZE)

    // Timestamps for sensor readings and detections
    private var lastAccelTimestamp: Long = 0
    private var lastGyroTimestamp: Long = 0
    private var lastDetectionTime: Long = 0

    private val lastAccelValues = FloatArray(3)
    private val lastGyroValues = FloatArray(3)
    private var isAccelDataFresh = false
    private var isGyroDataFresh = false

    private var fileWriter: FileWriter? = null
    private val fileName = "accelerometer_data_${System.currentTimeMillis()}.txt" // Use the same file for appending

    private val detections = mutableStateListOf<DetectionEvent>()

    private val viewModel by viewModels<VideoViewModel>()

    private val anomalyDetectedWindow : MutableList<DetectionResult> = mutableListOf()

    val accelerationData = mutableListOf<Pair<Long, Float>>() // (timestamp, acceleration)


    private val requiredPermissions = mutableListOf(
        android.Manifest.permission.CAMERA,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) // Needed for Android 9 (API 28) and below
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            Toast.makeText(this, "Permissions granted! You may proceed ahead", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions are required to record video", Toast.LENGTH_SHORT).show()
        }
    }


    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
       // setContentView(R.layout.activity_pothole_detection)

        checkAndRequestPermissions()
        setContent {
            Column(modifier = Modifier.fillMaxSize()) {
                // Detection Screen (Top Half)
//                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
//                    VideoRecorderScreen(
//                        viewModel
//                    ) { }
//                }

                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    DetectionScreen()
                }

            }

        }

        // Initialize sensors
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager!!.getDefaultSensor(Sensor.TYPE_GYROSCOPE)


        // Check sensor availability
        var sensorsAvailable = true

        if (accelerometer == null) {
            Log.e(TAG, "No accelerometer found. Device not compatible.")
            sensorsAvailable = false
        }

        if (gyroscope == null) {
            Log.e(TAG, "No gyroscope found. Falling back to accelerometer-only mode.")
            // We can still operate without gyroscope, just less accurately
        }

        if (!sensorsAvailable) {
            Log.e(TAG, "Essential sensors missing. Exiting.")
            finish()
        } else {
            Log.i(TAG, "Sensors found. Starting road anomaly detection with optimized thresholds.")
        }
    }

    private fun checkAndRequestPermissions() {
        val deniedPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPermissions.isNotEmpty()) {
            permissionLauncher.launch(deniedPermissions.toTypedArray())
        } else {
            Toast.makeText(this,"All Permissions Granted. You are good to go!",Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        super.onStop()
       closeFileWriter()
    }

    @Composable
    fun DetectionScreen() {


        val detectionsState by rememberUpdatedState(detections)
        val isRunning = remember { mutableStateOf(false) }

        val shouldShowResult = remember { mutableStateOf(false) }

        // UI
        Column(modifier = Modifier.fillMaxSize()) {

            if (shouldShowResult.value){
                AccelerationChart(accelerationData)

            }
            // List of detections
            LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
                val modified  = detections.filterNot { it.title == SPEED_BUMP && it.confidence > 2.0f }
                items(modified.size) { i ->
                    DetectionEventItem(
                        type = detections[i].title,
                        timestamp = detections[i].timestamp,
                        patternType = detections[i].patternType,
                        confidence = detections[i].confidence
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Buttons for Start/Stop
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = {
                        if (isRunning.value) {
                            isRunning.value = false
                            shouldShowResult.value = true
                            stopSensorCapturing()
                        } else {
                            isRunning.value = true
                            startSensorCapturing()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = if (isRunning.value) "Stop" else "Start")
                }

                Spacer(modifier = Modifier.padding(top = 24.dp))
                if (isRunning.value.not()){
                    Button(
                        onClick = {
                            resetUI()
                            shouldShowResult.value = false
                            accelerationData.clear()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "Reset")
                    }
                }

            }
        }
    }


    @Composable
    fun AccelerationChart(accelerationData: List<Pair<Long, Float>>) {
        AndroidView(
            factory = { context ->
                LineChart(context).apply {
                    this.description.isEnabled = false  // Hide description label
                    this.axisRight.isEnabled = false // Disable right Y-axis
                    this.legend.isEnabled = false // Hide legend
                    this.xAxis.position = XAxis.XAxisPosition.BOTTOM
                    this.xAxis.granularity = 1f
                    this.xAxis.setDrawLabels(true)
                }
            },
            update = { chart ->
                if (accelerationData.isEmpty()) return@AndroidView

                val entries = accelerationData.map { (timestamp, acceleration) ->
                    Entry(timestamp.toFloat(), acceleration)
                }

                val dataSet = LineDataSet(entries, "Vertical Acceleration Pothole Detection").apply {
                    color = Color.BLUE
                    valueTextColor = Color.BLACK
                    lineWidth = 2f
                    setDrawValues(false) // Hide data point values
                    setDrawCircles(false) // Remove dots from the line
                    setDrawFilled(true) // Optional: fills the area below the line
                    fillColor = Color.BLUE
                    fillAlpha = 50
                    mode = LineDataSet.Mode.CUBIC_BEZIER // Enables smooth curve for wave effect
                }

                chart.data = LineData(dataSet)

                // Configure X-Axis Labels
                val xAxis = chart.xAxis
                val firstTimestamp = accelerationData.first().first
                val lastTimestamp = accelerationData.last().first

                xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(value.toLong()))
                        return when (value.toLong()) {
                            firstTimestamp -> "Start\n$time"
                            lastTimestamp -> "End\n$time"
                            else -> time
                        }
                    }
                }

                chart.invalidate() // Refresh the chart
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).fillMaxHeight(0.8f)
        )
    }




    private fun resetUI() {
       detections.clear()
    }

    protected override fun onResume() {
        super.onResume()
    }

    protected override fun onPause() {
        super.onPause()
        stopSensorCapturing()
    }

    override fun onSensorChanged(event: SensorEvent) {

        val currentTime = System.currentTimeMillis()

        if (currentTime - lastDetectionTime >= COOLDOWN_PERIOD) {
            if (isAccelDataFresh) {
                processSensorData()
                isAccelDataFresh = false
                isGyroDataFresh = false
            }
            zAccelerationWindow.clear()
            verticalAccelerationTimeWindow.clear()
            gyroXWindow.clear()
            lastDetectionTime = currentTime
        }

        // Handle accelerometer data
        if (event.sensor.type === Sensor.TYPE_ACCELEROMETER) {
            // Store accelerometer values (X, Y, Z axes)
            System.arraycopy(event.values, 0, lastAccelValues, 0, 3)
            lastAccelTimestamp = event.timestamp
            isAccelDataFresh = true


            val verticalAcceleration = lastAccelValues[1]

            zAccelerationWindow.add(verticalAcceleration) // Add newest value
            verticalAccelerationTimeWindow.add(event.timestamp)

            recordAccelerationData(event.timestamp, verticalAcceleration)



        } else if (event.sensor.type === Sensor.TYPE_GYROSCOPE) {
            // Store gyroscope values (X, Y, Z axes)
            System.arraycopy(event.values, 0, lastGyroValues, 0, 3)
            lastGyroTimestamp = event.timestamp
            isGyroDataFresh = true



            // Add roll (X-axis) to window
            if (gyroXWindow.size >= WINDOW_SIZE) {
                gyroXWindow.remove() // Remove oldest value if window is full
            }
            gyroXWindow.add(lastGyroValues[2]) // X-axis rotation (roll)


            // Add pitch (Y-axis) to window
            if (gyroYWindow.size >= WINDOW_SIZE) {
                gyroYWindow.remove() // Remove oldest value if window is full
            }
            gyroYWindow.add(lastGyroValues[1]) // Y-axis rotation (pitch)


            // Verbose logging for debugging
            Log.v(
                TAG,
                ("Gyro X roll: " + lastGyroValues[0]).toString() + ", Gyro Y (Pitch): " + lastGyroValues[1]  +", Gyro Z (yaw): " + lastGyroValues[2]
            )
        }
    }

    private fun processSensorData() {
        val currentTime = System.currentTimeMillis()
        // Only process if we have a full window and cooldown period has passed


        if ((currentTime - lastDetectionTime >= COOLDOWN_PERIOD)) {
            // Analyze accelerometer data for vertical acceleration patterns
            val accelAnalysis = analyzeAccelerometerData()
            accelAnalysis?.second?.let {
                anomalyDetectedWindow.add(it)
            }

            // Analyze gyroscope data if available
            var gyroAnalysis: GyroscopeAnalysis? = null
            if (gyroscope != null && gyroXWindow.size == WINDOW_SIZE && gyroYWindow.size == WINDOW_SIZE) {
                gyroAnalysis = analyzeGyroscopeData()
            }

            // Fuse sensor data and detect anomalies
            // detectRoadAnomalies(accelAnalysis, gyroAnalysis, currentTime)
        }
    }

    private fun initializeFileWriter() {
        // Get the public directory
        val publicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)

        // Create a new file or use the existing one
        val file = File(publicDirectory, fileName)

        try {
            // Open FileWriter in append mode by passing 'true' as the second parameter
            fileWriter = FileWriter(file, true) // Append mode
        } catch (e: IOException) {
            Log.e(TAG, "Error initializing file writer", e)
        }
    }

    private fun analyzeAccelerometerData(): Pair<String,DetectionResult?>? {
        // Calculate statistics for accelerometer window data
        var sum = 0f
        var min = Float.MAX_VALUE
        var max = Float.MIN_VALUE


        // Find min, max, and sum for average calculation
        for (value in zAccelerationWindow) {
            sum += value
            if (value < min) min = value
            if (value > max) max = value
        }

        val avg = sum / zAccelerationWindow.size
        val range = max - min // Total range of acceleration in window
        val verticalDelta =
            abs((avg - baselineAcceleration).toDouble()).toFloat() // Deviation from baseline

        val potentialPothole = PotholeAnalyzer.detectPothole(zAccelerationWindow.toList(), baselineAcceleration)


        val potentialBump = SpeedBumpAnalyzer.detectSpeedBump(
            zAccelerationWindow.toList(), baselineAcceleration)

        if (potentialBump.detected){
            detections.add(
                DetectionEvent(
                    title = SPEED_BUMP,
                    timestamp = getCurrentTime(),
                    confidence = potentialBump.confidence,
                    patternType = potentialBump.patternType,
                    detected = true
                )
            )
        }

        if (potentialPothole.detected){
            detections.add(
                DetectionEvent(title = POTHOLE,
                    timestamp = getCurrentTime(),
                    confidence = potentialPothole.confidence,
                    patternType = potentialPothole.patternType,
                    detected = true
                )
            )
        }

        Log.d(TAG,"analyzeAcceleroData : ${zAccelerationWindow} isBump:  ${potentialBump}  isPothole:  ${potentialPothole}")

//        val timestamp = System.currentTimeMillis()
//        val jsonObject = JSONObject()
//        jsonObject.put("timestamp", timestamp)
//        jsonObject.put("values", zAccelerationWindow)
//        jsonObject.put("gyroy",gyroYWindow)
//        jsonObject.put("gyrox",gyroXWindow)
//        jsonObject.put("potholeDetection",potentialPothole)
//        jsonObject.put("bumpDetection",potentialBump)
//
//
//
//        val jsonString = jsonObject.toString()
//
//        try {
//            // Check if fileWriter is initialized
//            fileWriter?.apply {
//                write(jsonString + "\n") // Append the data with a newline
//                flush() // Ensure data is written
//                Log.d(TAG, "Data saved successfully to the file.")
//            }
//        } catch (e: IOException) {
//            Log.e(TAG, "Error writing data to file", e)
//        }


        val selectedDetection = when {
            potentialPothole.detected && potentialPothole.confidence > 1.5f -> Pair(first = POTHOLE, second = potentialPothole)
            potentialBump.detected && potentialBump.confidence > 1.5f ->  Pair(first = SPEED_BUMP, second = potentialPothole)
            else ->
                Pair(first = POTHOLE, DetectionResult(detected = true,
                confidence = 1.3f,
                detectionType = POTHOLE,
                detectionIndex = 12,
                inputYAccValues =  zAccelerationWindow.toMutableList(),
                inputGyroPitchValue = gyroXWindow.toMutableList()
            )
                )
        }

        return selectedDetection
    }

    private fun analyzeGyroscopeData(): GyroscopeAnalysis {
        // Calculate statistics for gyroscope window data
        var sumX = 0f
        var sumY = 0f
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE


        // Process X-axis (roll) data
        for (value in gyroXWindow) {
            sumX += abs(value).toFloat() //Use absolute values for rotational velocity
            if (value < minX) minX = value
            if (abs(value.toDouble()) > maxX) maxX = abs(value.toDouble()).toFloat()
        }


        // Process Y-axis (pitch) data
        for (value in gyroYWindow) {
            sumY += abs(value).toFloat()
            if (value < minY) minY = value
            if (abs(value.toDouble()) > maxY) maxY = abs(value.toDouble()).toFloat()
        }

        val avgX = sumX / gyroXWindow.size
        val avgY = sumY / gyroYWindow.size


        // Determine significant rotational patterns
        // For potholes: Look for both pitch and strong roll (vehicle tilting to one side)
        val significantPotholeRotation = (maxY > GYRO_THRESHOLD_PITCH) &&
                (maxX > GYRO_THRESHOLD_ROLL_POTHOLE)


        // For bumps: Strong pitch is most important, but with a lower roll threshold
        val significantBumpRotation = (maxY > GYRO_THRESHOLD_PITCH) &&
                (maxX > GYRO_THRESHOLD_ROLL_BUMP)

        return GyroscopeAnalysis(
            avgX, avgY, maxX, maxY, minX, minY, significantPotholeRotation, significantBumpRotation
        )
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Not needed for this implementation
    }

    private fun closeFileWriter() {
        try {
            fileWriter?.close()
            Log.d(TAG, "File writer closed successfully.")
        } catch (e: IOException) {
            Log.e(TAG, "Error closing file writer", e)
        }
    }

    companion object {
        private const val TAG = "SensorRideActivity"
    }


    private fun startSensorCapturing(){
        // Register sensor listeners with SENSOR_DELAY_GAME for faster updates (50Hz sampling rate)
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        if (gyroscope != null) {
            sensorManager?.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(TAG, "Accelerometer and gyroscope listeners registered");
        } else {
            Log.d(TAG, "Only accelerometer listener registered");
        }
        initializeFileWriter()

        viewModel.startRecordingPeriodically()
        startCountdown()
    }

    private fun stopSensorCapturing(){
        sensorManager?.unregisterListener(this)
        Log.d(TAG, "Sensor listeners unregistered")
        closeFileWriter()
        viewModel.stopRecordingPeriodically()
    }

    private fun startCountdown() {
        // 5 seconds countdown, tick every 1 second
        object : CountDownTimer(5000, 1000) {

            override fun onTick(millisUntilFinished: Long) {

            }

            override fun onFinish() {
                Log.d(TAG,"onFinish called ${anomalyDetectedWindow.map { it.confidence }}")
                viewModel.fetchLastUpdatedLocation(1, anomalyDetectedWindow)
                viewModel.sendCapturedSensorData(anomalyDetectedWindow )
                anomalyDetectedWindow.clear()
                stopSensorCapturing()
                startSensorCapturing()
            }
        }.start()

        viewModel.fetchLastUpdatedLocation(0, null)
    }

    fun recordAccelerationData(timestamp: Long, acceleration: Float) {
        accelerationData.add(Pair(timestamp, acceleration))
    }
}

/**
 *  private fun detectRoadAnomalies(
 *         accel: AccelerometerAnalysis,
 *         gyro: GyroscopeAnalysis?,
 *         currentTime: Long
 *     ) {
 *         // Default to medium confidence with accelerometer only
 *         var confidence = ConfidenceLevel.MEDIUM
 *         var anomalyType: String? = null
 *
 *
 *         // Check for pothole
 *         if (accel.potentialPothole) {
 *             anomalyType = "POTHOLE"
 *
 *
 *             // If gyro data available, use it to confirm or adjust confidence
 *             if (gyro != null) {
 *                 if (gyro.significantPotholeRotation) {
 *                     // Both sensors indicate pothole with strong roll component - high confidence
 *                     confidence = ConfidenceLevel.HIGH
 *                     Log.d(TAG, "Pothole confirmed by strong pitch + roll signature")
 *                 } else if (gyro.maxY > GYRO_THRESHOLD_PITCH) {
 *                     // At least significant pitch was detected - medium confidence
 *                     confidence = ConfidenceLevel.MEDIUM
 *                     Log.d(TAG, "Pothole partially confirmed by pitch movement only")
 *                 } else {
 *                     // Gyro doesn't confirm at all - lower confidence
 *                     confidence = ConfidenceLevel.LOW
 *                     Log.d(TAG, "Pothole not confirmed by gyroscope data")
 *                 }
 *             }
 *         } else if (accel.potentialBump) {
 *             anomalyType = "BUMP"
 *
 *
 *             // If gyro data available, use it to confirm or adjust confidence
 *             if (gyro != null) {
 *                 if (gyro.significantBumpRotation) {
 *                     // Both sensors indicate bump - high confidence
 *                     // Note: We use a lower roll threshold for bumps
 *                     confidence = ConfidenceLevel.HIGH
 *                     Log.d(TAG, "Bump confirmed by pitch + mild roll signature")
 *                 } else if (gyro.maxY > GYRO_THRESHOLD_PITCH) {
 *                     // Only pitch movement detected - still acceptable for bumps
 *                     confidence = ConfidenceLevel.MEDIUM
 *                     Log.d(TAG, "Bump confirmed by pitch movement only")
 *                 } else {
 *                     // Gyro doesn't confirm - lower confidence
 *                     confidence = ConfidenceLevel.LOW
 *                     Log.d(TAG, "Bump not confirmed by gyroscope data")
 *                 }
 *             }
 *         } else if (accel.range < 2.0f) {
 *             // Gradually adjust baseline to account for sensor drift and changing road surfaces
 *            // baselineAcceleration = baselineAcceleration * 0.95f + accel.avg * 0.05f
 *           //  Log.v(TAG, "Stable road condition - updated baseline: $baselineAcceleration")
 *         }
 *
 *
 *         // Log detection if anomaly detected with sufficient confidence
 *         if (anomalyType != null) {
 *             // Only log events with medium or high confidence to reduce false positives
 *             if (confidence !== ConfidenceLevel.LOW) {
 *                 logAnomalyDetected(anomalyType, currentTime, accel, gyro, confidence)
 *                 lastDetectionTime = currentTime // Reset cooldown timer
 *             } else {
 *                 Log.d(
 *                     TAG,
 *                     "Possible $anomalyType detected but confidence too low. Ignoring."
 *                 )
 *             }
 *         }
 *     }
 *
 *     private fun logAnomalyDetected(
 *         type: String, timestamp: Long, accel: AccelerometerAnalysis,
 *         gyro: GyroscopeAnalysis?, confidence: ConfidenceLevel
 *     ) {
 *         Log.i(TAG, " logAnomalyDetected ====================")
 *         Log.i(
 *             TAG,
 *             " logAnomalyDetected DETECTED: $type (Confidence: $confidence)"
 *         )
 *         Log.i(TAG, String.format("Time: %d", timestamp))
 *         Log.i(
 *             TAG, String.format(
 *                 "logAnomalyDetected Accel - Avg: %.2f, Min: %.2f, Max: %.2f, Range: %.2f",
 *                 accel.avg, accel.min, accel.max, accel.range
 *             ).plus("is pothole : ${accel.potentialPothole} ,isbump : ${accel.potentialBump}")
 *         )
 *
 *         if (gyro != null) {
 *             Log.i(
 *                 TAG, String.format(
 *                     " logAnomalyDetected Gyro - AvgX: %.2f, AvgY: %.2f, MaxX: %.2f, MaxY: %.2f",
 *                     gyro.avgX, gyro.avgY, gyro.maxX, gyro.maxY,
 *                 ).plus("is pothole : ${gyro.significantPotholeRotation} ,isbump : ${ gyro.significantBumpRotation}")
 *             )
 *         }
 *
 *         Log.i(TAG, "====================")
 *     }
 *
 */

/**
 *     // Confidence levels for detections
 *     private enum class ConfidenceLevel {
 *         LOW, MEDIUM, HIGH
 *     }
 *
 *     val POTHOLE_ACCEL_THRESHOLD: Float = 10.2f // Vertical acceleration threshold for potholes
 *
 *     val BUMP_ACCEL_THRESHOLD: Float = 15.0f // Vertical acceleration threshold for bumps
 *
 *     val POTHOLE_MIN_THRESHOLD: Float = 2.0f          // Minimum drop required
 *     val POTHOLE_RECOVERY_THRESHOLD: Float = 3.0f      // Minimum recovery after drop
 *     val POTHOLE_DURATION_MIN_MS: Long = 100           // Minimum duration of event
 *     val POTHOLE_DURATION_MAX_MS: Long = 800          // Maximum duration of event
 */