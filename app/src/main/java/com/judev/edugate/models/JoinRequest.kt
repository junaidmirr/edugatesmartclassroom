package com.judev.edugate.models

data class JoinRequest(
    val id: String = "",
    val studentId: String = "",
    val studentEmail: String = "",
    val classId: String = "",
    val className: String = "",
    val status: String = "pending", // "pending", "approved", "rejected"
    val timestamp: Long = System.currentTimeMillis()
)