package com.judev.edugate.models

data class EduFile(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val type: String = "", // "material" or "note"
    val extension: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val size: Long = 0,
    val classId: String = ""
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "url" to url,
            "type" to type,
            "extension" to extension,
            "timestamp" to timestamp,
            "size" to size,
            "classId" to classId
        )
    }
}