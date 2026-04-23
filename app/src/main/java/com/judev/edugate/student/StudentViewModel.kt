package com.judev.edugate.student

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.judev.edugate.models.Classroom
import com.judev.edugate.models.JoinRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class StudentViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val rtdb = FirebaseDatabase.getInstance("https://fipperai-default-rtdb.firebaseio.com/").reference

    private val _enrolledClassrooms = MutableStateFlow<List<Classroom>>(emptyList())
    val enrolledClassrooms = _enrolledClassrooms.asStateFlow()

    private val _liveClassrooms = MutableStateFlow<Set<String>>(emptySet())
    val liveClassrooms = _liveClassrooms.asStateFlow()

    private val _joinStatus = MutableStateFlow<JoinStatus>(JoinStatus.Idle)
    val joinStatus = _joinStatus.asStateFlow()

    init {
        fetchEnrolledClassrooms()
    }

    private fun fetchEnrolledClassrooms() {
        val currentUser = auth.currentUser ?: return
        
        db.collection("join_requests")
            .whereEqualTo("studentId", currentUser.uid)
            .whereEqualTo("status", "approved")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val classIds = snapshot.documents.mapNotNull { it.getString("classId") }
                    if (classIds.isNotEmpty()) {
                        db.collection("classrooms")
                            .whereIn(FieldPath.documentId(), classIds)
                            .addSnapshotListener { classSnapshot, _ ->
                                if (classSnapshot != null) {
                                    val list = classSnapshot.documents.mapNotNull { doc ->
                                        val data = doc.data ?: return@mapNotNull null
                                        Classroom(
                                            id = doc.id,
                                            name = data["name"] as? String ?: "",
                                            teacherName = data["teacherName"] as? String ?: "",
                                            code = data["code"] as? String ?: "",
                                            createdBy = data["createdBy"] as? String ?: "",
                                            profileIcon = (data["profileIcon"] as? Long)?.toInt() ?: 0
                                        )
                                    }
                                    _enrolledClassrooms.value = list
                                    listenForLiveStatuses(classIds)
                                }
                            }
                    } else {
                        _enrolledClassrooms.value = emptyList()
                        _liveClassrooms.value = emptySet()
                    }
                }
            }
    }

    private fun listenForLiveStatuses(classIds: List<String>) {
        classIds.forEach { classId ->
            rtdb.child("whiteboards").child(classId).child("isLive")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val isLive = snapshot.getValue(Boolean::class.java) ?: false
                        val currentLive = _liveClassrooms.value.toMutableSet()
                        if (isLive) currentLive.add(classId) else currentLive.remove(classId)
                        _liveClassrooms.value = currentLive
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
        }
    }

    fun joinClassroom(code: String) {
        val currentUser = auth.currentUser ?: return
        _joinStatus.value = JoinStatus.Loading

        viewModelScope.launch {
            try {
                val query = db.collection("classrooms").whereEqualTo("code", code).get().await()
                if (query.isEmpty) {
                    _joinStatus.value = JoinStatus.Error("Invalid classroom code")
                    return@launch
                }

                val classroomDoc = query.documents[0]
                val classId = classroomDoc.id
                val className = classroomDoc.getString("name") ?: ""

                val existingRequest = db.collection("join_requests")
                    .whereEqualTo("studentId", currentUser.uid)
                    .whereEqualTo("classId", classId)
                    .get().await()

                if (!existingRequest.isEmpty) {
                    val status = existingRequest.documents[0].getString("status")
                    _joinStatus.value = JoinStatus.Error("Request already $status")
                    return@launch
                }

                val request = JoinRequest(
                    studentId = currentUser.uid,
                    studentEmail = currentUser.email ?: "Unknown",
                    classId = classId,
                    className = className,
                    status = "pending"
                )
                
                db.collection("join_requests").add(request).await()
                _joinStatus.value = JoinStatus.Success
            } catch (e: Exception) {
                Log.e("StudentViewModel", "Join failed", e)
                _joinStatus.value = JoinStatus.Error(e.message ?: "Failed to join")
            }
        }
    }

    fun leaveClassroom(classId: String) {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                val snapshot = db.collection("join_requests")
                    .whereEqualTo("studentId", currentUser.uid)
                    .whereEqualTo("classId", classId)
                    .get().await()
                
                for (doc in snapshot.documents) {
                    db.collection("join_requests").document(doc.id).delete().await()
                }
            } catch (e: Exception) {
                Log.e("StudentViewModel", "Leave failed", e)
            }
        }
    }

    fun resetJoinStatus() {
        _joinStatus.value = JoinStatus.Idle
    }
}

sealed class JoinStatus {
    object Idle : JoinStatus()
    object Loading : JoinStatus()
    object Success : JoinStatus()
    data class Error(val message: String) : JoinStatus()
}
