package com.shelfx.checkapplication.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.shelfx.checkapplication.data.database.UserImagesDao
import com.shelfx.checkapplication.data.entity.UserImages
import com.shelfx.checkapplication.utils.EmbeddingPipeline

class UserImagesRepository(
    private val dao: UserImagesDao,
    private val embeddingPipeline: EmbeddingPipeline    // ✅ inject pipeline
) {

//    suspend fun insertUserImages(userImages: UserImages) {
//        dao.insertUserImages(userImages)
//    }

    suspend fun getUserImages(userName: String): UserImages? {
        return dao.getUserImages(userName)
    }

    suspend fun saveUserWithEmbeddings(
        context: Context,
        name: String,
        frontBitmap: Bitmap,
        leftBitmap: Bitmap,
        rightBitmap: Bitmap
    ) {
        Log.d("ViewModel", "Starting to save embeddings for user: $name")
        // ✅ use the pipeline’s generateEmbedding method
//        val frontEmb = embeddingPipeline.generateEmbedding(context, frontBitmap)
//        val leftEmb  = embeddingPipeline.generateEmbedding(context, leftBitmap)
//        val rightEmb = embeddingPipeline.generateEmbedding(context, rightBitmap)

        // Generate embeddings
        Log.d("Repository", "Generating FRONT embedding...")
        val frontEmb = embeddingPipeline.generateEmbedding(context, frontBitmap)
        Log.d("Repository", "Front embedding: ${if (frontEmb != null) "SUCCESS (size: ${frontEmb.size})" else "FAILED (null)"}")

        Log.d("Repository", "Generating LEFT embedding...")
        val leftEmb = embeddingPipeline.generateEmbedding(context, leftBitmap)
        Log.d("Repository", "Left embedding: ${if (leftEmb != null) "SUCCESS (size: ${leftEmb.size})" else "FAILED (null)"}")

        Log.d("Repository", "Generating RIGHT embedding...")
        val rightEmb = embeddingPipeline.generateEmbedding(context, rightBitmap)
        Log.d("Repository", "Right embedding: ${if (rightEmb != null) "SUCCESS (size: ${rightEmb.size})" else "FAILED (null)"}")


        if (frontEmb != null && leftEmb != null && rightEmb != null) {
            Log.d("Repository", "✅ All embeddings generated successfully!")

            val user = UserImages(
                userName = name,
                frontEmbedding = frontEmb,
                leftEmbedding = leftEmb,
                rightEmbedding = rightEmb
            )

            Log.d("Repository", "Inserting into database...")
            try {
                // First, check if the user already exists
                val existingUser = dao.getUserImages(name)
                Log.d("Repository", "Existing user: ${existingUser?.rightEmbedding.toString()}")


                if (existingUser == null) {
                    // --- USER DOES NOT EXIST: INSERT NEW ---
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
                    // --- USER EXISTS: UPDATE RECORD ---
                    Log.d("Repository", "User '$name' found. Updating existing record...")
                    val updatedUser = existingUser.copy(
                        frontEmbedding = frontEmb,
                        leftEmbedding = leftEmb,
                        rightEmbedding = rightEmb
                    )
                    dao.updateUserImages(updatedUser) // Assuming you have an @Update method in your DAO
                    Log.d("Repository", "DATABASE UPDATE SUCCESS! User '$name' embeddings have been updated.")
                }

                // --- VERIFICATION STEP (applies to both insert and update) ---
                val retrievedUser = dao.getUserImages(name)
                if (retrievedUser != null) {
                    Log.d("Repository", "VERIFICATION SUCCESS!")
                    Log.d("Repository", "Retrieved user: ${retrievedUser.userName}")
                    Log.d("Repository", "Front embedding size: ${retrievedUser.frontEmbedding.size}")
                    Log.d("Repository", "Left embedding size: ${retrievedUser.leftEmbedding.size}")
                    Log.d("Repository", "Right embedding size: ${retrievedUser.rightEmbedding.size}")
                } else {
                    Log.e("Repository", "VERIFICATION FAILED! Could not retrieve user after save operation.")
                }
            } catch (e: Exception) {
                Log.e("Repository", "DATABASE SAVE FAILED: ${e.message}", e)
                throw e
            }


        } else {
            Log.e("Repository", "EMBEDDING GENERATION FAILED!")
            Log.e("Repository", "Front: ${frontEmb != null}, Left: ${leftEmb != null}, Right: ${rightEmb != null}")
            throw Exception("Failed to generate embeddings for one or more images")
        }

        Log.d("Repository", "========== EMBEDDING PROCESS COMPLETE ==========")
    }
}
