package com.judev.edugate.teacher

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.judev.edugate.models.EduFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream

class ClassroomViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    // Explicitly using your storage bucket URL
    private val storage = FirebaseStorage.getInstance("gs://fipperai.firebasestorage.app")

    private val _uploadProgress = MutableStateFlow<Float?>(null)
    val uploadProgress = _uploadProgress.asStateFlow()

    private val _files = MutableStateFlow<List<EduFile>>(emptyList())
    val files = _files.asStateFlow()

    fun fetchFiles(classId: String, type: String) {
        db.collection("files")
            .whereEqualTo("classId", classId)
            .whereEqualTo("type", type)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ClassroomViewModel", "Listen failed.", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    _files.value = snapshot.documents.mapNotNull { doc ->
                        doc.toObject(EduFile::class.java)?.copy(id = doc.id)
                    }
                }
            }
    }

    fun uploadFile(context: Context, uri: Uri, classId: String, type: String) {
        val fileName = getFileName(context, uri) ?: "unknown_file_${System.currentTimeMillis()}"
        val extension = fileName.substringAfterLast(".", "")
        
        // Allowed extensions
        val allowed = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")
        if (!allowed.contains(extension.lowercase())) {
            Log.e("ClassroomViewModel", "File type not allowed: $extension")
            return
        }

        val storageRef = storage.reference.child("classrooms/$classId/$type/$fileName")
        Log.d("ClassroomViewModel", "Uploading to: ${storageRef.path}")
        
        val uploadTask = storageRef.putFile(uri)

        _uploadProgress.value = 0f

        uploadTask.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toFloat()
            _uploadProgress.value = progress / 100f
            Log.d("ClassroomViewModel", "Upload progress: $progress%")
        }.addOnSuccessListener { taskSnapshot ->
            Log.d("ClassroomViewModel", "Upload successful")
            storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                val eduFile = EduFile(
                    name = fileName,
                    url = downloadUrl.toString(),
                    type = type,
                    extension = extension,
                    size = taskSnapshot.metadata?.sizeBytes ?: 0L,
                    classId = classId
                )
                
                viewModelScope.launch {
                    try {
                        db.collection("files").add(eduFile.toMap()).await()
                        saveFileLocally(context, uri, fileName)
                        _uploadProgress.value = null
                    } catch (e: Exception) {
                        Log.e("ClassroomViewModel", "Error saving to Firestore", e)
                        _uploadProgress.value = null
                    }
                }
            }
        }.addOnFailureListener { e ->
            Log.e("ClassroomViewModel", "Upload failed: ${e.message}")
            _uploadProgress.value = null
        }
    }

    fun deleteFile(context: Context, file: EduFile) {
        viewModelScope.launch {
            try {
                // 1. Delete from Firebase Storage
                storage.reference.child("classrooms/${file.classId}/${file.type}/${file.name}").delete().await()
                
                // 2. Delete from Firestore
                db.collection("files").document(file.id).delete().await()
                
                // 3. Delete from Local Storage
                val localFile = File(context.filesDir, file.name)
                if (localFile.exists()) {
                    localFile.delete()
                }
            } catch (e: Exception) {
                Log.e("ClassroomViewModel", "Failed to delete file", e)
            }
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("ClassroomViewModel", "Error getting file name", e)
        }
        return name
    }

    private fun saveFileLocally(context: Context, uri: Uri, fileName: String) {
        try {
            val localFile = File(context.filesDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.e("ClassroomViewModel", "Error saving file locally", e)
        }
    }
    
    fun getLocalFile(context: Context, fileName: String): File? {
        val file = File(context.filesDir, fileName)
        return if (file.exists()) file else null
    }
}