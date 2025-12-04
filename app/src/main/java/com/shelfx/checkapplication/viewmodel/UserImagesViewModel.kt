package com.shelfx.checkapplication.viewmodel

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfx.checkapplication.data.entity.UserImages
import com.shelfx.checkapplication.data.repository.UserImagesRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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

    /**
     * Checks if there are any users in the database.
     * @return True if at least one user exists, false otherwise.
     */
    suspend fun hasAnyUsers(): Boolean {
        return viewModelScope.async(Dispatchers.IO) {
            repository.getAnyUser() != null // Assumes you have a method to check for any user
        }.await()
    }

    /**
     * Verifies a user's face against their stored embeddings during login.
     * NOTE: You must implement the actual similarity matching logic in your repository.
     *
     * @param liveBitmap The new image captured from the camera for verification.
     * @param userName The name of the user trying to log in.
     * @return True if the face is a match, false otherwise.
     */
    suspend fun verifyUser(liveBitmap: android.graphics.Bitmap, userName: String): Boolean {
        return viewModelScope.async(Dispatchers.IO) {
            // --- THIS IS A PLACEHOLDER FOR YOUR CORE VERIFICATION LOGIC ---
            // 1. Get the stored embeddings for the provided 'userName'.
            // 2. Generate new embeddings for the 'liveBitmap'.
            // 3. Compare the new embeddings with the stored ones.
            // 4. Return true if similarity is above your threshold, otherwise return false.

            Log.d("ViewModel", "Simulating verification for '$userName'. Implement real logic.")
            // Example of what real logic might look like:
            // val isMatch = repository.verifyFace(liveBitmap, userName)
            // return@async isMatch
            true // Returning true for now to allow UI flow testing.
        }.await()
    }



    // -------------------------------------------------------
    // Save embeddings (front, left, right)
    // -------------------------------------------------------

    suspend fun saveUserEmbeddings(
        // The context parameter should be removed if you followed previous advice
        context: Context,
        userName: String,
        frontBitmap: android.graphics.Bitmap,
        leftBitmap: android.graphics.Bitmap,
        rightBitmap: android.graphics.Bitmap
    ): Boolean { // <-- 1. Explicitly returns Boolean
        // 2. Use async to run the task in the background and get a result
        val saveJob: Deferred<Boolean> = viewModelScope.async(Dispatchers.IO) {
            try {
                repository.saveUserWithEmbeddings(
                    context = context, // Or getApplication() if you refactored
                    name = userName,
                    frontBitmap = frontBitmap,
                    leftBitmap = leftBitmap,
                    rightBitmap = rightBitmap
                )
                return@async true // <-- 3. Return true if the try block succeeds
            } catch (e: Exception) {
                Log.e("UserImagesViewModel", "Error in saveUserWithEmbeddings: ${e.message}", e)
                return@async false // <-- 4. Return false if an exception is caught
            }
        }
        // 5. await() the result from the async block and return it
        return saveJob.await()
    }
}



