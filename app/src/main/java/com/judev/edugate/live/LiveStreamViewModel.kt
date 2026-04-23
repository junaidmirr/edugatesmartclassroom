package com.judev.edugate.live

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration

class LiveStreamViewModel : ViewModel() {
    private val rtdb = FirebaseDatabase.getInstance("https://fipperai-default-rtdb.firebaseio.com/").reference
    private var agoraEngine: RtcEngine? = null
    
    // Using your updated App ID
    private val appId = "cb5caa174dbf4dadb9bc52396bac4a90"

    val isMicOn = mutableStateOf(false)
    val isCamOn = mutableStateOf(false)
    val isScreenSharing = mutableStateOf(false)
    val remoteUid = mutableStateOf<Int?>(null)
    val isLive = mutableStateOf(false)
    val streamType = mutableStateOf("") // "camera" or "screen"
    val errorMessage = mutableStateOf("")

    private var currentClassId: String? = null
    private var currentRole: String? = null

    fun initStream(context: Context, classId: String, role: String) {
        if (agoraEngine != null) return
        
        currentClassId = classId
        currentRole = role
        errorMessage.value = ""
        try {
            val config = RtcEngineConfig()
            config.mContext = context.applicationContext
            config.mAppId = appId
            config.mEventHandler = object : IRtcEngineEventHandler() {
                override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                    Log.d("AgoraLive", "Join success: $uid")
                }
                override fun onUserJoined(uid: Int, elapsed: Int) {
                    Log.d("AgoraLive", "Remote user joined: $uid")
                    remoteUid.value = uid
                }
                override fun onUserOffline(uid: Int, reason: Int) {
                    Log.d("AgoraLive", "Remote user offline: $uid")
                    if (remoteUid.value == uid) remoteUid.value = null
                }
                override fun onError(err: Int) {
                    Log.e("AgoraLive", "Error code: $err")
                    errorMessage.value = "Agora Error: $err"
                }
            }
            agoraEngine = RtcEngine.create(config)
            
            agoraEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
            
            // Core media settings
            agoraEngine?.enableVideo()
            agoraEngine?.enableAudio()
            
            agoraEngine?.setVideoEncoderConfiguration(VideoEncoderConfiguration(
                VideoEncoderConfiguration.VD_640x480,
                VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                VideoEncoderConfiguration.STANDARD_BITRATE,
                VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
            ))

            rtdb.child("live_streams").child(classId).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val live = snapshot.child("isLive").getValue(Boolean::class.java) ?: false
                    val type = snapshot.child("type").getValue(String::class.java) ?: ""
                    
                    isLive.value = live
                    streamType.value = type
                    
                    if (live) {
                        joinChannel(classId)
                    } else {
                        leaveChannel()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    errorMessage.value = "Database Error: ${error.message}"
                }
            })

        } catch (e: Exception) {
            Log.e("AgoraLive", "Init failed: ${e.message}")
            errorMessage.value = "Init failed: ${e.message}"
        }
    }

    private fun joinChannel(classId: String) {
        val options = ChannelMediaOptions()
        if (currentRole == "teacher") {
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            options.autoSubscribeAudio = true
            options.autoSubscribeVideo = true
            
            // Set initial tracks based on current type
            if (streamType.value == "screen") {
                options.publishCameraTrack = false
                options.publishScreenCaptureVideo = true
                options.publishScreenCaptureAudio = true
            } else {
                options.publishCameraTrack = true
                options.publishScreenCaptureVideo = false
                options.publishScreenCaptureAudio = false
            }
            options.publishMicrophoneTrack = true
        } else {
            options.clientRoleType = Constants.CLIENT_ROLE_AUDIENCE
            options.autoSubscribeAudio = true
            options.autoSubscribeVideo = true
            options.publishCameraTrack = false
            options.publishScreenCaptureVideo = false
            options.publishMicrophoneTrack = false
        }
        
        // Pass 0 for uid to let Agora generate one
        agoraEngine?.joinChannel(null, classId, 0, options)
    }

    private fun leaveChannel() {
        agoraEngine?.leaveChannel()
        remoteUid.value = null
    }

    fun startCameraStream(classId: String) {
        isCamOn.value = true
        isMicOn.value = true
        isScreenSharing.value = false
        streamType.value = "camera"
        
        // 1. Stop screen capture if it was running
        agoraEngine?.stopScreenCapture()
        
        // 2. Enable camera and start preview
        agoraEngine?.enableLocalVideo(true)
        agoraEngine?.startPreview()
        
        // 3. Update media options to publish CAMERA and hide SCREEN
        val options = ChannelMediaOptions()
        options.publishCameraTrack = true
        options.publishScreenCaptureVideo = false
        options.publishScreenCaptureAudio = false
        options.publishMicrophoneTrack = true
        agoraEngine?.updateChannelMediaOptions(options)
        
        val update = mapOf("isLive" to true, "type" to "camera")
        rtdb.child("live_streams").child(classId).setValue(update)
            .addOnFailureListener { errorMessage.value = "Failed to start stream: ${it.message}" }
    }

    fun startScreenStream(classId: String) {
        isScreenSharing.value = true
        isMicOn.value = true
        isCamOn.value = false
        streamType.value = "screen"
        
        // 1. Stop camera preview
        agoraEngine?.stopPreview()
        
        // 2. Start screen capture
        val params = ScreenCaptureParameters()
        params.captureAudio = true
        params.captureVideo = true
        agoraEngine?.startScreenCapture(params)
        
        // 3. Update media options to publish SCREEN and hide CAMERA
        val options = ChannelMediaOptions()
        options.publishCameraTrack = false
        options.publishScreenCaptureVideo = true
        options.publishScreenCaptureAudio = true
        options.publishMicrophoneTrack = true
        agoraEngine?.updateChannelMediaOptions(options)
        
        val update = mapOf("isLive" to true, "type" to "screen")
        rtdb.child("live_streams").child(classId).setValue(update)
            .addOnFailureListener { errorMessage.value = "Failed to start screen share: ${it.message}" }
    }

    fun stopStream(classId: String) {
        if (isScreenSharing.value) agoraEngine?.stopScreenCapture()
        agoraEngine?.stopPreview()
        agoraEngine?.enableLocalVideo(false)
        
        isLive.value = false
        isCamOn.value = false
        isMicOn.value = false
        isScreenSharing.value = false
        
        rtdb.child("live_streams").child(classId).removeValue()
        leaveChannel()
    }

    fun toggleMic() {
        isMicOn.value = !isMicOn.value
        val options = ChannelMediaOptions()
        options.publishMicrophoneTrack = isMicOn.value
        agoraEngine?.updateChannelMediaOptions(options)
    }

    fun toggleCamera() {
        if (streamType.value != "camera") return
        isCamOn.value = !isCamOn.value
        
        if (isCamOn.value) {
            agoraEngine?.startPreview()
        } else {
            agoraEngine?.stopPreview()
        }
        
        val options = ChannelMediaOptions()
        options.publishCameraTrack = isCamOn.value
        agoraEngine?.updateChannelMediaOptions(options)
    }

    fun switchCamera() {
        agoraEngine?.switchCamera()
    }

    fun setupLocalVideo(container: android.view.SurfaceView) {
        val videoCanvas = VideoCanvas(container, VideoCanvas.RENDER_MODE_FIT, 0)
        agoraEngine?.setupLocalVideo(videoCanvas)
        if (isCamOn.value) {
            agoraEngine?.startPreview()
        }
    }

    fun setupRemoteVideo(container: android.view.SurfaceView, uid: Int) {
        val videoCanvas = VideoCanvas(container, VideoCanvas.RENDER_MODE_FIT, uid)
        agoraEngine?.setupRemoteVideo(videoCanvas)
    }

    fun cleanup() {
        agoraEngine?.leaveChannel()
        RtcEngine.destroy()
        agoraEngine = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
