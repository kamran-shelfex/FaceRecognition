package com.shelfx.checkapplication.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.shelfx.checkapplication.data.entity.UserImages

@Dao
interface UserImagesDao {

    @Insert
    suspend fun insertUserImages(userImages: UserImages)

    @Update // <-- ADD THIS METHOD
    suspend fun updateUserImages(userImages: UserImages)
    @Query("SELECT * FROM user_images WHERE userName = :name")
    suspend fun getUserImages(name: String): UserImages?
}

