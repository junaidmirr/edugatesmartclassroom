package com.judev.edugate.teacher

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.judev.edugate.models.Classroom
import com.judev.edugate.models.DaySchedule
import com.judev.edugate.models.EduFile
import com.judev.edugate.models.Timetable
import java.util.*

enum class ClassroomSection {
    HOME, MATERIALS, TIMETABLE, NOTES, STUDENTS, LIVE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassroomDetailsScreen(
    classId: String, 
    onBack: () -> Unit,
    onSmartBoardClick: (String) -> Unit,
    onLiveStreamClick: (String, String, String) -> Unit,
    userRole: String
) {
    var classroom by remember { mutableStateOf<Classroom?>(null) }
    var currentSection by remember { mutableStateOf(ClassroomSection.HOME) }
    var showLiveClassOptions by remember { mutableStateOf(false) }
    var isSmartBoardLive by remember { mutableStateOf(false) }
    var liveStreamData by remember { mutableStateOf<Map<String, Any>?>(null) }
    
    val db = FirebaseFirestore.getInstance()
    val rtdb = FirebaseDatabase.getInstance("https://fipperai-default-rtdb.firebaseio.com/").reference

    LaunchedEffect(classId) {
        db.collection("classrooms").document(classId).get().addOnSuccessListener { doc ->
            val data = doc.data
            if (data != null) {
                classroom = Classroom(
                    id = doc.id,
                    name = data["name"] as? String ?: "",
                    teacherName = data["teacherName"] as? String ?: "",
                    code = data["code"] as? String ?: "",
                    createdBy = data["createdBy"] as? String ?: "",
                    profileIcon = (data["profileIcon"] as? Long)?.toInt() ?: 0
                )
            }
        }
        
        rtdb.child("whiteboards").child(classId).child("isLive")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    isSmartBoardLive = snapshot.getValue(Boolean::class.java) ?: false
                }
                override fun onCancelled(error: DatabaseError) {}
            })
            
        rtdb.child("live_streams").child(classId)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    liveStreamData = snapshot.value as? Map<String, Any>
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        when(currentSection) {
                            ClassroomSection.HOME -> classroom?.name?.uppercase() ?: "LOADING..."
                            ClassroomSection.MATERIALS -> "MATERIALS"
                            ClassroomSection.TIMETABLE -> "TIMETABLE"
                            ClassroomSection.NOTES -> "LECTURE NOTES"
                            ClassroomSection.STUDENTS -> "STUDENTS"
                            ClassroomSection.LIVE -> "LIVE SESSIONS"
                        },
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        fontSize = 18.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentSection == ClassroomSection.HOME) onBack()
                        else currentSection = ClassroomSection.HOME
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (classroom == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (currentSection) {
                    ClassroomSection.HOME -> HomeSection(
                        classroom!!, 
                        userRole, 
                        isSmartBoardLive, 
                        liveStreamData,
                        onSectionSelect = { section ->
                            if (section == null && userRole == "teacher") {
                                showLiveClassOptions = true
                            } else if (section != null) {
                                currentSection = section
                            }
                        },
                        onJoinSmartBoard = { onSmartBoardClick(classId) },
                        onJoinLiveStream = { type -> onLiveStreamClick(classId, userRole, type) }
                    )
                    ClassroomSection.MATERIALS -> FileListSection(classId, "material", userRole)
                    ClassroomSection.TIMETABLE -> TimetableSection(classId, userRole)
                    ClassroomSection.NOTES -> FileListSection(classId, "note", userRole)
                    ClassroomSection.STUDENTS -> JoinedStudentsSection(classId, userRole)
                    ClassroomSection.LIVE -> LiveSessionsSection(
                        classId, 
                        isSmartBoardLive, 
                        liveStreamData, 
                        onJoinSmartBoard = onSmartBoardClick,
                        onJoinLiveStream = { type -> onLiveStreamClick(classId, userRole, type) }
                    )
                }
            }
        }
    }

    if (showLiveClassOptions) {
        LiveClassOptionsDialog(
            onDismiss = { showLiveClassOptions = false },
            onSmartBoardClick = { 
                showLiveClassOptions = false
                onSmartBoardClick(classId)
            },
            onLiveStreamClick = { type ->
                showLiveClassOptions = false
                onLiveStreamClick(classId, "teacher", type)
            }
        )
    }
}

@Composable
fun LiveSessionsSection(
    classId: String, 
    isBoardLive: Boolean, 
    liveStreamData: Map<String, Any>?,
    onJoinSmartBoard: (String) -> Unit,
    onJoinLiveStream: (String) -> Unit
) {
    val isStreamLive = liveStreamData?.get("isLive") as? Boolean ?: false
    val streamType = liveStreamData?.get("type") as? String ?: ""

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (isBoardLive) {
            LiveStatusCard("SMARTBOARD LIVE", "ACTIVE DRAWING SESSION", Color(0xFF00C853)) {
                onJoinSmartBoard(classId)
            }
        }
        
        if (isStreamLive) {
            val title = if (streamType == "camera") "CAMERA LIVE" else "SCREEN SHARE LIVE"
            LiveStatusCard(title, "ACTIVE VIDEO SESSION", Color.Red) {
                onJoinLiveStream(streamType)
            }
        }

        if (!isBoardLive && !isStreamLive) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.VideocamOff, null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("NO ACTIVE SESSIONS", color = Color.Gray, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
            }
        }
    }
}

@Composable
fun LiveStatusCard(title: String, description: String, color: Color, onJoin: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, color)
            .background(color.copy(alpha = 0.05f))
            .padding(24.dp)
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.Black, fontSize = 20.sp, color = color, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(description, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onJoin,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(0.dp)
            ) {
                Text("JOIN NOW", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
fun JoinedStudentsSection(classId: String, userRole: String) {
    val db = FirebaseFirestore.getInstance()
    var students by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }

    LaunchedEffect(classId) {
        db.collection("join_requests")
            .whereEqualTo("classId", classId)
            .whereEqualTo("status", "approved")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    students = snapshot.documents.map { doc ->
                        mapOf(
                            "id" to doc.id,
                            "email" to (doc.getString("studentEmail") ?: "Unknown")
                        )
                    }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        if (students.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO STUDENTS JOINED", fontWeight = FontWeight.Bold, color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(students) { student ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, MaterialTheme.colorScheme.primary)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(
                                student["email"]?.uppercase() ?: "", 
                                modifier = Modifier.weight(1f), 
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                            
                            if (userRole == "teacher") {
                                IconButton(onClick = {
                                    db.collection("join_requests").document(student["id"]!!).delete()
                                }) {
                                    Icon(Icons.Default.RemoveCircle, "Remove", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveClassOptionsDialog(
    onDismiss: () -> Unit, 
    onSmartBoardClick: () -> Unit,
    onLiveStreamClick: (String) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.primary)
                .padding(24.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("START SESSION", fontWeight = FontWeight.Black, fontSize = 24.sp)
                Spacer(Modifier.height(8.dp))
                LiveOptionItem(Icons.Default.Edit, "SMARTBOARD", Color(0xFF00C853)) { onSmartBoardClick() }
                LiveOptionItem(Icons.Default.ScreenShare, "SCREEN SHARE", Color.Black) { onLiveStreamClick("screen") }
                LiveOptionItem(Icons.Default.Videocam, "CAMERA SHARE", Color.Black) { onLiveStreamClick("camera") }
                
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("CANCEL", fontWeight = FontWeight.Black, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun LiveOptionItem(icon: ImageVector, title: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, color)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color)
            Spacer(Modifier.width(16.dp))
            Text(title, fontWeight = FontWeight.Black, color = color, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun HomeSection(
    classroom: Classroom, 
    userRole: String, 
    isBoardLive: Boolean, 
    liveStreamData: Map<String, Any>?,
    onSectionSelect: (ClassroomSection?) -> Unit,
    onJoinSmartBoard: () -> Unit,
    onJoinLiveStream: (String) -> Unit
) {
    val isStreamLive = liveStreamData?.get("isLive") as? Boolean ?: false
    val streamType = liveStreamData?.get("type") as? String ?: ""

    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = "CLASSROOM HUB",
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = classroom.name.uppercase(),
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp
        )
        Text(text = "CODE: ${classroom.code}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        
        if (userRole == "student") {
            if (isBoardLive) {
                Spacer(Modifier.height(24.dp))
                LiveBanner("SMARTBOARD IS LIVE", Color(0xFF00C853), onJoinSmartBoard)
            }
            if (isStreamLive) {
                Spacer(Modifier.height(16.dp))
                val title = if (streamType == "camera") "CAMERA IS LIVE" else "SCREEN SHARE IS LIVE"
                LiveBanner(title, Color.Red) { onJoinLiveStream(streamType) }
            }
        }
        
        Spacer(Modifier.height(40.dp))

        val options = mutableListOf(
            HomeOption("MATERIALS", Icons.Default.Folder, ClassroomSection.MATERIALS),
            HomeOption("TIMETABLE", Icons.Default.EventNote, ClassroomSection.TIMETABLE),
            HomeOption("LECTURE NOTES", Icons.Default.Description, ClassroomSection.NOTES),
            HomeOption("STUDENTS", Icons.Default.Group, ClassroomSection.STUDENTS),
            HomeOption("LIVE", Icons.Default.Podcasts, ClassroomSection.LIVE)
        )
        
        if (userRole == "teacher") {
            options.add(HomeOption("START LIVE", Icons.Default.VideoCall, null))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(options) { option ->
                val isLiveAlert = option.title == "LIVE" && (isBoardLive || isStreamLive)
                OptionCard(option.title, option.icon, isLiveAlert) {
                    onSectionSelect(option.section)
                }
            }
        }
    }
}

@Composable
fun LiveBanner(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, color)
            .background(color.copy(alpha = 0.1f))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(12.dp))
            Text(text, color = color, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowForward, null, tint = color, modifier = Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListSection(classId: String, type: String, userRole: String, viewModel: ClassroomViewModel = viewModel()) {
    val context = LocalContext.current
    val files by viewModel.files.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()
    var fileToDelete by remember { mutableStateOf<EduFile?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let { viewModel.uploadFile(context, it, classId, type) }
        }
    )

    LaunchedEffect(classId, type) {
        viewModel.fetchFiles(classId, type)
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        if (userRole == "teacher") {
            Button(
                onClick = { launcher.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation", "text/plain")) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (type == "material") "UPLOAD MATERIAL" else "ADD NOTE", fontWeight = FontWeight.Black)
            }

            uploadProgress?.let { progress ->
                Spacer(Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.LightGray
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO FILES FOUND", fontWeight = FontWeight.Bold, color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(files, key = { it.id }) { file ->
                    FileItem(file, onDelete = if (userRole == "teacher") { { fileToDelete = file } } else null) {
                        openFile(context, file, viewModel)
                    }
                }
            }
        }
    }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("DELETE FILE", fontWeight = FontWeight.Black) },
            text = { Text("ARE YOU SURE YOU WANT TO DELETE '${file.name.uppercase()}'?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteFile(context, file); fileToDelete = null }) {
                    Text("DELETE", color = Color.Red, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("CANCEL", fontWeight = FontWeight.Bold) }
            },
            shape = RoundedCornerShape(0.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableSection(classId: String, userRole: String, viewModel: TimetableViewModel = viewModel()) {
    val context = LocalContext.current
    val timetables by viewModel.timetables.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var timetableToDelete by remember { mutableStateOf<Timetable?>(null) }
    var selectedTimetable by remember { mutableStateOf<Timetable?>(null) }

    LaunchedEffect(classId) {
        viewModel.fetchTimetables(classId)
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        if (userRole == "teacher") {
            Button(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(0.dp)
            ) {
                Icon(Icons.Default.CalendarToday, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("CREATE TIMETABLE", fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(24.dp))
        }

        if (timetables.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("NO TIMETABLES FOUND", fontWeight = FontWeight.Bold, color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(timetables, key = { it.id }) { timetable ->
                    TimetableItem(
                        timetable, 
                        selectedTimetable, 
                        onDelete = if (userRole == "teacher") { { timetableToDelete = timetable } } else null,
                        onSelect = { selectedTimetable = it }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateTimetableDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { date, schedules ->
                viewModel.createTimetable(context, classId, date, schedules)
                showCreateDialog = false
            }
        )
    }

    timetableToDelete?.let {
        AlertDialog(
            onDismissRequest = { timetableToDelete = null },
            title = { Text("DELETE TIMETABLE", fontWeight = FontWeight.Black) },
            text = { Text("REMOVE THIS TIMETABLE?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteTimetable(context, it); timetableToDelete = null }) {
                    Text("DELETE", color = Color.Red, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { timetableToDelete = null }) { Text("CANCEL", fontWeight = FontWeight.Bold) }
            },
            shape = RoundedCornerShape(0.dp)
        )
    }
}

@Composable
fun TimetableItem(timetable: Timetable, selectedTimetable: Timetable?, onDelete: (() -> Unit)?, onSelect: (Timetable?) -> Unit) {
    val context = LocalContext.current
    val isSelected = selectedTimetable?.id == timetable.id
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray)
            .clickable { onSelect(if (isSelected) null else timetable) }
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TableChart, null, size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(timetable.creationDate, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                if (onDelete != null) {
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Gray) }
                }
            }
            
            if (isSelected) {
                Spacer(Modifier.height(16.dp))
                timetable.schedule.forEach { schedule ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(schedule.day.uppercase(), modifier = Modifier.width(100.dp), fontWeight = FontWeight.Black, fontSize = 12.sp)
                        Text(schedule.timeRange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(timetable.fileUrl))) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Text("OPEN EXCEL", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun CreateTimetableDialog(onDismiss: () -> Unit, onCreate: (String, List<DaySchedule>) -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf("") }
    val schedules = remember { 
        mutableStateListOf(
            DaySchedule("Monday", ""), DaySchedule("Tuesday", ""),
            DaySchedule("Wednesday", ""), DaySchedule("Thursday", ""),
            DaySchedule("Friday", ""), DaySchedule("Saturday", ""),
            DaySchedule("Sunday", "")
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.primary)
                .padding(24.dp)
        ) {
            val scrollState = rememberScrollState()
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                Text("NEW TIMETABLE", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(24.dp))
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.LightGray)
                        .clickable {
                            val cal = Calendar.getInstance()
                            DatePickerDialog(context, { _, y, m, d -> date = "$d/${m+1}/$y" }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                        }
                        .padding(16.dp)
                ) {
                    Text(if (date.isEmpty()) "SELECT DATE" else date, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(24.dp))

                schedules.forEachIndexed { index, schedule ->
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Text(schedule.day.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black)
                        TextField(
                            value = schedule.timeRange,
                            onValueChange = { schedules[index] = schedule.copy(timeRange = it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. 09:00 - 10:00", fontSize = 12.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = { if (date.isNotEmpty()) onCreate(date, schedules) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(0.dp)
                ) {
                    Text("SAVE TIMETABLE", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun FileItem(file: EduFile, onDelete: (() -> Unit)?, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Color.LightGray)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when(file.extension.lowercase()) {
                    "pdf" -> Icons.Default.PictureAsPdf
                    else -> Icons.Default.Description
                },
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name.uppercase(), fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text("${(file.size / 1024)} KB", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Gray.copy(alpha = 0.5f)) }
            }
        }
    }
}

fun openFile(context: android.content.Context, eduFile: EduFile, viewModel: ClassroomViewModel) {
    val localFile = viewModel.getLocalFile(context, eduFile.name)
    if (localFile != null) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", localFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } else {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(eduFile.url))
        context.startActivity(intent)
    }
}

@Composable
fun OptionCard(title: String, icon: ImageVector, isAlert: Boolean, onClick: () -> Unit) {
    val borderColor = if (isAlert) Color.Red else MaterialTheme.colorScheme.primary
    val bgColor = if (isAlert) Color.Red.copy(alpha = 0.05f) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(bgColor)
            .border(2.dp, borderColor)
            .clickable { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Column {
            Icon(imageVector = icon, contentDescription = null, tint = borderColor, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Black, color = borderColor, letterSpacing = 1.sp)
        }
        if (isAlert) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color.Red)
            )
        }
    }
}

data class HomeOption(val title: String, val icon: ImageVector, val section: ClassroomSection?)

fun size(dp: androidx.compose.ui.unit.Dp) = Modifier.size(dp)
