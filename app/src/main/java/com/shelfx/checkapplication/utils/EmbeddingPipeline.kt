package com.shelfx.checkapplication.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.shelfx.checkapplication.ml.EdgeFaceEmbedder

class EmbeddingPipeline(
    private val preprocess: Preprocess,
    private val embedder: EdgeFaceEmbedder
) {

    companion object {
        private const val TAG = "EmbeddingPipeline"
    }

    /**
     * Step 1: Detect + Align face for recognition
     */
    suspend fun preprocessFace(bitmap: Bitmap): Bitmap? {
        return try {
            Log.d(TAG, "Starting face preprocessing…")

            val alignedFace = preprocess.preprocessForRecognition(bitmap)

            if (alignedFace == null) {
                Log.e(TAG, "Face preprocessing failed — no face or alignment error")
            } else {
                Log.d(TAG, "Face preprocessing successful")
            }

            alignedFace

        } catch (e: Exception) {
            Log.e(TAG, "Error during preprocessing: ${e.message}", e)
            null
        }
    }

    /**
     * Step 2: Generate embedding from aligned face
     * This version is used by Repository + FaceVerifier
     */
    suspend fun generateEmbedding(bitmap: Bitmap): FloatArray? {
        return try {

            // Detect & align face
            val alignedFace = preprocess.preprocessForRecognition(bitmap)
            if (alignedFace == null) {
                Log.w(TAG, "No face detected — cannot generate embedding")
                return null
            }

            // Generate embedding
            val emb = embedder.getEmbedding(alignedFace)

            // Safety: re-normalize and validate in case upstream changes model output
            val norm = l2Normalize(emb).also { validated ->
                val sumSquares = validated.fold(0f) { acc, v -> acc + v * v }
                val magnitude = kotlin.math.sqrt(sumSquares)
                if (magnitude < 0.95f || magnitude > 1.05f) {
                    Log.w(TAG, "Embedding magnitude out of bounds: $magnitude")
                }
                val firstVals = validated.take(5).joinToString(", ")
                val uniqueCount = validated.distinct().size
                if (uniqueCount <= 2) {
                    Log.w(TAG, "Embedding appears nearly-constant (unique=$uniqueCount); check input/preprocess")
                }
                Log.d(TAG, "Embedding diag → mag=%.4f unique=%d first=[%s]".format(magnitude, uniqueCount, firstVals))
            }

            Log.d(TAG, "Embedding generated successfully! dims=${emb.size}")
            Log.d(TAG, "Embedding sample: [${emb.take(5).joinToString(", ")}…]")

            norm

        } catch (e: Exception) {
            Log.e(TAG, "Error generating embedding: ${e.message}", e)
            null
        }
    }

    /**
     * Optional detailed embedding (keep for debugging)
     */
    suspend fun generateEmbeddingWithDetails(bitmap: Bitmap): EmbeddingResult? {
        return try {
            val preprocessResult = preprocess.preprocessWithDetails(bitmap)
                ?: return null

            val embedding = embedder.getEmbedding(preprocessResult.alignedFace)
            val normalized = l2Normalize(embedding)

            EmbeddingResult(
                embedding = normalized,
                alignedFace = preprocessResult.alignedFace,
                faceDetectionResult = preprocessResult.faceDetectionResult
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error generating detailed embedding: ${e.message}", e)
            null
        }
    }
}

private fun l2Normalize(vec: FloatArray): FloatArray {
    var sum = 0f
    for (v in vec) sum += v * v
    val norm = kotlin.math.sqrt(sum)
    if (norm <= 1e-6f) return vec
    return FloatArray(vec.size) { i -> vec[i] / norm }
}

data class EmbeddingResult(
    val embedding: FloatArray,
    val alignedFace: Bitmap,
    val faceDetectionResult: FaceDetectionResult
)
