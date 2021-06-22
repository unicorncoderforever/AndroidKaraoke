package com.shri.androidkaraoke.musicpicker

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import com.shri.androidkaraoke.musicpicker.model.UCMusicListModel
import java.util.*

 class UCMusicFetcher() {
    var phoneTracksArray: ArrayList<UCMusicListModel>

    interface IMusicListType {
        companion object {
            const val GALLERY = 1
            const val MY_ALBUM = 0
        }
    }

    fun fetchAlbums(
        context: Context,
        galleryType: Int
    ): ArrayList<UCMusicListModel> {
        val gallaryFiles: ArrayList<UCMusicListModel>
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        gallaryFiles = ArrayList()
        val select = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        val selection =
            MediaStore.Audio.Media.IS_MUSIC + "=1" + " AND " + MediaStore.Audio.Media.DATA + " LIKE '%/Sing With ME/%'"
        val deselection =
            MediaStore.Audio.Media.IS_MUSIC + "=1" + " AND " + MediaStore.Audio.Media.DATA + " NOT LIKE '%/Sing With ME/%'"
        val cursor: Cursor?
        if (galleryType == IMusicListType.MY_ALBUM) {
            cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, select, selection,
                null, MediaStore.Audio.Media.ALBUM_ID + " DESC"
            )
            Log.d(TAG, ":" + cursor!!.count)
        } else if (galleryType == IMusicListType.GALLERY) {
            cursor = context.contentResolver.query(
                uri, select, deselection,
                null, MediaStore.Audio.Media.ALBUM_ID
            )
        } else {
            cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, select, null,
                null, MediaStore.Audio.Media.ALBUM_ID + " DESC"
            )
        }
        if (cursor!!.moveToFirst()) {
            while (!cursor.isAfterLast) {
                val albumId =
                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID))
                val track =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE))
                val data =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                val artist =
                    cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                if (data.endsWith(".mp3") || data.endsWith(".MP3") || data.endsWith(".m4a") || data.endsWith(
                        ".mp4"
                    ) || data.endsWith(".MP4") || data.endsWith(".M4A")
                ) {
                    val audioListModel = UCMusicListModel()
                    audioListModel.musicPath = data
                    audioListModel.musicTitle = track
                    audioListModel.musicArtist = artist
                    audioListModel.albumId = albumId
                    gallaryFiles.add(audioListModel)
                }
                cursor.moveToNext()
            }
        }
        cursor.close()
        return gallaryFiles
    }

    companion object {
        private const val TAG = "UCMusicFetcher"
    }

    init {
        phoneTracksArray = ArrayList()
    }
}