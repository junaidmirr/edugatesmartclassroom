package com.judev.edugate.teacher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.judev.edugate.models.Classroom

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDashboardScreen(
    viewModel: TeacherViewModel = viewModel(),
    onClassroomClick: (String) -> Unit
) {
    val classrooms by viewModel.classrooms.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    var classroomToDelete by remember { mutableStateOf<Classroom?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(0.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Classroom")
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
            Text("YOUR CLASSES", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Spacer(Modifier.height(24.dp))

            if (classrooms.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("NO CLASSROOMS FOUND", fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 1.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(classrooms, key = { it.id }) { classroom ->
                        ClassroomItem(classroom, onClick = { onClassroomClick(classroom.id) }, onDelete = { classroomToDelete = classroom })
                    }
                }
            }
        }
    }

    if (showDialog) {
        CreateClassroomDialog(
            onDismiss = { showDialog = false },
            onCreate = { name, teacher, code, icon ->
                viewModel.createClassroom(name, teacher, code, icon)
                showDialog = false
            },
            generatedCode = viewModel.generateClassCode()
        )
    }

    classroomToDelete?.let { classroom ->
        AlertDialog(
            onDismissRequest = { classroomToDelete = null },
            title = { Text("DELETE CLASSROOM", fontWeight = FontWeight.Black) },
            text = { Text("Are you sure you want to delete '${classroom.name.uppercase()}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteClassroom(classroom.id)
                        classroomToDelete = null
                    }
                ) {
                    Text("DELETE", color = Color.Red, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { classroomToDelete = null }) {
                    Text("CANCEL", fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(0.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun ClassroomItem(classroom: Classroom, onClick: () -> Unit, onDelete: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, MaterialTheme.colorScheme.primary)
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = getIconForIndex(classroom.profileIcon),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(classroom.name.uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text("CODE: ${classroom.code}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray.copy(alpha = 0.5f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateClassroomDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, Int) -> Unit,
    generatedCode: String
) {
    var name by remember { mutableStateOf("") }
    var teacherName by remember { mutableStateOf("") }
    var selectedIconIndex by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text("NEW CLASSROOM", fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(32.dp))

                EdugateSimpleTextField(value = name, onValueChange = { name = it }, label = "CLASS NAME")
                Spacer(Modifier.height(16.dp))
                EdugateSimpleTextField(value = teacherName, onValueChange = { teacherName = it }, label = "TEACHER NAME")
                
                Spacer(Modifier.height(32.dp))
                Text("SELECT ICON", fontSize = 10.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .border(2.dp, if (selectedIconIndex == index) MaterialTheme.colorScheme.primary else Color.LightGray)
                                .clickable { selectedIconIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                getIconForIndex(index),
                                contentDescription = null,
                                tint = if (selectedIconIndex == index) MaterialTheme.colorScheme.primary else Color.LightGray
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
                Text("ACCESS CODE", fontSize = 10.sp, fontWeight = FontWeight.Black)
                Text(generatedCode, fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)

                Spacer(Modifier.height(40.dp))

                Button(
                    onClick = { if (name.isNotBlank() && teacherName.isNotBlank()) onCreate(name, teacherName, generatedCode, selectedIconIndex) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(0.dp),
                    enabled = name.isNotBlank() && teacherName.isNotBlank()
                ) {
                    Text("CREATE", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun EdugateSimpleTextField(value: String, onValueChange: (String) -> Unit, label: String) {
    Column {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black)
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = Color.LightGray
            ),
            singleLine = true
        )
    }
}

fun getIconForIndex(index: Int): ImageVector {
    return when (index) {
        0 -> Icons.Default.Class
        1 -> Icons.Default.School
        else -> Icons.Default.Add
    }
}
