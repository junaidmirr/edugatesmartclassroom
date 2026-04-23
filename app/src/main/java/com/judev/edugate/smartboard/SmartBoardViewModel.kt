package com.judev.edugate.smartboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.judev.edugate.models.EduFile
import io.agora.rtc2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.math.*

class SmartBoardViewModel : ViewModel() {

    private val rtdb = FirebaseDatabase.getInstance("https://fipperai-default-rtdb.firebaseio.com/").reference
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://fipperai.firebasestorage.app")

    // ── Agora State (Audio Only) ─────────────────────────────────────────────
    private var agoraEngine: RtcEngine? = null
    private val appId = "cb5caa174dbf4dadb9bc52396bac4a90"
    
    val isMicOn = mutableStateOf(false)

    // ── Multi-Slide Board state ──────────────────────────────────────────────
    private val slides = mutableStateListOf<List<BoardElement>>(emptyList())
    val currentSlideIndex = mutableStateOf(0)
    
    val elements     = mutableStateListOf<BoardElement>()
    val currentPoints = mutableStateListOf<Offset>()
    val isDrawing    = mutableStateOf(false)
    val redrawTrigger = mutableStateOf(0L)

    private val capturedFramePaths = mutableListOf<String>()
    val savedFramesCount = mutableStateOf(0)

    private val undoStack = ArrayDeque<List<BoardElement>>()
    private val redoStack = ArrayDeque<List<BoardElement>>()

    val livePoints      = mutableStateListOf<Offset>()
    val liveColor       = mutableStateOf(Color.Black)
    val liveStrokeWidth = mutableStateOf(10f)
    val liveIsEraser    = mutableStateOf(false)

    val currentTool      = mutableStateOf(Tool.PEN)
    val currentColor     = mutableStateOf(Color.Black)
    val currentFillColor = mutableStateOf(Color.Transparent)
    val currentStrokeWidth = mutableStateOf(8f)
    val currentOpacity   = mutableStateOf(1f)
    val currentFontSize  = mutableStateOf(40f)

    val selectedElement       = mutableStateOf<BoardElement?>(null)
    val selectedElementBounds = mutableStateOf(Rect.Zero)
    private val elementBoundsMap = mutableMapOf<Long, Rect>()

    private var dragStartOffset    = Offset.Zero
    private var initialElementOffset = Offset.Zero
    private var initialElementSize   = Size.Zero
    private var initialRotation      = 0f
    val activeHandle = mutableStateOf<Handle?>(null)

    val isLive        = mutableStateOf(false)
    private var classroomId = ""
    private var userRole    = ""

    private var lastSyncTime = 0L
    private val syncIntervalMs = 30L

    enum class Handle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, ROTATE }

    private fun setupAgora(context: Context) {
        if (agoraEngine != null) return
        try {
            val config = RtcEngineConfig()
            config.mContext = context.applicationContext
            config.mAppId = appId
            config.mEventHandler = object : IRtcEngineEventHandler() {
                override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                    Log.d("AgoraSmartBoard", "Audio channel joined: $uid")
                }
            }
            agoraEngine = RtcEngine.create(config)
            agoraEngine?.disableVideo()
            agoraEngine?.enableAudio()
            agoraEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
        } catch (e: Exception) {
            Log.e("Agora", "Init failed: ${e.message}")
        }
    }

    private fun joinAudioChannel() {
        val options = ChannelMediaOptions()
        if (userRole == "teacher") {
            options.clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
            options.publishMicrophoneTrack = true
            options.autoSubscribeAudio = true
        } else {
            options.clientRoleType = Constants.CLIENT_ROLE_AUDIENCE
            options.publishMicrophoneTrack = false
            options.autoSubscribeAudio = true
        }
        agoraEngine?.joinChannel(null, classroomId, 0, options)
        if (userRole == "teacher") {
            agoraEngine?.muteLocalAudioStream(!isMicOn.value)
        }
    }

    fun toggleMic() {
        isMicOn.value = !isMicOn.value
        if (userRole == "teacher") {
            val options = ChannelMediaOptions()
            options.publishMicrophoneTrack = isMicOn.value
            agoraEngine?.updateChannelMediaOptions(options)
            agoraEngine?.muteLocalAudioStream(!isMicOn.value)
        }
    }

    fun initClassroom(context: Context, classId: String, role: String) {
        classroomId = classId
        userRole    = role
        setupAgora(context)
        
        rtdb.child("whiteboards/$classId/isLive")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val live = s.getValue(Boolean::class.java) ?: false
                    isLive.value = live
                    if (live) {
                        joinAudioChannel()
                    } else {
                        agoraEngine?.leaveChannel()
                    }
                }
                override fun onCancelled(e: DatabaseError) {}
            })

        rtdb.child("whiteboards/$classId/currentSlideIndex")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val index = snapshot.getValue(Int::class.java) ?: 0
                    if (userRole == "student" && currentSlideIndex.value != index) {
                        currentSlideIndex.value = index
                        fetchSlideElements(index)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        if (role == "student") {
            listenForActiveStream()
            fetchSlideElements(0)
        } else if (role == "teacher") {
            // Initialize slides if empty
            if (slides.isEmpty()) {
                slides.add(emptyList())
            }
        }
    }

    private fun fetchSlideElements(index: Int) {
        rtdb.child("whiteboards/$classroomId/slides/$index/strokes")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    if (userRole == "student" && currentSlideIndex.value == index) {
                        elements.clear()
                        s.children.forEach { snap -> parseElement(snap)?.let { elements.add(it) } }
                        redrawTrigger.value++
                    }
                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    fun addNewSlide() {
        saveCurrentSlideToLocal()
        slides.add(emptyList())
        val newIndex = slides.size - 1
        currentSlideIndex.value = newIndex
        elements.clear()
        undoStack.clear()
        redoStack.clear()
        if (isLive.value) {
            rtdb.child("whiteboards/$classroomId/currentSlideIndex").setValue(newIndex)
        }
        redrawTrigger.value++
    }

    fun nextSlide() {
        if (currentSlideIndex.value < slides.size - 1) {
            saveCurrentSlideToLocal()
            currentSlideIndex.value++
            loadSlideFromLocal(currentSlideIndex.value)
            if (isLive.value) rtdb.child("whiteboards/$classroomId/currentSlideIndex").setValue(currentSlideIndex.value)
        }
    }

    fun prevSlide() {
        if (currentSlideIndex.value > 0) {
            saveCurrentSlideToLocal()
            currentSlideIndex.value--
            loadSlideFromLocal(currentSlideIndex.value)
            if (isLive.value) rtdb.child("whiteboards/$classroomId/currentSlideIndex").setValue(currentSlideIndex.value)
        }
    }

    private fun saveCurrentSlideToLocal() {
        if (slides.size <= currentSlideIndex.value) {
            slides.add(elements.toList())
        } else {
            slides[currentSlideIndex.value] = elements.toList()
        }
    }

    private fun loadSlideFromLocal(index: Int) {
        elements.clear()
        if (index < slides.size) {
            elements.addAll(slides[index])
        }
        undoStack.clear()
        redoStack.clear()
        redrawTrigger.value++
        if (userRole == "teacher" && isLive.value) {
            rtdb.child("whiteboards/$classroomId/slides/$index/strokes").removeValue()
            elements.forEach { syncElementToFirebase(it) }
        }
    }

    fun startLiveSession() {
        if (classroomId.isEmpty()) return
        rtdb.child("whiteboards/$classroomId/isLive").setValue(true)
        rtdb.child("whiteboards/$classroomId/currentSlideIndex").setValue(currentSlideIndex.value)
        loadSlideFromLocal(currentSlideIndex.value)
    }

    fun stopLiveSession() {
        if (classroomId.isEmpty()) return
        isLive.value = false
        rtdb.child("whiteboards/$classroomId/isLive").setValue(false)
        rtdb.child("whiteboards/$classroomId/activeStream").removeValue()
        agoraEngine?.leaveChannel()
    }

    fun captureAllSlides(context: Context, density: Density, onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            saveCurrentSlideToLocal()
            capturedFramePaths.clear()
            slides.forEachIndexed { index, slideElements ->
                val width = 1200f
                val height = 1680f
                val bitmap = Bitmap.createBitmap(width.toInt(), height.toInt(), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.parseColor("#F8F7F4"))
                val drawScope = CanvasDrawScope()
                val composeCanvas = androidx.compose.ui.graphics.Canvas(canvas)
                val size = Size(width, height)

                drawScope.draw(density, LayoutDirection.Ltr, composeCanvas, size) {
                    slideElements.forEach { element ->
                        drawElementOnCanvas(element, width, height)
                    }
                }

                val tempFile = File(context.cacheDir, "slide_${index}_${System.currentTimeMillis()}.png")
                FileOutputStream(tempFile).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 90, out) }
                bitmap.recycle()
                capturedFramePaths.add(tempFile.absolutePath)
            }
            withContext(Dispatchers.Main) {
                savedFramesCount.value = capturedFramePaths.size
                onComplete()
            }
        }
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawElementOnCanvas(element: BoardElement, width: Float, height: Float) {
        when (element) {
            is BoardElement.PathElement -> {
                val pts = element.points
                if (pts.size >= 2) {
                    val path = Path().apply {
                        moveTo(pts[0].x * width, pts[0].y * height)
                        for (i in 1 until pts.size) {
                            if (i < pts.size - 1) {
                                val mx = (pts[i].x + pts[i+1].x) / 2f * width
                                val my = (pts[i].y + pts[i+1].y) / 2f * height
                                quadraticTo(pts[i].x * width, pts[i].y * height, mx, my)
                            } else { lineTo(pts[i].x * width, pts[i].y * height) }
                        }
                    }
                    drawPath(path, element.color.copy(alpha = element.opacity),
                        style = Stroke(element.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        blendMode = if (element.isEraser) BlendMode.Clear else BlendMode.SrcOver)
                }
            }
            is BoardElement.ShapeElement -> {
                val pos  = Offset(element.offset.x * width, element.offset.y * height)
                val s    = Size(element.size.width * width, element.size.height * height)
                withTransform({ rotate(element.rotation, pivot = pos + Offset(s.width/2, s.height/2)) }) {
                    when (element.type) {
                        ShapeType.RECTANGLE -> {
                            if (element.fillColor != Color.Transparent) drawRect(element.fillColor.copy(alpha = element.opacity), pos, s)
                            drawRect(element.color.copy(alpha = element.opacity), pos, s, style = Stroke(element.strokeWidth))
                        }
                        ShapeType.CIRCLE -> {
                            val c = pos + Offset(s.width/2, s.height/2); val r = minOf(s.width, s.height) / 2
                            if (element.fillColor != Color.Transparent) drawCircle(element.fillColor.copy(alpha = element.opacity), r, c)
                            drawCircle(element.color.copy(alpha = element.opacity), r, c, style = Stroke(element.strokeWidth))
                        }
                        ShapeType.LINE -> drawLine(element.color.copy(alpha = element.opacity), pos, pos + Offset(s.width, s.height), element.strokeWidth)
                        ShapeType.TRIANGLE -> {
                            val tp = Path().apply { moveTo(pos.x + s.width/2, pos.y); lineTo(pos.x + s.width, pos.y + s.height); lineTo(pos.x, pos.y + s.height); close() }
                            if (element.fillColor != Color.Transparent) drawPath(tp, element.fillColor.copy(alpha = element.opacity))
                            drawPath(tp, element.color.copy(alpha = element.opacity), style = Stroke(element.strokeWidth))
                        }
                        ShapeType.ARROW -> {
                            val sx = pos.x; val sy = pos.y + s.height/2; val ex = pos.x + s.width; val ey = sy
                            drawLine(element.color.copy(alpha = element.opacity), Offset(sx,sy), Offset(ex,ey), element.strokeWidth)
                            val as_ = element.strokeWidth * 4; val ap = Path().apply { moveTo(ex, ey); lineTo(ex-as_, ey-as_); lineTo(ex-as_, ey+as_); close() }
                            drawPath(ap, element.color.copy(alpha = element.opacity))
                        }
                    }
                }
            }
            else -> {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        agoraEngine?.leaveChannel()
        RtcEngine.destroy()
        agoraEngine = null
    }

    // ── Whiteboard Logic (Firebase Sync) ─────────────────────────────────────

    private fun listenForActiveStream() {
        rtdb.child("whiteboards/$classroomId/activeStream")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(s: DataSnapshot) {
                    val colorHex = s.child("color").getValue(String::class.java) ?: "#000000"
                    val width    = s.child("width").getValue(Float::class.java) ?: 10f
                    val eraser   = s.child("isEraser").getValue(Boolean::class.java) ?: false
                    liveColor.value       = parseColor(colorHex)
                    liveStrokeWidth.value = width
                    liveIsEraser.value    = eraser
                    val pts = s.child("points").children.mapNotNull { pt ->
                        val x = pt.child("x").getValue(Float::class.java) ?: return@mapNotNull null
                        val y = pt.child("y").getValue(Float::class.java) ?: return@mapNotNull null
                        Offset(x, y)
                    }
                    livePoints.clear()
                    livePoints.addAll(pts)
                    redrawTrigger.value++
                }
                override fun onCancelled(e: DatabaseError) {}
            })
    }

    private fun parseColor(hex: String): Color = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Black }

    private fun parseElement(snap: DataSnapshot): BoardElement? {
        return when (snap.child("elementType").getValue(String::class.java) ?: "path") {
            "path" -> {
                val colorHex = snap.child("color").getValue(String::class.java) ?: "#000000"
                val width    = snap.child("width").getValue(Float::class.java) ?: 5f
                val eraser   = snap.child("isEraser").getValue(Boolean::class.java) ?: false
                val opacity  = snap.child("opacity").getValue(Float::class.java) ?: 1f
                val pts = snap.child("points").children.mapNotNull {
                    val x = it.child("x").getValue(Float::class.java) ?: return@mapNotNull null
                    val y = it.child("y").getValue(Float::class.java) ?: return@mapNotNull null
                    Offset(x, y)
                }
                if (pts.size < 2) return null
                BoardElement.PathElement(Path(), parseColor(colorHex), width, eraser, pts, opacity, snap.key?.toLongOrNull() ?: System.nanoTime())
            }
            "text" -> {
                val text = snap.child("text").getValue(String::class.java) ?: return null
                val x = snap.child("x").getValue(Float::class.java) ?: 0f
                val y = snap.child("y").getValue(Float::class.java) ?: 0f
                val fs = snap.child("fontSize").getValue(Float::class.java) ?: 40f
                val color = parseColor(snap.child("color").getValue(String::class.java) ?: "#000000")
                val rot = snap.child("rotation").getValue(Float::class.java) ?: 0f
                val opacity = snap.child("opacity").getValue(Float::class.java) ?: 1f
                val bold = snap.child("bold").getValue(Boolean::class.java) ?: false
                BoardElement.TextElement(snap.key?.toLongOrNull() ?: System.nanoTime(), text, Offset(x, y), fs, color, rot, opacity, bold)
            }
            "shape" -> {
                val type = ShapeType.valueOf(snap.child("shapeType").getValue(String::class.java) ?: "RECTANGLE")
                val x = snap.child("x").getValue(Float::class.java) ?: 0f
                val y = snap.child("y").getValue(Float::class.java) ?: 0f
                val w = snap.child("width").getValue(Float::class.java) ?: 0.3f
                val h = snap.child("height").getValue(Float::class.java) ?: 0.2f
                val color = parseColor(snap.child("color").getValue(String::class.java) ?: "#000000")
                val fill = parseColor(snap.child("fillColor").getValue(String::class.java) ?: "#00000000")
                val stroke = snap.child("strokeWidth").getValue(Float::class.java) ?: 5f
                val rot = snap.child("rotation").getValue(Float::class.java) ?: 0f
                val opacity = snap.child("opacity").getValue(Float::class.java) ?: 1f
                BoardElement.ShapeElement(snap.key?.toLongOrNull() ?: System.nanoTime(), type, Offset(x,y), Size(w,h), color, fill, stroke, rot, opacity)
            }
            "emoji" -> {
                val emoji = snap.child("emoji").getValue(String::class.java) ?: return null
                val x = snap.child("x").getValue(Float::class.java) ?: 0f
                val y = snap.child("y").getValue(Float::class.java) ?: 0f
                val size = snap.child("size").getValue(Float::class.java) ?: 40f
                val rot = snap.child("rotation").getValue(Float::class.java) ?: 0f
                BoardElement.EmojiElement(snap.key?.toLongOrNull() ?: System.nanoTime(), emoji, Offset(x,y), size, rot)
            }
            else -> null
        }
    }

    fun onPointerDown(offset: Offset) {
        dragStartOffset = offset
        when (currentTool.value) {
            Tool.PEN, Tool.MARKER, Tool.ERASER -> {
                pushUndoSnapshot()
                currentPoints.clear()
                currentPoints.add(offset)
                isDrawing.value = true
                if (isLive.value) sendLiveUpdate()
                redrawTrigger.value++
            }
            Tool.SELECT -> {
                val handle = checkHandleHit(offset)
                if (handle != null) { activeHandle.value = handle; prepareTransform() }
                else { selectElementAt(offset); if (selectedElement.value != null) prepareTransform() }
            }
            else -> {}
        }
    }

    fun onPointerMove(offset: Offset) {
        when (currentTool.value) {
            Tool.PEN, Tool.MARKER, Tool.ERASER -> {
                currentPoints.add(offset)
                if (isLive.value) {
                    val now = System.currentTimeMillis()
                    if (now - lastSyncTime >= syncIntervalMs) { lastSyncTime = now; sendLiveUpdate() }
                }
                redrawTrigger.value++
            }
            Tool.SELECT -> {
                if (activeHandle.value != null) transformSelectedElement(offset)
                else if (selectedElement.value != null) moveSelectedElement(offset)
            }
            else -> {}
        }
    }

    fun onPointerUp() {
        if (isDrawing.value) {
            val pts = ArrayList(currentPoints)
            if (pts.size >= 2) {
                val isEraser = currentTool.value == Tool.ERASER
                val isMarker = currentTool.value == Tool.MARKER
                val color = if (isEraser) Color.White else currentColor.value
                val sw = if (isEraser) currentStrokeWidth.value * 5 else if (isMarker) currentStrokeWidth.value * 2 else currentStrokeWidth.value
                val opacity = if (isMarker) currentOpacity.value * 0.6f else currentOpacity.value
                val newElement = BoardElement.PathElement(path = Path(), color = color, strokeWidth = sw, isEraser = isEraser, points = pts, opacity = opacity)
                elements.add(newElement)
                redoStack.clear()
                if (isLive.value) { syncElementToFirebase(newElement); rtdb.child("whiteboards/$classroomId/activeStream").removeValue() }
            }
            currentPoints.clear()
            isDrawing.value = false
            redrawTrigger.value++
        } else if (selectedElement.value != null && isLive.value) { selectedElement.value?.let { syncElementToFirebase(it) } }
        activeHandle.value = null
    }

    private fun sendLiveUpdate() {
        if (classroomId.isEmpty()) return
        val isEraser = currentTool.value == Tool.ERASER
        val isMarker = currentTool.value == Tool.MARKER
        val color = if (isEraser) Color.Transparent else currentColor.value
        val sw = if (isEraser) currentStrokeWidth.value * 5 else if (isMarker) currentStrokeWidth.value * 2 else currentStrokeWidth.value
        val data = mapOf("points" to currentPoints.map { mapOf("x" to it.x, "y" to it.y) }, "color" to colorToHex(color), "width" to sw, "isEraser" to isEraser)
        rtdb.child("whiteboards/$classroomId/activeStream").setValue(data)
    }

    private fun syncElementToFirebase(element: BoardElement) {
        if (classroomId.isEmpty()) return
        val slideIndex = currentSlideIndex.value
        val id = when (element) { is BoardElement.PathElement -> System.currentTimeMillis().toString(); is BoardElement.ShapeElement -> element.id.toString(); is BoardElement.TextElement -> element.id.toString(); is BoardElement.EmojiElement -> element.id.toString() }
        val data: Map<String, Any?> = when (element) {
            is BoardElement.PathElement -> mapOf("elementType" to "path", "points" to element.points.map { mapOf("x" to it.x, "y" to it.y) }, "color" to colorToHex(element.color), "width" to element.strokeWidth, "isEraser" to element.isEraser, "opacity" to element.opacity)
            is BoardElement.ShapeElement -> mapOf("elementType" to "shape", "shapeType" to element.type.name, "x" to element.offset.x, "y" to element.offset.y, "width" to element.size.width, "height" to element.size.height, "color" to colorToHex(element.color), "fillColor" to colorToHex8(element.fillColor), "strokeWidth" to element.strokeWidth, "rotation" to element.rotation, "opacity" to element.opacity)
            is BoardElement.TextElement -> mapOf("elementType" to "text", "text" to element.text, "x" to element.offset.x, "y" to element.offset.y, "fontSize" to element.fontSize, "color" to colorToHex(element.color), "rotation" to element.rotation, "opacity" to element.opacity, "bold" to element.bold)
            is BoardElement.EmojiElement -> mapOf("elementType" to "emoji", "emoji" to element.emoji, "x" to element.offset.x, "y" to element.offset.y, "size" to element.size, "rotation" to element.rotation)
        }
        rtdb.child("whiteboards/$classroomId/slides/$slideIndex/strokes/$id").setValue(data)
    }

    private fun colorToHex(c: Color) = String.format("#%06X", 0xFFFFFF and c.toArgb())
    private fun colorToHex8(c: Color) = String.format("#%08X", c.toArgb())

    fun updateElementBounds(element: BoardElement, rect: Rect) {
        val id = when (element) { is BoardElement.ShapeElement -> element.id; is BoardElement.TextElement -> element.id; is BoardElement.EmojiElement -> element.id; else -> return }
        elementBoundsMap[id] = rect
        if (element == selectedElement.value) selectedElementBounds.value = rect
    }

    fun selectElementAt(offset: Offset) {
        selectedElement.value = elements.reversed().find { el -> val id = idOf(el) ?: return@find false; val b = elementBoundsMap[id] ?: heuristicBounds(el); b.inflate(0.04f).contains(offset) }
        selectedElementBounds.value = selectedElement.value?.let { idOf(it)?.let { id -> elementBoundsMap[id] } } ?: Rect.Zero
    }

    private fun idOf(el: BoardElement): Long? = when (el) { is BoardElement.ShapeElement -> el.id; is BoardElement.TextElement -> el.id; is BoardElement.EmojiElement -> el.id; else -> null }
    private fun heuristicBounds(el: BoardElement): Rect = when (el) { is BoardElement.ShapeElement -> Rect(el.offset, el.size); is BoardElement.TextElement -> Rect(el.offset, Size(el.text.length * 0.018f, 0.06f)); is BoardElement.EmojiElement -> Rect(el.offset, Size(0.10f, 0.10f)); else -> Rect.Zero }

    private fun checkHandleHit(offset: Offset): Handle? {
        val el = selectedElement.value ?: return null
        val b = if (el is BoardElement.ShapeElement) Rect(el.offset, el.size) else selectedElementBounds.value
        if (b == Rect.Zero) return null
        val hs = 0.045f
        fun Offset.hitRect() = Rect(x - hs/2, y - hs/2, x + hs/2, y + hs/2)
        if (b.topLeft.hitRect().contains(offset)) return Handle.TOP_LEFT
        if (b.topRight.hitRect().contains(offset)) return Handle.TOP_RIGHT
        if (b.bottomLeft.hitRect().contains(offset)) return Handle.BOTTOM_LEFT
        if (b.bottomRight.hitRect().contains(offset)) return Handle.BOTTOM_RIGHT
        val rh = Offset(b.center.x, b.top - 0.07f)
        if (rh.hitRect().contains(offset)) return Handle.ROTATE
        return null
    }

    private fun prepareTransform() { val el = selectedElement.value ?: return; when (el) { is BoardElement.ShapeElement -> { initialElementOffset = el.offset; initialElementSize = el.size; initialRotation = el.rotation }; is BoardElement.TextElement -> { initialElementOffset = el.offset; initialRotation = el.rotation }; is BoardElement.EmojiElement -> { initialElementOffset = el.offset; initialRotation = el.rotation }; else -> {} } }

    private fun transformSelectedElement(offset: Offset) {
        val el = selectedElement.value ?: return; val handle = activeHandle.value ?: return; val index = elements.indexOf(el); if (index == -1) return; val delta = offset - dragStartOffset
        if (handle == Handle.ROTATE) { val b = if (el is BoardElement.ShapeElement) Rect(el.offset, el.size) else selectedElementBounds.value; val centre = b.center; val currentAngle = atan2((offset.y - centre.y).toDouble(), (offset.x - centre.x).toDouble()); val startAngle = atan2((dragStartOffset.y - centre.y).toDouble(), (dragStartOffset.x - centre.x).toDouble()); val rotDelta = ((currentAngle - startAngle) * (180.0 / PI)).toFloat(); val updated: BoardElement = when (el) { is BoardElement.ShapeElement -> el.copy(rotation = initialRotation + rotDelta); is BoardElement.TextElement -> el.copy(rotation = initialRotation + rotDelta); is BoardElement.EmojiElement -> el.copy(rotation = initialRotation + rotDelta); else -> el }; elements[index] = updated; selectedElement.value = updated; return }
        if (el is BoardElement.ShapeElement) { val minSize = 0.04f; var newOffset = initialElementOffset; var newSize = initialElementSize; when (handle) { Handle.BOTTOM_RIGHT -> newSize = Size(max(minSize, initialElementSize.width + delta.x), max(minSize, initialElementSize.height + delta.y)); Handle.BOTTOM_LEFT -> { val newW = max(minSize, initialElementSize.width - delta.x); newSize = Size(newW, max(minSize, initialElementSize.height + delta.y)); newOffset = Offset(initialElementOffset.x + (initialElementSize.width - newW), initialElementOffset.y) }; Handle.TOP_RIGHT -> { val newH = max(minSize, initialElementSize.height - delta.y); newSize = Size(max(minSize, initialElementSize.width + delta.x), newH); newOffset = Offset(initialElementOffset.x, initialElementOffset.y + (initialElementSize.height - newH)) }; Handle.TOP_LEFT -> { val newW = max(minSize, initialElementSize.width - delta.x); val newH = max(minSize, initialElementSize.height - delta.y); newSize = Size(newW, newH); newOffset = Offset(initialElementOffset.x + (initialElementSize.width - newW), initialElementOffset.y + (initialElementSize.height - newH)) }; else -> {} }; val updated = el.copy(size = newSize, offset = newOffset); elements[index] = updated; selectedElement.value = updated }
        if (el is BoardElement.TextElement) { val scaleFactor = 1f + delta.x * 2f; val updated = el.copy(fontSize = max(8f, min(200f, el.fontSize * scaleFactor.coerceIn(0.9f, 1.1f)))); elements[index] = updated; selectedElement.value = updated; dragStartOffset = offset }
        if (el is BoardElement.EmojiElement) { val scaleFactor = 1f + delta.x * 2f; val updated = el.copy(size = max(12f, min(200f, el.size * scaleFactor.coerceIn(0.9f, 1.1f)))); elements[index] = updated; selectedElement.value = updated; dragStartOffset = offset }
    }

    private fun moveSelectedElement(offset: Offset) { val el = selectedElement.value ?: return; val index = elements.indexOf(el); if (index == -1) return; val delta = offset - dragStartOffset; val updated: BoardElement = when (el) { is BoardElement.ShapeElement -> el.copy(offset = (initialElementOffset + delta).clamp01()); is BoardElement.TextElement -> el.copy(offset = (initialElementOffset + delta).clamp01()); is BoardElement.EmojiElement -> el.copy(offset = (initialElementOffset + delta).clamp01()); else -> el }; elements[index] = updated; selectedElement.value = updated }
    private fun Offset.clamp01() = Offset(x.coerceIn(0f, 0.98f), y.coerceIn(0f, 0.98f))

    private fun pushUndoSnapshot() { undoStack.addLast(elements.toList()); if (undoStack.size > 30) undoStack.removeFirst(); redoStack.clear() }
    fun undo() { if (undoStack.isEmpty()) return; redoStack.addLast(elements.toList()); val prev = undoStack.removeLast(); elements.clear(); elements.addAll(prev); selectedElement.value = null; selectedElementBounds.value = Rect.Zero; redrawTrigger.value++ }
    fun redo() { if (redoStack.isEmpty()) return; undoStack.addLast(elements.toList()); val next = redoStack.removeLast(); elements.clear(); elements.addAll(next); redrawTrigger.value++ }

    fun addText(text: String) { pushUndoSnapshot(); val el = BoardElement.TextElement(text = text, offset = Offset(0.3f, 0.4f), fontSize = currentFontSize.value, color = currentColor.value, opacity = currentOpacity.value); elements.add(el); redoStack.clear(); if (isLive.value) syncElementToFirebase(el); redrawTrigger.value++ }
    fun addShape(type: ShapeType) { pushUndoSnapshot(); val el = BoardElement.ShapeElement(type = type, offset = Offset(0.25f, 0.3f), size = Size(0.3f, 0.2f), color = currentColor.value, fillColor = currentFillColor.value, strokeWidth = currentStrokeWidth.value, opacity = currentOpacity.value); elements.add(el); redoStack.clear(); if (isLive.value) syncElementToFirebase(el); redrawTrigger.value++ }
    fun addEmoji(emoji: String) { pushUndoSnapshot(); val el = BoardElement.EmojiElement(emoji = emoji, offset = Offset(0.3f, 0.4f), size = 40f); elements.add(el); redoStack.clear(); if (isLive.value) syncElementToFirebase(el); redrawTrigger.value++ }
    fun deleteSelectedElement() { val el = selectedElement.value ?: return; pushUndoSnapshot(); elements.remove(el); if (isLive.value && classroomId.isNotEmpty()) { idOf(el)?.let { rtdb.child("whiteboards/$classroomId/slides/${currentSlideIndex.value}/strokes/${it}").removeValue() } }; selectedElement.value = null; selectedElementBounds.value = Rect.Zero; redoStack.clear(); redrawTrigger.value++ }
    fun duplicateSelected() { val el = selectedElement.value ?: return; pushUndoSnapshot(); val dup: BoardElement = when (el) { is BoardElement.ShapeElement -> el.copy(id = System.nanoTime(), offset = (el.offset + Offset(0.03f, 0.03f)).clamp01()); is BoardElement.TextElement -> el.copy(id = System.nanoTime(), offset = (el.offset + Offset(0.03f, 0.03f)).clamp01()); is BoardElement.EmojiElement -> el.copy(id = System.nanoTime(), offset = (el.offset + Offset(0.03f, 0.03f)).clamp01()); else -> return }; elements.add(dup); selectedElement.value = dup; redoStack.clear(); if (isLive.value) syncElementToFirebase(dup); redrawTrigger.value++ }
    fun clearBoard() { pushUndoSnapshot(); elements.clear(); elementBoundsMap.clear(); selectedElement.value = null; selectedElementBounds.value = Rect.Zero; isDrawing.value = false; currentPoints.clear(); redoStack.clear(); if (isLive.value) rtdb.child("whiteboards/$classroomId/slides/${currentSlideIndex.value}/strokes").removeValue(); redrawTrigger.value++ }

    fun saveToPdfAndUpload(context: Context, onComplete: () -> Unit) {
        if (capturedFramePaths.isEmpty()) { onComplete(); return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = "Lecture_Notes_${System.currentTimeMillis()}.pdf"
                val pdfFile = File(context.cacheDir, fileName)
                val pdfDocument = PdfDocument()
                val paint = Paint()

                capturedFramePaths.forEachIndexed { index, path ->
                    val bitmap = BitmapFactory.decodeFile(path)
                    if (bitmap != null) {
                        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        val canvas = page.canvas
                        val scale = min(595f / bitmap.width, 842f / bitmap.height)
                        val left = (595f - bitmap.width * scale) / 2f
                        val top = (842f - bitmap.height * scale) / 2f
                        val matrix = android.graphics.Matrix()
                        matrix.postScale(scale, scale)
                        matrix.postTranslate(left, top)
                        canvas.drawBitmap(bitmap, matrix, paint)
                        pdfDocument.finishPage(page)
                        bitmap.recycle()
                    }
                }
                FileOutputStream(pdfFile).use { out -> pdfDocument.writeTo(out) }
                pdfDocument.close()
                val storageRef = storage.reference.child("classrooms/$classroomId/note/$fileName")
                storageRef.putFile(android.net.Uri.fromFile(pdfFile)).await()
                val downloadUrl = storageRef.downloadUrl.await()
                val eduFile = EduFile(name = fileName, url = downloadUrl.toString(), type = "note", extension = "pdf", size = pdfFile.length(), classId = classroomId)
                firestore.collection("files").add(eduFile.toMap()).await()
                capturedFramePaths.forEach { File(it).delete() }
                pdfFile.delete()
                withContext(Dispatchers.Main) { capturedFramePaths.clear(); savedFramesCount.value = 0; onComplete() }
            } catch (e: Exception) { Log.e("SmartBoard", "PDF Export failed", e); withContext(Dispatchers.Main) { onComplete() } }
        }
    }
}
