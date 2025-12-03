package com.shelfx.checkapplication.viewmodel

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfx.checkapplication.data.entity.UserImages
import com.shelfx.checkapplication.data.repository.UserImagesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserImagesViewModel(
    private val repository: UserImagesRepository
) : ViewModel() {

    private val _userImages = MutableStateFlow<UserImages?>(null)
    val userImages: StateFlow<UserImages?> = _userImages

    // -------------------------------------------------------
    // Load user from database
    // -------------------------------------------------------
    fun loadUserImages(userName: String) {
        viewModelScope.launch {
            _userImages.value = repository.getUserImages(userName)
        }
    }

    // -------------------------------------------------------
    // Save embeddings (front, left, right)
    // -------------------------------------------------------
    fun saveUserEmbeddings(
        context: Context,
        userName: String,
        frontBitmap: android.graphics.Bitmap,
        leftBitmap: android.graphics.Bitmap,
        rightBitmap: android.graphics.Bitmap
    ) {
        viewModelScope.launch {
            try {
                repository.saveUserWithEmbeddings(
                    context = context,
                    name = userName,
                    frontBitmap = frontBitmap,
                    leftBitmap = leftBitmap,
                    rightBitmap = rightBitmap
                )
            }
            catch (e: Exception){
                Toast.makeText(context, "Error while saving images embeddings",Toast.LENGTH_LONG).show()
                Log.d("Embedding","Error while saving images embeddings")
            }

        }
    }
}



