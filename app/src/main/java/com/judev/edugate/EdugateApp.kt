package com.judev.edugate

import android.app.Application
import androidx.core.content.FileProvider
import com.google.firebase.FirebaseApp

class EdugateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}

class EdugateFileProvider : FileProvider()
