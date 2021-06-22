package com.shri.androidkaraoke

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.shri.androidkaraoke.musicpicker.HomeViewModel


class GenericFactory(private val application: Application): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
       if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
           return HomeViewModel(application) as T
       }
       else {
           throw IllegalArgumentException("ViewModel Not Found")
       }
    }

}