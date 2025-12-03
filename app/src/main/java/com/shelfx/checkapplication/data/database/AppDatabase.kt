package com.shelfx.checkapplication.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.shelfx.checkapplication.data.entity.UserImages

@Database(entities = [UserImages::class], version = 1, exportSchema = false)

@TypeConverters(EmbeddingConverter::class)

abstract class AppDatabase : RoomDatabase() {
    abstract fun userImagesDao(): UserImagesDao
}



