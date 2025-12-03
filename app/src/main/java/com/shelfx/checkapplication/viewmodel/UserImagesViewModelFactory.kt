package com.shelfx.checkapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.shelfx.checkapplication.data.repository.UserImagesRepository
import com.shelfx.checkapplication.utils.EmbeddingPipeline

//class UserImagesViewModelFactory(
//    private val repository: UserImagesRepository,
//    private val embeddingPipeline: EmbeddingPipeline // This parameter is missing in your call
//) : ViewModelProvider.Factory {
//
//    override fun <T : ViewModel> create(modelClass: Class<T>): T {
//        if (modelClass.isAssignableFrom(UserImagesViewModel::class.java)) {
//            @Suppress("UNCHECKED_CAST")
//            return UserImagesViewModel(repository) as T
//        }
//        throw IllegalArgumentException("Unknown ViewModel class")
//    }
//}
class UserImagesViewModelFactory(
    private val repository: UserImagesRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserImagesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UserImagesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
