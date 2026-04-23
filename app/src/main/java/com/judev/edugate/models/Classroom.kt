package com.judev.edugate.models

data class Classroom(
    val id: String = "",
    val name: String = "",
    val teacherName: String = "",
    val code: String = "",
    val createdBy: String = "",
    val profileIcon: Int = 0 // Using an index for simple icon selection for now
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "teacherName" to teacherName,
            "code" to code,
            "createdBy" to createdBy,
            "profileIcon" to profileIcon
        )
    }
}