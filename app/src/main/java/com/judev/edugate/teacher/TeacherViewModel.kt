package com.judev.edugate.teacher

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.judev.edugate.models.Classroom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class TeacherViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://fipperai.firebasestorage.app")

    private val _classrooms = MutableStateFlow<List<Classroom>>(emptyList())
    val classrooms = _classrooms.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating = _isCreating.asStateFlow()

    init {
        fetchClassrooms()
    }

    private fun fetchClassrooms() {
        val currentUser = auth.currentUser ?: return
        db.collection("classrooms")
            .whereEqualTo("createdBy", currentUser.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data
                        if (data != null) {
                            Classroom(
                                id = doc.id,
                                name = data["name"] as? String ?: "",
                                teacherName = data["teacherName"] as? String ?: "",
                                code = data["code"] as? String ?: "",
                                createdBy = data["createdBy"] as? String ?: "",
                                profileIcon = (data["profileIcon"] as? Long)?.toInt() ?: 0
                            )
                        } else null
                    }
                    _classrooms.value = list
                }
            }
    }

    fun generateClassCode(): String {
        return (100000..999999).random().toString()
    }

    fun createClassroom(name: String, teacherName: String, code: String, profileIcon: Int) {
        val currentUser = auth.currentUser ?: return
        _isCreating.value = true
        
        val classroom = Classroom(
            name = name,
            teacherName = teacherName,
            code = code,
            createdBy = currentUser.uid,
            profileIcon = profileIcon
        )

        viewModelScope.launch {
            try {
                db.collection("classrooms").add(classroom.toMap()).await()
                _isCreating.value = false
            } catch (e: Exception) {
                _isCreating.value = false
            }
        }
    }

    fun deleteClassroom(classId: String) {
        viewModelScope.launch {
            try {
                // 1. Delete all file metadata in Firestore for this class
                val files = db.collection("files").whereEqualTo("classId", classId).get().await()
                for (doc in files.documents) {
                    val fileName = doc.getString("name") ?: ""
                    val type = doc.getString("type") ?: ""
                    // Delete from Storage
                    try {
                        storage.reference.child("classrooms/$classId/$type/$fileName").delete().await()
                    } catch (e: Exception) {
                        Log.e("TeacherViewModel", "Failed to delete storage file", e)
                    }
                    // Delete metadata
                    db.collection("files").document(doc.id).delete().await()
                }

                // 2. Delete the classroom itself
                db.collection("classrooms").document(classId).delete().await()
            } catch (e: Exception) {
                Log.e("TeacherViewModel", "Failed to delete classroom", e)
            }
        }
    }
}