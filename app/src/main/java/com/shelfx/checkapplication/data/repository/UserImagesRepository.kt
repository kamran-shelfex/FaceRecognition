package com.shelfx.checkapplication.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.shelfx.checkapplication.data.database.UserImagesDao
import com.shelfx.checkapplication.data.entity.UserImages
import com.shelfx.checkapplication.utils.EmbeddingPipeline
import com.shelfx.checkapplication.utils.FaceVerifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserImagesRepository(
    private val dao: UserImagesDao,
    private val embeddingPipeline: EmbeddingPipeline,
    private val faceVerifier: FaceVerifier
) {

    suspend fun getUserImages(userName: String): UserImages? {
        return dao.getUserImages(userName)
    }

    suspend fun getAnyUser(): UserImages? {
        return dao.getAnyUser()
    }

    // FIX: Made this function 'suspend' and wrapped database call in withContext(Dispatchers.IO)
    suspend fun deleteUsers() {
        withContext(Dispatchers.IO) {
            try {
                dao.deleteAll()
                Log.d("Repository", "All users deleted successfully")
            } catch (e: Exception) {
                Log.e("Repository", "Error deleting users: $e")
            }
        }
    }

    suspend fun verifyUser(capturedBitmap: Bitmap, userName: String): Boolean {
        Log.d("Repository", "Attempting to verify user: $userName")

        val userWithEmbeddings = dao.getUserImages(userName)
        if (userWithEmbeddings == null) {
            Log.e("Repository", "Verification failed: User '$userName' not found in database.")
            return false
        }

        val storedEmbeddings = listOf(
            userWithEmbeddings.frontEmbedding,
            userWithEmbeddings.leftEmbedding,
            userWithEmbeddings.rightEmbedding
        )

        Log.d("Repository", "images list ${storedEmbeddings}")

        val (isMatch, similarityScore) = faceVerifier.verifyFace(
            capturedBitmap = capturedBitmap,
            storedEmbeddings = storedEmbeddings,
        )

        Log.i(
            "Repository",
            "Verification complete for '$userName'. Match: $isMatch, Similarity Score: $similarityScore"
        )

        return isMatch
    }

    suspend fun saveUserWithEmbeddings(
        context: Context,
        name: String,
        frontBitmap: Bitmap,
        leftBitmap: Bitmap,
        rightBitmap: Bitmap
    ) {
        // Run this heavy operation on the IO thread
        withContext(Dispatchers.IO) {
            Log.d("ViewModel", "Starting to save embeddings for user: $name")

            Log.d("Repository", "Generating FRONT embedding...")
            val frontEmb = embeddingPipeline.generateEmbedding(frontBitmap)
            Log.d("Repository", "Front embedding: ${if (frontEmb != null) "SUCCESS (size: ${frontEmb.size})" else "FAILED (null)"}")

            Log.d("Repository", "Generating LEFT embedding...")
            val leftEmb = embeddingPipeline.generateEmbedding(leftBitmap)
            Log.d("Repository", "Left embedding: ${if (leftEmb != null) "SUCCESS (size: ${leftEmb.size})" else "FAILED (null)"}")

            Log.d("Repository", "Generating RIGHT embedding...")
            val rightEmb = embeddingPipeline.generateEmbedding(rightBitmap)
            Log.d("Repository", "Right embedding: ${if (rightEmb != null) "SUCCESS (size: ${rightEmb.size})" else "FAILED (null)"}")

            if (frontEmb != null && leftEmb != null && rightEmb != null) {
                Log.d("Repository", "✅ All embeddings generated successfully!")

                try {
                    val existingUser = dao.getUserImages(name)
                    Log.d("Repository", "Existing user: ${existingUser?.rightEmbedding.toString()}")

                    if (existingUser == null) {
                        Log.d("Repository", "User '$name' not found. Inserting new record...")
                        val newUser = UserImages(
                            userName = name,
                            frontEmbedding = frontEmb,
                            leftEmbedding = leftEmb,
                            rightEmbedding = rightEmb
                        )
                        val insertedId = dao.insertUserImages(newUser)
                        Log.d("Repository", "DATABASE INSERT SUCCESS! New user Row ID: $insertedId")

                    } else {
                        Log.d("Repository", "User '$name' found. Updating existing record...")
                        val updatedUser = existingUser.copy(
                            frontEmbedding = frontEmb,
                            leftEmbedding = leftEmb,
                            rightEmbedding = rightEmb
                        )
                        dao.updateUserImages(updatedUser)
                        Log.d("Repository", "DATABASE UPDATE SUCCESS! User '$name' embeddings have been updated.")
                    }

                    val retrievedUser = dao.getUserImages(name)
                    if (retrievedUser != null) {
                        Log.d("Repository", "VERIFICATION SUCCESS! Retrieved user: ${retrievedUser.userName}")
                    } else {
                        Log.e("Repository", "VERIFICATION FAILED! Could not retrieve user after save operation.")
                    }
                } catch (e: Exception) {
                    Log.e("Repository", "DATABASE SAVE FAILED: ${e.message}", e)
                    throw e
                }
            } else {
                Log.e("Repository", "EMBEDDING GENERATION FAILED!")
                throw Exception("Failed to generate embeddings for one or more images")
            }

            Log.d("Repository", "========== EMBEDDING PROCESS COMPLETE ==========")
        }
    }
}
