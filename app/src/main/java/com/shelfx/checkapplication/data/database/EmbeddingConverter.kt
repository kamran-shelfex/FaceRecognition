package com.shelfx.checkapplication.data.database

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EmbeddingConverter {

    @TypeConverter
    fun fromFloatArray(array: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(array.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        array.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @TypeConverter
    fun toFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)

        val arr = FloatArray(bytes.size / 4)
        for (i in arr.indices) {
            arr[i] = buffer.getFloat()
        }
        return arr
    }
}
