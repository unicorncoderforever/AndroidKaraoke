package com.shri.androidkaraoke.musicpicker

import android.app.Application
import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.shri.androidkaraoke.musicpicker.model.UCMusicListModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : BaseViewModel(application) {
    private val mcontext: Context
    val  model:MutableLiveData<ArrayList<UCMusicListModel>> =
        MutableLiveData<ArrayList<UCMusicListModel>>()
    var musicFetcher:UCMusicFetcher? = null


    init {
        mcontext = application
        musicFetcher = UCMusicFetcher()
    }

     fun loadMusicFiles(){
         viewModelScope.launch(Dispatchers.IO) {
          fetchAlbums(UCMusicFetcher.IMusicListType.GALLERY)
        }
    }
    fun loadkaraokeSongs(){
        viewModelScope.launch(Dispatchers.IO) {
            fetchAlbums(UCMusicFetcher.IMusicListType.MY_ALBUM)
        }

    }

    suspend fun fetchAlbums(type:Int){
        val  festival  = viewModelScope.async {
            musicFetcher?.fetchAlbums(mcontext,type)
        }
        model.postValue(festival.await())
        }
}