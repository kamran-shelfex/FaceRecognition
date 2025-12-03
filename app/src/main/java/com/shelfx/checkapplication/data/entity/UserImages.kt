package com.shelfx.checkapplication.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

//@Entity(tableName = "user_images")
//data class UserImages(
//    @PrimaryKey(autoGenerate = true) val id: Int = 0,
//    val userName: String,
//    val frontImagePath: String,
//    val leftImagePath: String,
//    val rightImagePath: String
//)


@Suppress("ArrayInDataClass")
@Entity(tableName = "user_images")
data class UserImages(
    @PrimaryKey(autoGenerate = false)
    val userName: String,
    val frontEmbedding: FloatArray,
    val leftEmbedding: FloatArray,
    val rightEmbedding: FloatArray
)


