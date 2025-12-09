package com.shelfx.checkapplication.utils

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

        // Stricter similarity threshold to reduce false accepts
        private const val SIMILARITY_THRESHOLD = 0.80f

        // Require at least 2 of 3 embeddings to match
        private const val MIN_MATCHING_EMBEDDINGS = 2

        // Disable adaptive lowering of threshold to keep it strict
        private const val USE_ADAPTIVE_THRESHOLD = false
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
            Log.d(TAG, "════════════════════════════════════════════════════════")
            Log.d(TAG, "🔍 STARTING FACE VERIFICATION")
            Log.d(TAG, "════════════════════════════════════════════════════════")
            Log.d(TAG, "Input bitmap: ${capturedBitmap.width}x${capturedBitmap.height}")
            Log.d(TAG, "Stored embeddings count: ${storedEmbeddings.size}")

            // ========== STEP 1: Validate Stored Embeddings ==========
            if (storedEmbeddings.isEmpty()) {
                Log.e(TAG, "❌ CRITICAL: No stored embeddings to compare against")
                return Pair(false, 0f)
            }

            // Type and content validation for stored embeddings
            Log.d(TAG, "")
            Log.d(TAG, "📦 VALIDATING STORED EMBEDDINGS:")
            Log.d(TAG, "─────────────────────────────────────────────────────────")
            storedEmbeddings.forEachIndexed { index, embedding ->
                Log.d(TAG, "Embedding #$index:")
                Log.d(TAG, "  ├─ Type: ${embedding::class.java.simpleName}")
                Log.d(TAG, "  ├─ Is FloatArray: ${embedding is FloatArray}")
                Log.d(TAG, "  ├─ Size: ${embedding.size}")

                if (embedding.isEmpty()) {
                    Log.e(TAG, "  └─ ❌ EMPTY ARRAY!")
                } else {
                    Log.d(TAG, "  ├─ First 5 values: [${embedding.take(5).joinToString(", ") { "%.4f".format(it) }}]")
                    Log.d(TAG, "  ├─ All zeros? ${embedding.all { it == 0f }}")
                    Log.d(TAG, "  ├─ All same value? ${embedding.all { it == embedding[0] }}")

                    val magnitude = calculateMagnitude(embedding)
                    val isNormalized = kotlin.math.abs(magnitude - 1.0f) < 0.01f
                    Log.d(TAG, "  ├─ Magnitude: %.4f".format(magnitude))
                    Log.d(TAG, "  ├─ Normalized? ${if (isNormalized) "✓ YES" else "✗ NO (should be ~1.0)"}")
                    Log.d(TAG, "  ├─ Min value: %.4f".format(embedding.minOrNull() ?: 0f))
                    Log.d(TAG, "  ├─ Max value: %.4f".format(embedding.maxOrNull() ?: 0f))
                    Log.d(TAG, "  └─ Average: %.4f".format(embedding.average()))
                }
            }

            // Check for dimension consistency
            val sizes = storedEmbeddings.map { it.size }.distinct()
            if (sizes.size > 1) {
                Log.e(TAG, "❌ CRITICAL: Inconsistent embedding sizes: $sizes")
                return Pair(false, 0f)
            }

            // ========== STEP 2: Generate Captured Embedding ==========
            Log.d(TAG, "")
            Log.d(TAG, "📸 GENERATING CAPTURED EMBEDDING:")
            Log.d(TAG, "─────────────────────────────────────────────────────────")

            val capturedEmbedding = embeddingPipeline.generateEmbedding(capturedBitmap)




            if (capturedEmbedding == null) {
                Log.e(TAG, "❌ Failed to generate embedding (no face detected or processing failed)")
                return Pair(false, 0f)
            }

            // After generating capturedEmbedding
            Log.d(TAG, "🔍 EMBEDDING DIAGNOSIS:")
            Log.d(TAG, "First 10 values: ${capturedEmbedding.take(10).joinToString(", ") { "%.6f".format(it) }}")
            Log.d(TAG, "Last 10 values: ${capturedEmbedding.takeLast(10).joinToString(", ") { "%.6f".format(it) }}")
            Log.d(TAG, "Unique values count: ${capturedEmbedding.distinct().size} / ${capturedEmbedding.size}")
//            Log.d(TAG, "Standard deviation: %.6f".format(capturedEmbedding.standardDeviation()))

// Compare raw embeddings BEFORE normalization
            storedEmbeddings.forEachIndexed { index, stored ->
                val rawSimilarity = calculateCosineSimilarity(capturedEmbedding, stored)
                Log.d(TAG, "RAW similarity (before norm) #$index: %.6f".format(rawSimilarity))
            }

            Log.d(TAG, "🔍 STORED EMBEDDINGS DIAGNOSIS:")
            storedEmbeddings.forEachIndexed { i, embedding ->
                Log.d(TAG, "Stored #$i - First 10: ${embedding.take(10).joinToString(", ") { "%.6f".format(it) }}")
                Log.d(TAG, "Stored #$i - Unique count: ${embedding.distinct().size}")
            }

// Compare stored embeddings with each other
            if (storedEmbeddings.size > 1) {
                Log.d(TAG, "")
                Log.d(TAG, "🔄 COMPARING STORED EMBEDDINGS WITH EACH OTHER:")
                for (i in 0 until storedEmbeddings.size - 1) {
                    for (j in i + 1 until storedEmbeddings.size) {
                        val sim = calculateCosineSimilarity(storedEmbeddings[i], storedEmbeddings[j])
                        Log.d(TAG, "Stored #$i vs Stored #$j: %.6f".format(sim))
                        if (sim > 0.99f) {
                            Log.e(TAG, "❌ WARNING: Stored embeddings #$i and #$j are nearly identical!")
                        }
                    }
                }
            }
            Log.d(TAG, "✓ Captured embedding generated successfully")
            Log.d(TAG, "  ├─ Type: ${capturedEmbedding::class.java.simpleName}")
            Log.d(TAG, "  ├─ Size: ${capturedEmbedding.size}")
            Log.d(TAG, "  ├─ First 5 values: [${capturedEmbedding.take(5).joinToString(", ") { "%.4f".format(it) }}]")
            Log.d(TAG, "  ├─ All zeros? ${capturedEmbedding.all { it == 0f }}")

            val capturedMagnitude = calculateMagnitude(capturedEmbedding)
            val capturedIsNormalized = kotlin.math.abs(capturedMagnitude - 1.0f) < 0.01f
            Log.d(TAG, "  ├─ Magnitude: %.4f".format(capturedMagnitude))
            Log.d(TAG, "  ├─ Normalized? ${if (capturedIsNormalized) "✓ YES" else "✗ NO (should be ~1.0)"}")
            Log.d(TAG, "  ├─ Min value: %.4f".format(capturedEmbedding.minOrNull() ?: 0f))
            Log.d(TAG, "  ├─ Max value: %.4f".format(capturedEmbedding.maxOrNull() ?: 0f))
            Log.d(TAG, "  └─ Average: %.4f".format(capturedEmbedding.average()))

            // ========== STEP 3: Validate Dimensions ==========
            val expectedSize = storedEmbeddings.first().size
            if (capturedEmbedding.size != expectedSize) {
                Log.e(TAG, "")
                Log.e(TAG, "❌ CRITICAL: Embedding size mismatch!")
                Log.e(TAG, "   Captured: ${capturedEmbedding.size}")
                Log.e(TAG, "   Expected: $expectedSize")
                return Pair(false, 0f)
            }

            // ========== STEP 4: Normalize Embeddings if Needed ==========
            Log.d(TAG, "")
            Log.d(TAG, "🔧 NORMALIZATION CHECK:")
            Log.d(TAG, "─────────────────────────────────────────────────────────")

            val normalizedCaptured = if (!capturedIsNormalized) {
                Log.w(TAG, "⚠️ Captured embedding not normalized, normalizing now...")
                l2Normalize(capturedEmbedding).also {
                    Log.d(TAG, "   New magnitude: %.4f".format(calculateMagnitude(it)))
                }
            } else {
                Log.d(TAG, "✓ Captured embedding already normalized")
                capturedEmbedding
            }

            val normalizedStored = storedEmbeddings.mapIndexed { index, embedding ->
                val magnitude = calculateMagnitude(embedding)
                val isNorm = kotlin.math.abs(magnitude - 1.0f) < 0.01f
                if (!isNorm) {
                    Log.w(TAG, "⚠️ Stored embedding #$index not normalized (mag: %.4f), normalizing...".format(magnitude))
                    l2Normalize(embedding)
                } else {
                    Log.d(TAG, "✓ Stored embedding #$index already normalized")
                    embedding
                }
            }

            // ========== STEP 5: Calculate Similarities ==========
            Log.d(TAG, "")
            Log.d(TAG, "🎯 CALCULATING COSINE SIMILARITIES:")
            Log.d(TAG, "─────────────────────────────────────────────────────────")

            val similarities = normalizedStored.mapIndexed { index, storedEmbedding ->
                val similarity = calculateCosineSimilarity(normalizedCaptured, storedEmbedding)

                Log.d(TAG, "Embedding #$index:")
                Log.d(TAG, "  ├─ Similarity: %.6f".format(similarity))
                Log.d(TAG, "  ├─ Meets threshold (%.2f)? ${if (similarity >= SIMILARITY_THRESHOLD) "✓ YES" else "✗ NO"}".format(SIMILARITY_THRESHOLD))

                // Additional diagnostic - dot product and norms
                val dotProduct = normalizedCaptured.indices.sumOf {
                    (normalizedCaptured[it] * storedEmbedding[it]).toDouble()
                }.toFloat()
                Log.d(TAG, "  ├─ Dot product: %.6f".format(dotProduct))
                Log.d(TAG, "  ├─ Captured norm: %.6f".format(calculateMagnitude(normalizedCaptured)))
                Log.d(TAG, "  └─ Stored norm: %.6f".format(calculateMagnitude(storedEmbedding)))

                similarity
            }

            // ========== STEP 6: Calculate Statistics ==========
            Log.d(TAG, "")
            Log.d(TAG, "📊 SIMILARITY STATISTICS:")
            Log.d(TAG, "─────────────────────────────────────────────────────────")

            val maxSimilarity = similarities.maxOrNull() ?: 0f
            val minSimilarity = similarities.minOrNull() ?: 0f
            val avgSimilarity = similarities.average().toFloat()
            val matchingCount = similarities.count { it >= SIMILARITY_THRESHOLD }

            Log.d(TAG, "Max similarity:       %.6f".format(maxSimilarity))
            Log.d(TAG, "Min similarity:       %.6f".format(minSimilarity))
            Log.d(TAG, "Avg similarity:       %.6f".format(avgSimilarity))
            Log.d(TAG, "Matching embeddings:  $matchingCount/${storedEmbeddings.size}")
            Log.d(TAG, "Base threshold:       $SIMILARITY_THRESHOLD")

            // Distribution analysis
            val ranges = listOf(
                "[-1.0, -0.5)" to similarities.count { it >= -1.0f && it < -0.5f },
                "[-0.5, 0.0)" to similarities.count { it >= -0.5f && it < 0f },
                "[0.0, 0.3)" to similarities.count { it >= 0f && it < 0.3f },
                "[0.3, 0.5)" to similarities.count { it >= 0.3f && it < 0.5f },
                "[0.5, 0.7)" to similarities.count { it >= 0.5f && it < 0.7f },
                "[0.7, 0.9)" to similarities.count { it >= 0.7f && it < 0.9f },
                "[0.9, 1.0]" to similarities.count { it >= 0.9f && it <= 1.0f }
            )
            Log.d(TAG, "")
            Log.d(TAG, "Distribution:")
            ranges.forEach { (range, count) ->
                if (count > 0) {
                    Log.d(TAG, "  $range: $count")
                }
            }

            // ========== STEP 7: Determine Match ==========
            Log.d(TAG, "")
            Log.d(TAG, "🔐 MATCH DETERMINATION:")
            Log.d(TAG, "─────────────────────────────────────────────────────────")

            val isMatch = determineMatch(
                maxSimilarity = maxSimilarity,
                avgSimilarity = avgSimilarity,
                matchingCount = matchingCount,
                totalCount = storedEmbeddings.size
            )

            // ========== FINAL RESULT ==========
            Log.d(TAG, "")
            Log.d(TAG, "════════════════════════════════════════════════════════")
            if (isMatch) {
                Log.i(TAG, "✅ VERIFICATION RESULT: MATCH")
                Log.i(TAG, "   Similarity Score: %.6f".format(maxSimilarity))
            } else {
                Log.w(TAG, "❌ VERIFICATION RESULT: NO MATCH")
                Log.w(TAG, "   Max Similarity: %.6f (threshold: %.2f)".format(maxSimilarity, SIMILARITY_THRESHOLD))
                Log.w(TAG, "   Gap: %.6f".format(SIMILARITY_THRESHOLD - maxSimilarity))
            }
            Log.d(TAG, "════════════════════════════════════════════════════════")

            return Pair(isMatch, maxSimilarity)

        } catch (e: Exception) {
            Log.e(TAG, "")
            Log.e(TAG, "════════════════════════════════════════════════════════")
            Log.e(TAG, "💥 EXCEPTION DURING FACE VERIFICATION")
            Log.e(TAG, "════════════════════════════════════════════════════════")
            Log.e(TAG, "Exception type: ${e::class.java.simpleName}")
            Log.e(TAG, "Message: ${e.message}")
            Log.e(TAG, "Stack trace:")
            e.printStackTrace()
            Log.e(TAG, "════════════════════════════════════════════════════════")
            return Pair(false, 0f)
        }
    }

    /**
     * Determine if faces match based on similarity scores
     */
    private fun determineMatch(
        maxSimilarity: Float,
        avgSimilarity: Float,
        matchingCount: Int,
        totalCount: Int
    ): Boolean {
        // Strategy 1: Strict - require multiple embeddings to match
        if (MIN_MATCHING_EMBEDDINGS > 1 && totalCount > 1) {
            val meetsMinMatches = matchingCount >= MIN_MATCHING_EMBEDDINGS
            Log.d(TAG, "Strategy: Multi-match (require $MIN_MATCHING_EMBEDDINGS matches)")
            Log.d(TAG, "Result: $meetsMinMatches")
            return meetsMinMatches
        }

        // Strategy 2: Adaptive threshold based on confidence
        if (USE_ADAPTIVE_THRESHOLD && totalCount > 1) {
            // If average similarity is also high, we can be more lenient
            val confidenceBoost = if (avgSimilarity > SIMILARITY_THRESHOLD - 0.1f) 0.05f else 0f
            val adaptiveThreshold = SIMILARITY_THRESHOLD - confidenceBoost
            Log.d(TAG, "Strategy: Adaptive threshold")
            Log.d(TAG, "  ├─ Base threshold: $SIMILARITY_THRESHOLD")
            Log.d(TAG, "  ├─ Confidence boost: $confidenceBoost")
            Log.d(TAG, "  ├─ Adaptive threshold: $adaptiveThreshold")
            Log.d(TAG, "  └─ Result: ${maxSimilarity >= adaptiveThreshold}")
            return maxSimilarity >= adaptiveThreshold
        }

        // Strategy 3: Default - single best match
        Log.d(TAG, "Strategy: Single best match")
        Log.d(TAG, "  ├─ Max similarity: $maxSimilarity")
        Log.d(TAG, "  ├─ Threshold: $SIMILARITY_THRESHOLD")
        Log.d(TAG, "  └─ Result: ${maxSimilarity >= SIMILARITY_THRESHOLD}")
        return maxSimilarity >= SIMILARITY_THRESHOLD
    }

    /**
     * Calculate cosine similarity between two embeddings
     */
    private fun calculateCosineSimilarity(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float {
        require(embedding1.size == embedding2.size) {
            "Embeddings must have the same size: ${embedding1.size} vs ${embedding2.size}"
        }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        // Single pass computation for efficiency
        for (i in embedding1.indices) {
            val a = embedding1[i]
            val b = embedding2[i]
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }

        // Calculate magnitudes
        normA = sqrt(normA)
        normB = sqrt(normB)

        // Handle zero magnitude vectors
        if (normA < 1e-10f || normB < 1e-10f) {
            Log.w(TAG, "⚠️ Zero or near-zero magnitude vector detected in similarity calculation")
            Log.w(TAG, "   normA: $normA, normB: $normB")
            return 0f
        }

        // Return raw cosine similarity in [-1, 1] range
        val similarity = dotProduct / (normA * normB)

        // Clamp to handle floating point errors
        return similarity.coerceIn(-1f, 1f)
    }


    /**
     * L2 normalize an embedding vector
     * Makes the vector unit length (magnitude = 1.0)
     */
    private fun l2Normalize(embedding: FloatArray): FloatArray {

        Log.d(TAG, "L2 normalizing embedding...")

        var sumSquares = 0f
        for (value in embedding) {
            sumSquares += value * value
        }

        val magnitude = sqrt(sumSquares)

        // Avoid division by zero
        if (magnitude < 1e-10f) {
            Log.w(TAG, "⚠️ Near-zero magnitude vector detected during normalization: $magnitude")
            return embedding
        }

        return FloatArray(embedding.size) { i ->
            embedding[i] / magnitude
        }
    }

    /**
     * Calculate the magnitude (L2 norm) of a vector
     */
    private fun calculateMagnitude(embedding: FloatArray): Float {
        var sumSquares = 0f
        for (value in embedding) {
            sumSquares += value * value
        }
        return sqrt(sumSquares)
    }

    /**
     * Calculate Euclidean (L2) distance between embeddings
     * Lower distance = more similar
     */
    fun calculateEuclideanDistance(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float {
        require(embedding1.size == embedding2.size) {
            "Embeddings must have the same size: ${embedding1.size} vs ${embedding2.size}"
        }

        var sumSquaredDiff = 0f
        for (i in embedding1.indices) {
            val diff = embedding1[i] - embedding2[i]
            sumSquaredDiff += diff * diff
        }

        return sqrt(sumSquaredDiff)
    }

    /**
     * Verify face using multiple metrics for higher confidence
     * Returns match only if both cosine similarity AND euclidean distance agree
     */
    suspend fun verifyFaceWithMultipleMetrics(
        capturedBitmap: Bitmap,
        storedEmbeddings: List<FloatArray>,
        euclideanThreshold: Float = 0.8f
    ): Pair<Boolean, Map<String, Float>> {
        try {
            if (storedEmbeddings.isEmpty()) {
                return Pair(false, emptyMap())
            }

            val capturedEmbedding = embeddingPipeline.generateEmbedding(capturedBitmap)
                ?: return Pair(false, emptyMap())

            val maxCosineSim = storedEmbeddings.maxOf { stored ->
                calculateCosineSimilarity(capturedEmbedding, stored)
            }

            val minEuclideanDist = storedEmbeddings.minOf { stored ->
                calculateEuclideanDistance(capturedEmbedding, stored)
            }

            val cosineMatch = maxCosineSim >= SIMILARITY_THRESHOLD
            val euclideanMatch = minEuclideanDist <= euclideanThreshold
            val bothMatch = cosineMatch && euclideanMatch

            val metrics = mapOf(
                "maxCosineSimilarity" to maxCosineSim,
                "minEuclideanDistance" to minEuclideanDist,
                "cosineMatch" to if (cosineMatch) 1f else 0f,
                "euclideanMatch" to if (euclideanMatch) 1f else 0f
            )

            Log.d(TAG, "")
            Log.d(TAG, "🎯 MULTI-METRIC VERIFICATION:")
            Log.d(TAG, "─────────────────────────────────────────────────────────")
            Log.d(TAG, "Cosine similarity: $maxCosineSim (match: $cosineMatch)")
            Log.d(TAG, "Euclidean distance: $minEuclideanDist (match: $euclideanMatch)")
            Log.d(TAG, "Both agree: $bothMatch")

            return Pair(bothMatch, metrics)

        } catch (e: Exception) {
            Log.e(TAG, "Error in multi-metric verification: ${e.message}", e)
            return Pair(false, emptyMap())
        }
    }
}