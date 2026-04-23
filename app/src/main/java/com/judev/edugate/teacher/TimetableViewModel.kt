package com.judev.edugate.teacher

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.judev.edugate.models.DaySchedule
import com.judev.edugate.models.Timetable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

class TimetableViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance("gs://fipperai.firebasestorage.app")

    private val _timetables = MutableStateFlow<List<Timetable>>(emptyList())
    val timetables = _timetables.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    fun fetchTimetables(classId: String) {
        db.collection("timetables")
            .whereEqualTo("classId", classId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    _timetables.value = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        Timetable(
                            id = doc.id,
                            classId = data["classId"] as? String ?: "",
                            creationDate = data["creationDate"] as? String ?: "",
                            fileName = data["fileName"] as? String ?: "",
                            fileUrl = data["fileUrl"] as? String ?: "",
                            schedule = (data["schedule"] as? List<Map<String, String>>)?.map {
                                DaySchedule(it["day"] ?: "", it["timeRange"] ?: "")
                            } ?: emptyList()
                        )
                    }
                }
            }
    }

    fun createTimetable(context: Context, classId: String, date: String, schedules: List<DaySchedule>) {
        viewModelScope.launch {
            _isGenerating.value = true
            try {
                val fileName = "Timetable_${System.currentTimeMillis()}.xlsx"
                val localFile = File(context.filesDir, fileName)

                // 1. Create Excel Workbook
                val workbook = XSSFWorkbook()
                val sheet = workbook.createSheet("Timetable")
                
                val headerRow = sheet.createRow(0)
                headerRow.createCell(0).setCellValue("Day")
                headerRow.createCell(1).setCellValue("Time Range")

                schedules.forEachIndexed { index, schedule ->
                    val row = sheet.createRow(index + 1)
                    row.createCell(0).setCellValue(schedule.day)
                    row.createCell(1).setCellValue(schedule.timeRange)
                }

                FileOutputStream(localFile).use { workbook.write(it) }
                workbook.close()

                // 2. Upload to Firebase Storage
                val storageRef = storage.reference.child("classrooms/$classId/timetables/$fileName")
                storageRef.putFile(Uri.fromFile(localFile)).await()
                val downloadUrl = storageRef.downloadUrl.await()

                // 3. Save to Firestore
                val timetable = mapOf(
                    "classId" to classId,
                    "creationDate" to date,
                    "fileName" to fileName,
                    "fileUrl" to downloadUrl.toString(),
                    "schedule" to schedules.map { mapOf("day" to it.day, "timeRange" to it.timeRange) }
                )
                db.collection("timetables").add(timetable).await()
                
                _isGenerating.value = false
            } catch (e: Exception) {
                Log.e("TimetableViewModel", "Error creating timetable", e)
                _isGenerating.value = false
            }
        }
    }

    fun deleteTimetable(context: Context, timetable: Timetable) {
        viewModelScope.launch {
            try {
                storage.reference.child("classrooms/${timetable.classId}/timetables/${timetable.fileName}").delete().await()
                db.collection("timetables").document(timetable.id).delete().await()
                val localFile = File(context.filesDir, timetable.fileName)
                if (localFile.exists()) localFile.delete()
            } catch (e: Exception) {
                Log.e("TimetableViewModel", "Error deleting timetable", e)
            }
        }
    }
}