package com.judev.edugate.ui.theme

import android.app.Application
import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel

class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val sharedPreferences = application.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
    
    // Initial state loaded from SharedPreferences
    // -1 = System Default, 0 = Light, 1 = Dark
    private val _isDarkMode = mutableStateOf<Boolean?>(
        when (sharedPreferences.getInt("theme_mode", -1)) {
            0 -> false
            1 -> true
            else -> null
        }
    )
    val isDarkMode: State<Boolean?> = _isDarkMode

    fun toggleTheme(isDark: Boolean?) {
        _isDarkMode.value = isDark
        val modeValue = when (isDark) {
            false -> 0
            true -> 1
            else -> -1
        }
        sharedPreferences.edit().putInt("theme_mode", modeValue).apply()
    }
}
