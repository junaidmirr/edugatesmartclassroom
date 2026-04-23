package com.judev.edugate.smartboard

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

// ─── Design tokens (Keeping original colors for tools) ──────────────────────────
private val BoardBg        = Color(0xFFF8F7F4)
private val ToolbarBg      = Color(0xFF1C1C1E)
private val ToolbarSurf    = Color(0xFF2C2C2E)
private val AccentTeal     = Color(0xFF00C9A7)
private val AccentAmber    = Color(0xFFFFB347)
private val TextOnDark     = Color(0xFFECECEC)
private val SubtleGray     = Color(0xFF8E8E93)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartBoardScreen(
    classId: String,
    onBack: () -> Unit,
    userRole: String = "teacher",
    viewModel: SmartBoardViewModel = viewModel()
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val currentTool      by viewModel.currentTool
    val currentColor     by viewModel.currentColor
    val currentFillColor by viewModel.currentFillColor
    val strokeWidth      by viewModel.currentStrokeWidth
    val selectedElement  by viewModel.selectedElement
    val isLive           by viewModel.isLive
    val opacity          by viewModel.currentOpacity
    val currentSlide     by viewModel.currentSlideIndex
    val savedFramesCount by viewModel.savedFramesCount
    
    val isMicOn by viewModel.isMicOn

    var showColorPicker   by remember { mutableStateOf(false) }
    var showFillPicker    by remember { mutableStateOf(false) }
    var showTextDialog    by remember { mutableStateOf(false) }
    var showEmojiDialog   by remember { mutableStateOf(false) }
    var showShapeSelector by remember { mutableStateOf(false) }
    var showLiveConfirm   by remember { mutableStateOf(false) }
    var showStrokePanel   by remember { mutableStateOf(false) }
    var showEndLiveConfirm by remember { mutableStateOf(false) }
    var isUploadingPdf    by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(classId) {
        viewModel.initClassroom(context, classId, userRole)
        if (userRole == "teacher") {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val onBackgroundColor = MaterialTheme.colorScheme.onBackground

    Box(modifier = Modifier.fillMaxSize().background(BoardBg)) {

        // ── CANVAS AREA ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { canvasSize = it.size.toSize() }
                .pointerInput(userRole, canvasSize) {
                    if (userRole != "teacher" || canvasSize == Size.Zero) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val normDown = Offset(down.position.x / canvasSize.width, down.position.y / canvasSize.height)
                        viewModel.onPointerDown(normDown)

                        var didDrag = false
                        drag(down.id) { change ->
                            didDrag = true
                            change.consume()
                            viewModel.onPointerMove(Offset(change.position.x / canvasSize.width, change.position.y / canvasSize.height))
                        }
                        viewModel.onPointerUp()
                        if (!didDrag && viewModel.currentTool.value == Tool.SELECT) viewModel.selectElementAt(normDown)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                @Suppress("UNUSED_VARIABLE")
                val trigger = viewModel.redrawTrigger.value
                val cw = size.width
                val ch = size.height
                if (cw == 0f || ch == 0f) return@Canvas

                viewModel.elements.forEach { element ->
                    when (element) {
                        is BoardElement.PathElement -> {
                            val pts = element.points
                            if (pts.size >= 2) {
                                val path = Path().apply {
                                    moveTo(pts[0].x * cw, pts[0].y * ch)
                                    for (i in 1 until pts.size) {
                                        if (i < pts.size - 1) {
                                            val mx = (pts[i].x + pts[i+1].x) / 2f * cw
                                            val my = (pts[i].y + pts[i+1].y) / 2f * ch
                                            quadraticTo(pts[i].x * cw, pts[i].y * ch, mx, my)
                                        } else { lineTo(pts[i].x * cw, pts[i].y * ch) }
                                    }
                                }
                                drawPath(path, element.color.copy(alpha = element.opacity),
                                    style = Stroke(element.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                                    blendMode = if (element.isEraser) BlendMode.Clear else BlendMode.SrcOver)
                            }
                        }
                        is BoardElement.ShapeElement -> {
                            val pos  = Offset(element.offset.x * cw, element.offset.y * ch)
                            val s    = Size(element.size.width * cw, element.size.height * ch)
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
                                        val tp = Path().apply { moveTo(pos.x+s.width/2, pos.y); lineTo(pos.x+s.width, pos.y+s.height); lineTo(pos.x, pos.y+s.height); close() }
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
                        is BoardElement.TextElement -> {
                            val pos = Offset(element.offset.x * cw, element.offset.y * ch)
                            val tlr = textMeasurer.measure(element.text, TextStyle(color = element.color.copy(alpha = element.opacity), fontSize = element.fontSize.sp, fontWeight = if (element.bold) FontWeight.Bold else FontWeight.Normal))
                            if (userRole == "teacher") viewModel.updateElementBounds(element, Rect(element.offset, Size(tlr.size.width.toFloat()/cw, tlr.size.height.toFloat()/ch)))
                            withTransform({ rotate(element.rotation, pivot = pos) }) { drawText(tlr, topLeft = pos) }
                        }
                        is BoardElement.EmojiElement -> {
                            val pos = Offset(element.offset.x * cw, element.offset.y * ch)
                            val tlr = textMeasurer.measure(element.emoji, TextStyle(fontSize = element.size.sp))
                            if (userRole == "teacher") viewModel.updateElementBounds(element, Rect(element.offset, Size(tlr.size.width.toFloat()/cw, tlr.size.height.toFloat()/ch)))
                            withTransform({ rotate(element.rotation, pivot = pos + Offset(tlr.size.width/2f, tlr.size.height/2f)) }) { drawText(tlr, topLeft = pos) }
                        }
                    }
                }

                val dp = viewModel.currentPoints
                if (viewModel.isDrawing.value && dp.size >= 2) {
                    val activePath = Path().apply {
                        moveTo(dp[0].x * cw, dp[0].y * ch)
                        for (i in 1 until dp.size) {
                            if (i < dp.size - 1) {
                                val mx = (dp[i].x + dp[i+1].x) / 2f * cw; val my = (dp[i].y + dp[i+1].y) / 2f * ch
                                quadraticTo(dp[i].x * cw, dp[i].y * ch, mx, my)
                            } else { lineTo(dp[i].x * cw, dp[i].y * ch) }
                        }
                    }
                    val isE = currentTool == Tool.ERASER
                    drawPath(activePath, color = if (isE) Color.White else currentColor.copy(alpha = opacity), style = Stroke(if (isE) strokeWidth * 5 else strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = if (isE) BlendMode.Clear else BlendMode.SrcOver)
                }

                if (userRole == "teacher" && selectedElement != null) {
                    val nb = if (selectedElement is BoardElement.ShapeElement) Rect((selectedElement as BoardElement.ShapeElement).offset, (selectedElement as BoardElement.ShapeElement).size) else viewModel.selectedElementBounds.value
                    if (nb != Rect.Zero) {
                        val b = Rect(nb.left*cw, nb.top*ch, nb.right*cw, nb.bottom*ch)
                        drawRect(color = onBackgroundColor, topLeft = b.topLeft, size = b.size, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
                    }
                }
            }
        }

        // ── TOP NAVIGATION (Cleaned up) ──────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                }
                
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    Text(
                        "WHITEBOARD", 
                        fontWeight = FontWeight.Black, 
                        fontSize = 12.sp, 
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.weight(1f))

                if (userRole == "teacher") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(onClick = { viewModel.undo() }) {
                            Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
                        }
                        
                        VerticalDivider(
                            modifier = Modifier.height(24.dp).padding(horizontal = 4.dp), 
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )

                        // SLIDE NAVIGATION
                        IconButton(onClick = { viewModel.prevSlide() }) {
                            Icon(Icons.Default.ChevronLeft, "Prev Slide", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        
                        Text(
                            "PAGE ${currentSlide + 1}", 
                            fontWeight = FontWeight.Black, 
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        IconButton(onClick = { viewModel.nextSlide() }) {
                            Icon(Icons.Default.ChevronRight, "Next Slide", tint = MaterialTheme.colorScheme.onSurface)
                        }

                        IconButton(onClick = { viewModel.addNewSlide() }) {
                            Icon(Icons.Default.Add, "New Slide", tint = Color(0xFF00C853), modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    // Student view slide indicator
                    Text(
                        "PAGE ${currentSlide + 1}", 
                        fontWeight = FontWeight.Black, 
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            }
        }

        // ── FLOATING LIVE & MIC CONTROLS (Bottom Left Corner) ────────────────
        if (userRole == "teacher") {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 100.dp), // Padded to be above bottom toolbar
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live Status Badge if already live
                if (isLive) {
                    Surface(
                        color = Color.Red,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            "LIVE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Mic Button
                FloatingActionButton(
                    onClick = { viewModel.toggleMic() },
                    containerColor = if (isMicOn) Color.White else Color.Red,
                    contentColor = if (isMicOn) Color(0xFF00C853) else Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp),
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(
                        if (isMicOn) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Mic",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Live Toggle Button
                FloatingActionButton(
                    onClick = { if (isLive) showEndLiveConfirm = true else showLiveConfirm = true },
                    containerColor = if (isLive) Color.Red else Color.Black,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp).widthIn(min = 100.dp),
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            if (isLive) Icons.Default.Stop else Icons.Default.Podcasts,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isLive) "STOP" else "GO LIVE",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // ── BOTTOM TOOLBAR (Reverted to original compact tool look) ──────────
        if (userRole == "teacher") {
            Surface(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter), color = ToolbarBg.copy(alpha = 0.98f)) {
                Column(Modifier.navigationBarsPadding()) {
                    AnimatedVisibility(visible = showStrokePanel, enter = expandVertically() + fadeIn(), exit  = shrinkVertically() + fadeOut()) {
                        Column(Modifier.fillMaxWidth().background(ToolbarSurf).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Size", color = SubtleGray, fontSize = 11.sp, modifier = Modifier.width(38.dp))
                                Slider(value = strokeWidth, onValueChange = { viewModel.currentStrokeWidth.value = it }, valueRange = 2f..60f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = AccentTeal, activeTrackColor = AccentTeal))
                                Text("${strokeWidth.toInt()}", color = TextOnDark, fontSize = 11.sp, modifier = Modifier.width(26.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Alpha", color = SubtleGray, fontSize = 11.sp, modifier = Modifier.width(38.dp))
                                Slider(value = opacity, onValueChange = { viewModel.currentOpacity.value = it }, valueRange = 0.1f..1f, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = AccentAmber, activeTrackColor = AccentAmber))
                                Text("${(opacity*100).toInt()}%", color = TextOnDark, fontSize = 11.sp, modifier = Modifier.width(26.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            val quickColors = listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta, Color.White)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                quickColors.forEach { c -> Box(Modifier.size(32.dp).clip(CircleShape).background(c).border(if (currentColor == c) 2.dp else 1.dp, if (currentColor == c) AccentTeal else SubtleGray, CircleShape).clickable { viewModel.currentColor.value = c }) }
                                Box(Modifier.size(32.dp).clip(CircleShape).background(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))).clickable { showColorPicker = true }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                            }
                        }
                    }
                    LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        item { CompactToolBtn(Icons.Default.Edit, "Pen", currentTool == Tool.PEN) { viewModel.currentTool.value = Tool.PEN; viewModel.selectedElement.value = null } }
                        item { CompactToolBtn(Icons.Default.Brush, "Marker", currentTool == Tool.MARKER) { viewModel.currentTool.value = Tool.MARKER; viewModel.selectedElement.value = null } }
                        item { CompactToolBtn(Icons.Default.AutoFixNormal, "Erase", currentTool == Tool.ERASER) { viewModel.currentTool.value = Tool.ERASER; viewModel.selectedElement.value = null } }
                        item { CompactToolBtn(Icons.Default.TextFields, "Text", currentTool == Tool.TEXT) { showTextDialog = true } }
                        item { CompactToolBtn(Icons.Default.Category, "Shape", currentTool == Tool.SHAPE) { showShapeSelector = true } }
                        item { CompactToolBtn(Icons.Default.EmojiEmotions, "Emoji", currentTool == Tool.EMOJI) { showEmojiDialog = true } }
                        item { CompactToolBtn(Icons.Default.TouchApp, "Select", currentTool == Tool.SELECT) { viewModel.currentTool.value = Tool.SELECT } }
                        item { 
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp).clickable { showStrokePanel = !showStrokePanel }) { 
                                Box(Modifier.size(28.dp).clip(CircleShape).background(currentColor).border(if (showStrokePanel) 2.dp else 1.dp, if (showStrokePanel) AccentTeal else SubtleGray, CircleShape)); 
                                Text("Tools", color = if (showStrokePanel) AccentTeal else SubtleGray, fontSize = 8.sp) 
                            } 
                        }
                        item { 
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp).clickable { showFillPicker = true }) { 
                                Box(Modifier.size(28.dp).clip(CircleShape).background(if (currentFillColor == Color.Transparent) ToolbarSurf else currentFillColor).border(1.dp, SubtleGray, CircleShape), contentAlignment = Alignment.Center) { 
                                    if (currentFillColor == Color.Transparent) Icon(Icons.Default.Close, null, tint = SubtleGray, modifier = Modifier.size(14.dp)) 
                                }; Text("Fill", color = SubtleGray, fontSize = 8.sp) 
                            } 
                        }
                        if (selectedElement != null) {
                            item { CompactToolBtn(Icons.Default.Delete, "Delete", false) { viewModel.deleteSelectedElement() } }
                        }
                    }
                }
            }
        }

        if (userRole == "student") {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLive) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isLive) "TEACHER IS DRAWING LIVE" else "VIEWING WHITEBOARD", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp)
                }
            }
        }

        if (isUploadingPdf) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("SAVING ALL PAGES...", color = Color.White, fontWeight = FontWeight.Black)
                }
            }
        }
    }

    // ── DIALOGS ──────────────────────────────────────────────────────────
    if (showLiveConfirm) EdugateDialog(title = "GO LIVE?", text = "Students will see your board in real-time.", onConfirm = { viewModel.startLiveSession(); showLiveConfirm = false }, onDismiss = { showLiveConfirm = false })

    if (showEndLiveConfirm) EdugateDialog(
        title = "END SESSION?",
        text = "Would you like to save all pages as PDF notes for the students?",
        confirmText = "SAVE & END",
        onConfirm = {
            isUploadingPdf = true
            viewModel.captureAllSlides(context, density) {
                viewModel.saveToPdfAndUpload(context) { 
                    isUploadingPdf = false; 
                    viewModel.stopLiveSession()
                    showEndLiveConfirm = false
                    onBack() 
                }
            }
        },
        onDismiss = { 
            viewModel.stopLiveSession()
            showEndLiveConfirm = false
            onBack()
        }
    )

    if (showColorPicker) ColorGridDialog("COLORS", onDismiss = { showColorPicker = false }) { viewModel.currentColor.value = it }
    if (showFillPicker) ColorGridDialog("FILL", onDismiss = { showFillPicker = false }, allowTransparent = true) { viewModel.currentFillColor.value = it }
    if (showTextDialog) TextInputDialog(onDismiss = { showTextDialog = false }) { viewModel.addText(it); showTextDialog = false }
    if (showEmojiDialog) EmojiGridDialog(onDismiss = { showEmojiDialog = false }) { viewModel.addEmoji(it); showEmojiDialog = false }
    if (showShapeSelector) ShapeGridDialog(onDismiss = { showShapeSelector = false }) { viewModel.addShape(it); showShapeSelector = false }
}

@Composable
fun CompactToolBtn(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp).clickable { onClick() }) {
        Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(if (isSelected) AccentTeal.copy(alpha = 0.2f) else Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = if (isSelected) AccentTeal else TextOnDark, modifier = Modifier.size(20.dp))
        }
        Text(label, fontSize = 8.sp, color = if (isSelected) AccentTeal else SubtleGray)
    }
}

@Composable
fun EdugateDialog(title: String, text: String, confirmText: String = "START", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).border(2.dp, MaterialTheme.colorScheme.primary).padding(24.dp)) {
            Column {
                Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp)); Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)); Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { onDismiss() }) { Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Black) }
                    Spacer(Modifier.width(16.dp)); Button(onClick = onConfirm, shape = RoundedCornerShape(0.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text(confirmText, fontWeight = FontWeight.Black) }
                }
            }
        }
    }
}

@Composable
fun ColorGridDialog(title: String, onDismiss: () -> Unit, allowTransparent: Boolean = false, onSelected: (Color) -> Unit) {
    val colors = listOf(Color.Black, Color.Red, Color.Blue, Color.Green, Color.Yellow, Color.Magenta, Color.Cyan, Color.White)
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).border(2.dp, MaterialTheme.colorScheme.primary).padding(24.dp)) {
            Column {
                Text(title, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface); Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(colors) { color -> Box(modifier = Modifier.size(40.dp).background(color).border(1.dp, MaterialTheme.colorScheme.onSurface, CircleShape).clickable { onSelected(color); onDismiss() }) }
                }
                if (allowTransparent) {
                    TextButton(onClick = { onSelected(Color.Transparent); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("NONE (TRANSPARENT)", color = Color.Gray, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun TextInputDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).border(2.dp, MaterialTheme.colorScheme.primary).padding(24.dp)) {
            Column {
                Text("ADD TEXT", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                TextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface))
                Spacer(Modifier.height(16.dp)); Button(onClick = { onConfirm(text) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(0.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("ADD", fontWeight = FontWeight.Black) }
            }
        }
    }
}

@Composable
fun EmojiGridDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val emojis = listOf("😀","😂","😍","🤔","😎","🥳","👍","🔥","⭐","📚")
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).border(2.dp, MaterialTheme.colorScheme.primary).padding(24.dp)) {
            LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(100.dp)) {
                items(emojis) { emoji -> Box(modifier = Modifier.size(48.dp).clickable { onConfirm(emoji) }, contentAlignment = Alignment.Center) { Text(emoji, fontSize = 24.sp) } }
            }
        }
    }
}

@Composable
fun ShapeGridDialog(onDismiss: () -> Unit, onConfirm: (ShapeType) -> Unit) {
    val shapes = listOf(ShapeType.RECTANGLE, ShapeType.CIRCLE, ShapeType.LINE, ShapeType.TRIANGLE, ShapeType.ARROW)
    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface).border(2.dp, MaterialTheme.colorScheme.primary).padding(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                shapes.forEach { shape ->
                    IconButton(onClick = { onConfirm(shape) }) {
                        Icon(when(shape) {
                            ShapeType.RECTANGLE -> Icons.Default.Rectangle
                            ShapeType.CIRCLE -> Icons.Default.Circle
                            ShapeType.LINE -> Icons.Default.HorizontalRule
                            ShapeType.TRIANGLE -> Icons.Default.ChangeHistory
                            ShapeType.ARROW -> Icons.AutoMirrored.Filled.ArrowForward
                        }, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
