package com.judev.edugate.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.judev.edugate.models.Classroom
import com.judev.edugate.teacher.getIconForIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(
    viewModel: StudentViewModel = viewModel(),
    onClassroomClick: (String) -> Unit
) {
    val enrolledClassrooms by viewModel.enrolledClassrooms.collectAsState()
    val liveClassrooms by viewModel.liveClassrooms.collectAsState()
    val joinStatus by viewModel.joinStatus.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showJoinDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Join Classroom")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text("JOINED CLASSES", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(24.dp))

            if (enrolledClassrooms.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("NO CLASSES JOINED", fontWeight = FontWeight.Bold, color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(enrolledClassrooms) { classroom ->
                        val isLive = liveClassrooms.contains(classroom.id)
                        StudentClassroomItem(
                            classroom = classroom, 
                            isLive = isLive,
                            onClick = { onClassroomClick(classroom.id) }
                        )
                    }
                }
            }
        }
    }

    if (showJoinDialog) {
        JoinClassroomDialog(
            status = joinStatus,
            onDismiss = { 
                showJoinDialog = false 
                viewModel.resetJoinStatus()
            },
            onJoin = { code -> viewModel.joinClassroom(code) }
        )
    }
}

@Composable
fun StudentClassroomItem(classroom: Classroom, isLive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, if (isLive) Color.Red else MaterialTheme.colorScheme.primary)
            .background(if (isLive) Color.Red.copy(alpha = 0.05f) else Color.Transparent)
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = getIconForIndex(classroom.profileIcon),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (isLive) Color.Red else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(classroom.name.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("TEACHER: ${classroom.teacherName.uppercase()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            }
            if (isLive) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Red))
                    Spacer(Modifier.width(8.dp))
                    Text("LIVE", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
            } else {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, size(16.dp), tint = Color.LightGray)
            }
        }
    }
}

@Composable
fun JoinClassroomDialog(
    status: JoinStatus,
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, MaterialTheme.colorScheme.primary)
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text("JOIN CLASSROOM", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(32.dp))
                
                Text("6-DIGIT CODE", fontSize = 10.sp, fontWeight = FontWeight.Black)
                TextField(
                    value = code,
                    onValueChange = { if(it.length <= 6) code = it },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    ),
                    singleLine = true
                )

                if (status is JoinStatus.Error) {
                    Text(status.message, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }

                if (status is JoinStatus.Success) {
                    Text("REQUEST SENT", color = Color(0xFF00C853), fontSize = 12.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(Modifier.height(32.dp))

                if (status is JoinStatus.Loading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                } else {
                    Button(
                        onClick = { onJoin(code) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(0.dp),
                        enabled = code.length == 6 && status !is JoinStatus.Success
                    ) {
                        Text("SEND REQUEST", fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("CANCEL", color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun size(dp: androidx.compose.ui.unit.Dp) = Modifier.size(dp)
