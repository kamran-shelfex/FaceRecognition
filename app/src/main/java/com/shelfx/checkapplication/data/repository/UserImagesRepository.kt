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

    suspend fun insertUserImages(userImages: UserImages) {
        dao.insertUserImages(userImages)
    }

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
        val frontEmb = embeddingPipeline.generateEmbedding(context, frontBitmap)
        val leftEmb  = embeddingPipeline.generateEmbedding(context, leftBitmap)
        val rightEmb = embeddingPipeline.generateEmbedding(context, rightBitmap)

        if (frontEmb != null && leftEmb != null && rightEmb != null) {
            Log.d("Repository", "All embeddings generated successfully!")
            val user = UserImages(
                userName = name,
                frontEmbedding = frontEmb,
                leftEmbedding = leftEmb,
                rightEmbedding = rightEmb
            )
            dao.insertUserImages(user)
        }
    }
}
