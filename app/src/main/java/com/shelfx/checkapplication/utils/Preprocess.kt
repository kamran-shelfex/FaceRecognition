// utils/Preprocess.kt
package com.shelfx.checkapplication.utils

import android.graphics.Bitmap
import android.util.Log

class Preprocess(
    private val faceDetector: FaceDetector,
    private val faceAlign: FaceAlign
) {
    /**
     * Complete preprocessing pipeline: detect → align → return
     */
    suspend fun preprocessForRecognition(bitmap: Bitmap): Bitmap? {
        return try {
            // Detect largest face
            val faceResult = faceDetector.detectLargestFace(bitmap)



            if (faceResult == null) {
                Log.w("Face result", "No face detected in image")
                return null
            }

            Log.d("Face result success", "Face detected at: ${faceResult.boundingBox}")

            // Align face
            val alignedFace = faceAlign.alignFaceWithLandmarks(faceResult = faceResult, bitmap = bitmap)

            if (alignedFace == null) {
                Log.w("Face alignment", "Face alignment failed")
                return null
            }

            Log.d(TAG, "Face aligned successfully: ${alignedFace.width}x${alignedFace.height}")
            alignedFace

        } catch (e: Exception) {
            Log.e("Face result error", "Error in preprocessing: ${e.message}")
            null
        }
    }

    /**
     * Preprocess and return both aligned face and detection result
     */
    suspend fun preprocessWithDetails(bitmap: Bitmap): PreprocessResult? {
        return try {
            val faceResult = faceDetector.detectLargestFace(bitmap)

            if (faceResult == null) {
                return null
            }

            val alignedFace = faceAlign.alignFaceWithLandmarks(bitmap, faceResult)

            if (alignedFace == null) {
                return null
            }

            PreprocessResult(
                alignedFace = alignedFace,
                faceDetectionResult = faceResult,
                originalBitmap = bitmap
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error in preprocessing: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "Preprocess"
    }
}

data class PreprocessResult(
    val alignedFace: Bitmap,
    val faceDetectionResult: FaceDetectionResult,
    val originalBitmap: Bitmap
)