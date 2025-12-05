package com.shelfx.checkapplication.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlin.math.sqrt

/**
 * Utility class for face verification using cosine similarity
 * Location: app/src/main/java/com/shelfx/checkapplication/utils/FaceVerifier.kt
 */
class FaceVerifier(
    private val embeddingPipeline: EmbeddingPipeline
) {

    companion object {
        private const val TAG = "FaceVerifier"

        // Threshold for cosine similarity (adjust based on your model)
        // Typical values: 0.4 to 0.6 (higher = stricter matching)
        private const val SIMILARITY_THRESHOLD = 0.5f
    }

    /**
     * Verify if the captured face matches the stored embeddings
     *
     * @param capturedBitmap The bitmap from camera during login
     * @param storedEmbeddings List of stored embeddings for the user (front, left, right)
     * @return Pair<Boolean, Float> - (isMatch, similarity score)
     */
    suspend fun verifyFace(
        capturedBitmap: Bitmap,
        storedEmbeddings: List<FloatArray>
    ): Pair<Boolean, Float> {
        try {
            Log.d(TAG, "Starting face verification...")

            // Step 1: Detect and align face in captured image
            val alignedFace = embeddingPipeline.preprocessFace(capturedBitmap)

            if (alignedFace == null) {
                Log.e(TAG, "No face detected in captured image")
                return Pair(false, 0f)
            }

            Log.d(TAG, "Face detected and aligned successfully")

            // Step 2: Generate embedding for captured face
            val capturedEmbedding = embeddingPipeline.generateEmbedding( alignedFace)

            if (capturedEmbedding == null) {
                Log.e(TAG, "Failed to generate embedding for captured face")
                return Pair(false, 0f)
            }

            Log.d(TAG, "Captured embedding generated: size=${capturedEmbedding.size}")

            // Step 3: Calculate cosine similarity with each stored embedding
            val similarities = storedEmbeddings.mapIndexed { index, storedEmbedding ->
                val similarity = calculateCosineSimilarity(capturedEmbedding, storedEmbedding)
                Log.d(TAG, "Similarity with embedding $index: $similarity")
                similarity
            }

            // Step 4: Use the maximum similarity score
            val maxSimilarity = similarities.maxOrNull() ?: 0f

            Log.d(TAG, "Max similarity: $maxSimilarity, Threshold: $SIMILARITY_THRESHOLD")

            // Step 5: Compare with threshold
            val isMatch = maxSimilarity >= SIMILARITY_THRESHOLD

            Log.d(TAG, "Verification result: ${if (isMatch) "MATCH" else "NO MATCH"}")

            return Pair(isMatch, maxSimilarity)

        } catch (e: Exception) {
            Log.e(TAG, "Error during face verification: ${e.message}", e)
            return Pair(false, 0f)
        }
    }

    /**
     * Calculate cosine similarity between two embeddings
     *
     * Formula: cos(θ) = (A · B) / (||A|| * ||B||)
     * Range: -1 to 1 (1 = identical, 0 = orthogonal, -1 = opposite)
     *
     * @param embedding1 First embedding vector
     * @param embedding2 Second embedding vector
     * @return Cosine similarity score (0 to 1 for normalized embeddings)
     */
    fun calculateCosineSimilarity(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float {
        require(embedding1.size == embedding2.size) {
            "Embeddings must have the same size: ${embedding1.size} vs ${embedding2.size}"
        }

        // Calculate dot product (A · B)
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            normA += embedding1[i] * embedding1[i]
            normB += embedding2[i] * embedding2[i]
        }

        // Calculate magnitudes (||A|| and ||B||)
        normA = sqrt(normA)
        normB = sqrt(normB)

        // Avoid division by zero
        if (normA == 0f || normB == 0f) {
            Log.w(TAG, "Zero magnitude vector detected")
            return 0f
        }

        // Calculate cosine similarity
        val similarity = dotProduct / (normA * normB)

        // Clamp to [0, 1] range (some embeddings might be slightly outside due to floating point)
        return similarity.coerceIn(0f, 1f)
    }

    /**
     * Alternative: Euclidean distance (L2 distance)
     * Lower distance = more similar
     * Typical threshold: 0.6 to 1.0
     */
    fun calculateEuclideanDistance(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float {
        require(embedding1.size == embedding2.size) {
            "Embeddings must have the same size"
        }

        var sumSquaredDiff = 0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sumSquaredDiff += diff * diff
        }

        return sqrt(sumSquaredDiff)
    }
}