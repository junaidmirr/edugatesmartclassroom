package com.judev.edugate.live

import android.Manifest
import android.content.pm.PackageManager
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveStreamScreen(
    classId: String,
    role: String,
    type: String, // "camera" or "screen"
    onBack: () -> Unit,
    viewModel: LiveStreamViewModel = viewModel()
) {
    val context = LocalContext.current
    val isMicOn by viewModel.isMicOn
    val isCamOn by viewModel.isCamOn
    val remoteUid by viewModel.remoteUid
    val isLive by viewModel.isLive
    val errorMsg by viewModel.errorMessage

    // Mutable state to track which surfaces have been bound
    var localSurfaceReady by remember { mutableStateOf(false) }
    var remoteSurfaceReady by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            if (role == "teacher") {
                if (type == "camera") {
                    viewModel.startCameraStream(classId)
                } else {
                    viewModel.startScreenStream(classId)
                }
            }
        }
    }

    // Initialize stream and request permissions
    LaunchedEffect(classId) {
        viewModel.initStream(context, classId, role)

        if (role == "teacher") {
            val permissions = if (type == "camera") {
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO)
            }

            val allGranted = permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }

            if (allGranted) {
                if (type == "camera") viewModel.startCameraStream(classId)
                else viewModel.startScreenStream(classId)
            } else {
                permissionLauncher.launch(permissions)
            }
        }
    }

    // Cleanup on screen exit
    DisposableEffect(Unit) {
        onDispose {
            Log.d("LiveStream", "Screen disposed, cleaning up")
            if (role == "teacher") {
                viewModel.stopStream(classId)
            }
            viewModel.cleanup()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (type == "camera") "Live Camera" else "Live Screen Share",
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (role == "teacher") viewModel.stopStream(classId)
                        onBack()
                    }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (role == "teacher") {
                        IconButton(onClick = { viewModel.switchCamera() }) {
                            Icon(
                                Icons.Default.FlipCameraAndroid,
                                contentDescription = "Switch Camera",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { viewModel.toggleMic() }) {
                            Icon(
                                if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "Mic",
                                tint = if (isMicOn) Color.Green else Color.Red
                            )
                        }
                        if (type == "camera") {
                            IconButton(onClick = { viewModel.toggleCamera() }) {
                                Icon(
                                    if (isCamOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    contentDescription = "Camera",
                                    tint = if (isCamOn) Color.Green else Color.Red
                                )
                            }
                        }
                        Button(
                            onClick = {
                                viewModel.stopStream(classId)
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("End Live", color = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // ERROR DISPLAY
            if (errorMsg.isNotEmpty()) {
                Surface(
                    color = Color(0xCC000000),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                ) {
                    Text(
                        errorMsg,
                        color = Color.Red,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp
                    )
                }
            }

            // TEACHER VIEW
            if (role == "teacher") {
                if (type == "camera") {
                    // FIXED: Create SurfaceView only once and reuse it
                    LocalSurfaceView(
                        modifier = Modifier.fillMaxSize(),
                        onSurfaceCreated = { surface ->
                            // Setup video only when surface is ready
                            if (!localSurfaceReady) {
                                Log.d("LiveStream", "Local surface ready, setting up video")
                                viewModel.setupLocalVideo(surface)
                                localSurfaceReady = true
                            }
                        }
                    )
                } else {
                    // Screen share - show placeholder
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ScreenShare,
                                contentDescription = null,
                                tint = Color.Green,
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "You are sharing your screen",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            // STUDENT VIEW
            else {
                if (isLive) {
                    if (remoteUid != null) {
                        // FIXED: Create remote surface and setup only once
                        RemoteSurfaceView(
                            remoteUid = remoteUid!!,
                            modifier = Modifier.fillMaxSize(),
                            onSurfaceCreated = { surface ->
                                if (!remoteSurfaceReady) {
                                    Log.d("LiveStream", "Remote surface ready, setting up remote video")
                                    viewModel.setupRemoteVideo(surface, remoteUid!!)
                                    remoteSurfaceReady = true
                                }
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Waiting for teacher to start...", color = Color.Gray)
                    }
                }
            }
        }
    }
}

/**
 * FIXED: Custom composable that creates SurfaceView once and reuses it
 * Prevents recreation on every recomposition
 */
@Composable
fun LocalSurfaceView(
    modifier: Modifier = Modifier,
    onSurfaceCreated: (SurfaceView) -> Unit
) {
    var surfaceView: SurfaceView? by remember { mutableStateOf(null) }
    var callbackExecuted by remember { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                // Store reference
                surfaceView = this

                // Setup callback for when surface is ready
                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                        Log.d("LiveStream", "SurfaceView created")
                        if (!callbackExecuted) {
                            onSurfaceCreated(this@apply)
                            callbackExecuted = true
                        }
                    }

                    override fun surfaceChanged(
                        holder: android.view.SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        Log.d("LiveStream", "SurfaceView changed: $width x $height")
                    }

                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                        Log.d("LiveStream", "SurfaceView destroyed")
                    }
                })
            }
        },
        modifier = modifier
    )
}

/**
 * FIXED: Custom composable for remote video surface
 */
@Composable
fun RemoteSurfaceView(
    remoteUid: Int,
    modifier: Modifier = Modifier,
    onSurfaceCreated: (SurfaceView) -> Unit
) {
    var surfaceView: SurfaceView? by remember { mutableStateOf(null) }
    var callbackExecuted by remember { mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                surfaceView = this

                holder.addCallback(object : android.view.SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                        Log.d("LiveStream", "Remote SurfaceView created for uid: $remoteUid")
                        if (!callbackExecuted) {
                            onSurfaceCreated(this@apply)
                            callbackExecuted = true
                        }
                    }

                    override fun surfaceChanged(
                        holder: android.view.SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        Log.d("LiveStream", "Remote SurfaceView changed: $width x $height")
                    }

                    override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                        Log.d("LiveStream", "Remote SurfaceView destroyed")
                    }
                })
            }
        },
        modifier = modifier
    )
}