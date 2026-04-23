package com.judev.edugate.teacher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.judev.edugate.models.JoinRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class JoinRequestsViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val _requests = MutableStateFlow<List<JoinRequest>>(emptyList())
    val requests = _requests.asStateFlow()

    fun fetchRequests() {
        val currentUser = auth.currentUser ?: return
        
        // Find classrooms created by this teacher
        viewModelScope.launch {
            val teacherClasses = db.collection("classrooms")
                .whereEqualTo("createdBy", currentUser.uid)
                .get().await()
            
            val classIds = teacherClasses.documents.map { it.id }
            if (classIds.isEmpty()) {
                _requests.value = emptyList()
                return@launch
            }

            // Fetch join requests for those classrooms
            db.collection("join_requests")
                .whereIn("classId", classIds)
                .whereEqualTo("status", "pending")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        _requests.value = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(JoinRequest::class.java)?.copy(id = doc.id)
                        }
                    }
                }
        }
    }

    fun updateRequestStatus(requestId: String, status: String) {
        viewModelScope.launch {
            db.collection("join_requests").document(requestId).update("status", status).await()
        }
    }
}