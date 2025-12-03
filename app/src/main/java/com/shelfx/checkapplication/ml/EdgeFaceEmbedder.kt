// ml/EdgeFaceEmbedder.kt
package com.shelfx.checkapplication.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class EdgeFaceEmbedder(context: Context) {
    private var interpreter: Interpreter? = null
    private val inputSize = 112 // EdgeFace standard input size
    private val embeddingSize = 512 // EdgeFace-S output embedding dimension

    init {
        try {
            val model = FileUtil.loadMappedFile(context, "edgeface.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                // Uncomment if you want to use NNAPI (GPU/NPU acceleration)
                // setUseNNAPI(true)
            }
            interpreter = Interpreter(model, options)
            Log.d(TAG, "EdgeFace model loaded successfully")

            // Print model input/output info
            printModelInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading EdgeFace model: ${e.message}")
            throw e
        }
    }

    /**
     * Generate face embedding from aligned face bitmap
     */
    fun getEmbedding(faceBitmap: Bitmap): FloatArray {
        require(interpreter != null) { "Model not initialized" }

        // Preprocess image to ByteBuffer
        val input = preprocessImage(faceBitmap)

        // Prepare output array
        val output = Array(1) { FloatArray(embeddingSize) }

        // Run inference
        interpreter?.run(input, output)

        // Normalize and return embedding
        return normalizeEmbedding(output[0])
    }

    /**
     * Convert bitmap to ByteBuffer for model input
     */
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        // Ensure bitmap is correct size
        val scaledBitmap = if (bitmap.width != inputSize || bitmap.height != inputSize) {
            Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        } else {
            bitmap
        }

        // Allocate ByteBuffer for input
        // 4 bytes per float * width * height * 3 channels (RGB)
        val buffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())

        // Extract pixel values
        val intValues = IntArray(inputSize * inputSize)
        scaledBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        // Convert pixels to normalized float values
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]

                // Extract RGB values
                val r = (value shr 16 and 0xFF)
                val g = (value shr 8 and 0xFF)
                val b = (value and 0xFF)

                // Normalize to [-1, 1] range (standard for face recognition)
                // Some models use [0, 1], adjust if needed
                buffer.putFloat((r - 127.5f) / 127.5f)
                buffer.putFloat((g - 127.5f) / 127.5f)
                buffer.putFloat((b - 127.5f) / 127.5f)
            }
        }

        buffer.rewind()
        return buffer
    }

    /**
     * L2 normalize the embedding vector
     */
    private fun normalizeEmbedding(embedding: FloatArray): FloatArray {
        // Calculate L2 norm
        val norm = sqrt(embedding.map { it * it }.sum())

        // Normalize (divide by norm)
        return if (norm > 0) {
            embedding.map { it / norm }.toFloatArray()
        } else {
            embedding
        }
    }

    /**
     * Print model input/output information for debugging
     */
    private fun printModelInfo() {
        interpreter?.let {
            Log.d(TAG, "Input tensor count: ${it.inputTensorCount}")
            Log.d(TAG, "Output tensor count: ${it.outputTensorCount}")

            // Input info
            val inputIndex = 0
            Log.d(TAG, "Input shape: ${it.getInputTensor(inputIndex).shape().contentToString()}")
            Log.d(TAG, "Input type: ${it.getInputTensor(inputIndex).dataType()}")

            // Output info
            val outputIndex = 0
            Log.d(TAG, "Output shape: ${it.getOutputTensor(outputIndex).shape().contentToString()}")
            Log.d(TAG, "Output type: ${it.getOutputTensor(outputIndex).dataType()}")
        }
    }

    /**
     * Close the interpreter and free resources
     */
    fun close() {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "EdgeFace model closed")
    }

    companion object {
        private const val TAG = "EdgeFaceEmbedder"
    }
}