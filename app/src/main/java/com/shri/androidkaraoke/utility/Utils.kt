package com.shri.androidkaraoke.utility

import android.content.Context
import android.os.Environment
import android.util.Log
import com.shri.karaokesdk.audiomodule.utility.Utils
import java.io.File

class Utils{

    companion object{
        const val APP_DIR = "karaokeAndroid"
        const val KARAOUKE_DIR = "karouke_audio"
        const val SONG_DIR = "Sing With ME"
        /**
         * returns the next available output file on incrementing the count
         * recently the getExternalStorageDirectory has been deprecated adjust for now, will update this soon
         */
        fun getNextOutputFile(context: Context): File {
            val sharedPreference =
                context.getSharedPreferences(APP_DIR, 0)
            val key = "audio_id"
            var value = sharedPreference.getInt(key, 0)
            value++
            val filename = KARAOUKE_DIR+"$value.mp4"
            val editor = sharedPreference.edit()
            editor.putInt(key, value)
            editor.commit()
            val outputDir =
                File(Environment.getExternalStorageDirectory(), SONG_DIR)
            if (!outputDir.exists()) {
                outputDir.mkdir()
            }
            Log.e(
                Utils.TAG,
                "getNextOutputFile: $filename"
            )
            val outputFile = File(outputDir.absolutePath, filename)
            val audioPath = outputFile.absolutePath
            return outputFile
        }

    }
}