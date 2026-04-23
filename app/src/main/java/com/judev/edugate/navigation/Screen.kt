package com.judev.edugate.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object TeacherDashboard : Screen("teacher_dashboard")
    object StudentDashboard : Screen("student_dashboard")
    object ClassroomDetails : Screen("classroom_details/{classId}") {
        fun createRoute(classId: String) = "classroom_details/$classId"
    }
    object JoinRequests : Screen("join_requests")
    object SmartBoard : Screen("smart_board/{classId}") {
        fun createRoute(classId: String) = "smart_board/$classId"
    }
    object LiveStream : Screen("live_stream/{classId}/{role}/{type}") {
        fun createRoute(classId: String, role: String, type: String) = "live_stream/$classId/$role/$type"
    }
    object Profile : Screen("profile")
}
