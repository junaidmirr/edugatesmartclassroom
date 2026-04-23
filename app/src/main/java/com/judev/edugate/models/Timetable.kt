package com.judev.edugate.models

data class Timetable(
    val id: String = "",
    val classId: String = "",
    val creationDate: String = "",
    val fileName: String = "",
    val fileUrl: String = "",
    val schedule: List<DaySchedule> = emptyList()
)

data class DaySchedule(
    val day: String = "",
    val timeRange: String = ""
)