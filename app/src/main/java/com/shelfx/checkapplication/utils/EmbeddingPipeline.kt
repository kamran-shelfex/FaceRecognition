// utils/EmbeddingPipeline.kt
package com.shelfx.checkapplication.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.shelfx.checkapplication.ml.EdgeFaceEmbedder

class EmbeddingPipeline(
    private val preprocess: Preprocess,
    private val embedder: EdgeFaceEmbedder
) {
    /**
     * Complete pipeline: bitmap → detect → align → embed
     */
    suspend fun generateEmbedding(context: Context, bitmap: Bitmap): FloatArray? {
        return try {
            // Step 1: Detect and align face
            val alignedFace = preprocess.preprocessForRecognition(bitmap)

            if (alignedFace == null) {
                Log.w(TAG, "Preprocessing failed - no face detected or alignment failed")
                return null
            }

            // Step 2: Generate embedding
            val embedding = embedder.getEmbedding(alignedFace)

            Log.d(TAG, "Embedding generated successfully: ${embedding.size} dimensions")
            Log.d(TAG, "Embedding sample: [${embedding.take(5).joinToString(", ")}...]")

            embedding

        } catch (e: Exception) {
            Log.e(TAG, "Error generating embedding: ${e.message}", e)
            null
        }
    }

    /**
     * Generate embedding with detailed results
     */
    suspend fun generateEmbeddingWithDetails(bitmap: Bitmap): EmbeddingResult? {
        return try {
            val preprocessResult = preprocess.preprocessWithDetails(bitmap)

            if (preprocessResult == null) {
                return null
            }

            val embedding = embedder.getEmbedding(preprocessResult.alignedFace)

            EmbeddingResult(
                embedding = embedding,
                alignedFace = preprocessResult.alignedFace,
                faceDetectionResult = preprocessResult.faceDetectionResult
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error generating embedding: ${e.message}", e)
            null
        }
    }

    companion object {
        private const val TAG = "EmbeddingPipeline"
    }
}

data class EmbeddingResult(
    val embedding: FloatArray,
    val alignedFace: Bitmap,
    val faceDetectionResult: FaceDetectionResult
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmbeddingResult
        return embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        return embedding.contentHashCode()
    }
}