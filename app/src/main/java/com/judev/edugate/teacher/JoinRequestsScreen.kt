package com.judev.edugate.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.judev.edugate.models.JoinRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinRequestsScreen(onBack: () -> Unit, viewModel: JoinRequestsViewModel = viewModel()) {
    val requests by viewModel.requests.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchRequests()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join Requests") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (requests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No pending requests.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(requests) { request ->
                    JoinRequestItem(
                        request = request,
                        onAccept = { viewModel.updateRequestStatus(request.id, "approved") },
                        onReject = { viewModel.updateRequestStatus(request.id, "rejected") }
                    )
                }
            }
        }
    }
}

@Composable
fun JoinRequestItem(request: JoinRequest, onAccept: () -> Unit, onReject: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(request.studentEmail, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("wants to join ${request.className}", fontSize = 12.sp, color = Color.Gray)
            }
            
            IconButton(onClick = onReject) {
                Icon(Icons.Default.Close, contentDescription = "Reject", tint = Color.Red)
            }
            IconButton(onClick = onAccept) {
                Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color(0xFF4CAF50))
            }
        }
    }
}
