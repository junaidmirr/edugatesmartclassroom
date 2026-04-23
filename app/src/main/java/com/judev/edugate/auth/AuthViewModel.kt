package com.judev.edugate.auth

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance().reference

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState = _authState.asStateFlow()

    private val _userRole = MutableStateFlow<String?>(null)
    val userRole = _userRole.asStateFlow()
    
    private val _profilePicUrl = MutableStateFlow<String?>(null)
    val profilePicUrl = _profilePicUrl.asStateFlow()

    init {
        checkUserStatus {}
    }

    fun checkUserStatus(onResult: (String) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _userRole.value = null
            onResult("login")
        } else {
            viewModelScope.launch {
                try {
                    val doc = db.collection("users").document(currentUser.uid).get().await()
                    if (doc.exists()) {
                        val role = doc.getString("role")
                        val pPic = doc.getString("profilePic")
                        _userRole.value = role
                        _profilePicUrl.value = pPic
                        if (role == "teacher") {
                            onResult("teacher_dashboard")
                        } else {
                            onResult("student_dashboard")
                        }
                    } else {
                        onResult("login")
                    }
                } catch (e: Exception) {
                    onResult("login")
                }
            }
        }
    }

    fun uploadProfilePicture(context: Context, uri: Uri) {
        val currentUser = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                
                // 1. Compress Image
                val inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                val baos = ByteArrayOutputStream()
                originalBitmap.compress(Bitmap.CompressFormat.JPEG, 25, baos) // 25% quality for very low size
                val data = baos.toByteArray()

                // 2. Upload to Storage
                val ref = storage.child("profiles/${currentUser.uid}.jpg")
                ref.putBytes(data).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                // 3. Update Firestore
                db.collection("users").document(currentUser.uid).update("profilePic", downloadUrl).await()
                
                _profilePicUrl.value = downloadUrl
                _authState.value = AuthState.Idle
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Upload failed")
            }
        }
    }

    fun login(email: String, pass: String) {
        _authState.value = AuthState.Loading
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener {
                viewModelScope.launch {
                    try {
                        val doc = db.collection("users").document(it.user!!.uid).get().await()
                        val role = doc.getString("role")
                        val pPic = doc.getString("profilePic")
                        _userRole.value = role
                        _profilePicUrl.value = pPic
                        _authState.value = AuthState.Success(role ?: "student")
                    } catch (e: Exception) {
                        _authState.value = AuthState.Error(e.message ?: "Login failed")
                    }
                }
            }
            .addOnFailureListener {
                _authState.value = AuthState.Error(it.message ?: "Login failed")
            }
    }

    fun signInWithGoogle(idToken: String, onRoleRequired: (String) -> Unit) {
        _authState.value = AuthState.Loading
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                val user = result.user!!
                viewModelScope.launch {
                    try {
                        val doc = db.collection("users").document(user.uid).get().await()
                        if (doc.exists()) {
                            val role = doc.getString("role") ?: "student"
                            val pPic = doc.getString("profilePic")
                            _userRole.value = role
                            _profilePicUrl.value = pPic
                            _authState.value = AuthState.Success(role)
                        } else {
                            _authState.value = AuthState.GoogleRoleSelection(user.email ?: "")
                        }
                    } catch (e: Exception) {
                        _authState.value = AuthState.Error(e.message ?: "Google Sign In failed")
                    }
                }
            }
            .addOnFailureListener {
                _authState.value = AuthState.Error(it.message ?: "Google Sign In failed")
            }
    }

    fun completeGoogleRegistration(role: String) {
        val user = auth.currentUser ?: return
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val userData = mapOf("email" to user.email, "role" to role, "profilePic" to "")
                db.collection("users").document(user.uid).set(userData).await()
                _userRole.value = role
                _authState.value = AuthState.Success(role)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Failed to save user role")
            }
        }
    }

    fun register(email: String, pass: String, role: String) {
        _authState.value = AuthState.Loading
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val user = mapOf("email" to email, "role" to role, "profilePic" to "")
                db.collection("users").document(result.user!!.uid).set(user)
                    .addOnSuccessListener {
                        _userRole.value = role
                        _authState.value = AuthState.Success(role)
                    }
                    .addOnFailureListener {
                        _authState.value = AuthState.Error(it.message ?: "Firestore error")
                    }
            }
            .addOnFailureListener {
                _authState.value = AuthState.Error(it.message ?: "Registration failed")
            }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                _authState.value = AuthState.Loading
                // Delete from Storage
                try { storage.child("profiles/${user.uid}.jpg").delete().await() } catch(e: Exception) {}
                // Delete from Firestore
                db.collection("users").document(user.uid).delete().await()
                // Delete from Auth
                user.delete().await()
                logout(onComplete)
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Re-authentication required to delete account.")
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        auth.signOut()
        _userRole.value = null
        _profilePicUrl.value = null
        _authState.value = AuthState.Idle
        onComplete()
    }
}

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val role: String) : AuthState()
    data class Error(val message: String) : AuthState()
    data class GoogleRoleSelection(val email: String) : AuthState()
}
