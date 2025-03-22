package com.example.sensorsride.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.video.AudioConfig
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.sensorsride.R
import com.example.sensorsride.domain.SpeedBumpAnalyzer.getTempVideoFile
import com.example.sensorsride.ui.theme.Typography
import com.example.sensorsride.utils.PermissionUtil


const val LANDSCAPE: Int = 0
const val PORTRAIT: Int = 1
const val ANY: Int = 2

@Composable
fun VideoRecorderScreen(
    videoRecorderViewModel: VideoViewModel,
    onResponse: (actionType: ActionType) -> Unit,
) {
    BackHandler {
        onResponse.invoke(ActionType.DISCARD)
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentIndex by videoRecorderViewModel.currentIndex.collectAsState()
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecordingPaused by remember { mutableStateOf(false) }
    var isRecordingStarted by remember { mutableStateOf(false) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var isRecordingStopped by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp
    val currentOrientation by videoRecorderViewModel.currentOrientation.collectAsState()

    val shouldStartRecording by videoRecorderViewModel.dataRecorderState.collectAsState()


    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.VIDEO_CAPTURE)
            cameraSelector = CameraSelector.Builder()
                .requireLensFacing(videoRecorderViewModel.lensFacing)
                .build()
        }
    }

    LaunchedEffect(shouldStartRecording) {
        if (shouldStartRecording == 1){
            recordVideo(
                controller = controller,
                context = context,
                recording = recording,
                isRecordingPaused = isRecordingPaused,
                onRecordingChanged = { rec, paused ->
                    recording = rec
                    isRecordingStarted = true
                    isTimerRunning = true
                },
                videoRecorderViewModel = videoRecorderViewModel, // Pass ViewModel
            )
        }
    }

    /** Launcher to open gallery **/
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)
        }?.use { input ->
            videoRecorderViewModel.isVideoFromGallery = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (isRecordingPaused) {
                        recording?.resume()
                        isRecordingPaused = false
                        isRecordingStarted = true
                    }
                    controller.unbind()
                    controller.bindToLifecycle(lifecycleOwner)
                }

                Lifecycle.Event.ON_PAUSE -> {
                    if (recording != null && isRecordingStarted) {
                        recording?.stop()
                        isRecordingPaused = true
                        isTimerRunning = false
                    }
                    controller.unbind()
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            controller.unbind()
        }
    }


    val isMergingRequired = videoRecorderViewModel.isMergingRequired
    Box(
        modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {}
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black), contentAlignment = Alignment.Center) {
                CameraPreview(
                    cameraController = controller,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.75f)
                )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(screenHeight / 5)
                .align(Alignment.BottomCenter)
                .background(Color.Black),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (recording == null && videoRecorderViewModel.isRotateEnabled) {
                IconButton(
                    onClick = {
                        controller.cameraSelector =
                            if (controller.cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else CameraSelector.DEFAULT_BACK_CAMERA
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.switch_camera),
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            val rotationAngle = if (currentOrientation == LANDSCAPE) 90f else 0f

            val bottomPadding =
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) 50.dp else 20.dp

            Column(
                modifier = Modifier
                    .padding(bottom = bottomPadding)
                    .graphicsLayer { rotationZ = rotationAngle },
                verticalArrangement = Arrangement.SpaceAround,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isRecordingStopped) {
                    Timer(
                        currentIndex = currentIndex,
                        totalSeconds =  5000L,
                        isTimerRunning = isTimerRunning,
                        onTimerFinished = {
                            if (isMergingRequired) {
                                if (true) {
                                    stopRecording(recording) { rec, paused ->
                                        recording = rec
                                        isRecordingStarted = false
                                        isTimerRunning = false
                                        isRecordingStopped = true
                                    }
                                } else {
                                    recording?.pause()
                                    isRecordingPaused = true
                                    isRecordingStarted = false
                                    isTimerRunning = false
                                    videoRecorderViewModel.currentIndex.value =
                                        currentIndex + 1
                                }
                            } else {
                                stopRecording(recording, onRecordingChanged = { rec, paused ->
                                    recording = rec
                                    isRecordingStarted = false
                                    isTimerRunning = false
                                })
                                videoRecorderViewModel.compressAndSaveVideo()
                            }
                        }
                    )
                }
                if (recording == null) {
                    val icon = if(isRecordingStarted) R.drawable.video_capture_button else R.drawable.ic_video_recording_default
                    Image(
                        modifier = Modifier
                            .size(64.dp)
                            .clickable {
                                if (recording != null) {
                                    // Stop recording manually
                                    stopRecording(recording) { rec, paused ->
                                        recording = rec
                                        isRecordingStarted = false
                                        isTimerRunning = false
                                        isRecordingStopped =
                                            true // Set to true when manually stopped
                                    }
                                    val tempFile = getTempVideoFile(context)
                                    videoRecorderViewModel.setVideoUri(tempFile.path)
                                } else if (true) {
                                    recordVideo(
                                        controller = controller,
                                        context = context,
                                        recording = recording,
                                        isRecordingPaused = isRecordingPaused,
                                        onRecordingChanged = { rec, paused ->
                                            recording = rec
                                            isRecordingStarted = true
                                            isTimerRunning = true
                                        },
                                        videoRecorderViewModel = videoRecorderViewModel, // Pass ViewModel
                                    )
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.all_videos_recorded),
                                        Toast.LENGTH_SHORT
                                    )
                                }
                            },
                        painter = painterResource(id = icon),
                        contentDescription = stringResource(
                            R.string.camera
                        )
                    )
                }
                if (recording != null) {
                }
            }
            if (videoRecorderViewModel.isGalleryEnabled && !isRecordingStarted) {
                Image(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            if (PermissionUtil.hasRequiredMediaPermissions(context)) {
                                launcher.launch("video/*")
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.permission_required_for_gallery),
                                    Toast.LENGTH_SHORT
                                )
                            }
                        },
                    painter = painterResource(id = R.drawable.ic_gallery),
                    contentDescription = stringResource(R.string.pick_video_from_gallery),
                )

            }
        }
    }
}

private fun recordVideo(
    controller: LifecycleCameraController,
    context: Context,
    recording: Recording?,
    isRecordingPaused: Boolean,
    onRecordingChanged: (Recording?, Boolean) -> Unit,
    videoRecorderViewModel: VideoViewModel
) {
    if (recording != null) {
        recording.stop()
        onRecordingChanged(null, false)
        return
    }
    if (ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        //ToastUtils.showToast(context, context.getString(R.string.audio_permission_required))
        return
    }
    val file = videoRecorderViewModel.updateOutputFile(context)
    val newRecording: Recording = controller.startRecording(
        FileOutputOptions.Builder(file).build(),
        AudioConfig.create(false),
        ContextCompat.getMainExecutor(context),
    ) { event ->
        when (event) {
            is VideoRecordEvent.Finalize -> {
                if (event.hasError()) {
                    Log.d("video-recording",event.cause?.message.toString())
                    onRecordingChanged(null, false)
                    Toast.makeText(context,context.getString(R.string.video_capture_failed), Toast.LENGTH_SHORT)
                } else {
                }
            }
        }
    }
    onRecordingChanged(newRecording, false)
}

private fun stopRecording(
    recording: Recording?,
    onRecordingChanged: (Recording?, Boolean) -> Unit
) {
    recording?.stop()
    onRecordingChanged(null, false)
}

@Composable
fun Timer(
    modifier: Modifier = Modifier,
    totalSeconds: Long,
    isTimerRunning: Boolean,
    currentIndex: Int,
    onTimerFinished: () -> Unit
) {
    var secondsRemaining by remember(currentIndex) { mutableStateOf(totalSeconds / 1000) }
    var countDownTimer: CountDownTimer? by remember { mutableStateOf(null) }
    var secondsElapsed by remember(currentIndex) { mutableStateOf(0L) }
    LaunchedEffect(currentIndex, isTimerRunning) {
        countDownTimer?.cancel()

        if (isTimerRunning) {
            countDownTimer = object : CountDownTimer(secondsRemaining * 1000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    secondsRemaining = millisUntilFinished / 1000
                    secondsElapsed++
                }

                override fun onFinish() {
                    secondsElapsed = totalSeconds / 1000
                    secondsRemaining = 0
                    onTimerFinished()
                }
            }.start()
        }
    }

    Box(
        modifier = Modifier
            .then(modifier)
            .padding(all = 10.dp)
    ) {
        Text(
            text = "${DateUtils.formatElapsedTime(secondsElapsed)} / ${
                DateUtils.formatElapsedTime(
                    totalSeconds / 1000
                )
            }",
            color = Color.White,
            style = Typography.labelLarge.copy(fontSize = 10.sp)
        )
    }
}

enum class ActionType {
    SAVE,
    DISCARD
}
