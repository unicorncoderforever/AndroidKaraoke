package com.shri.androidkaraoke.utility

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

/**
 * Created by preethirao on 01/06/16 AD.
 */
class UCAudioTrackPlayer(var mAudioPath: String) {
    private var mPlayer: MediaPlayer? = null
    fun initializeTrack(context: Context?) {
        mPlayer = MediaPlayer.create(context, Uri.parse(mAudioPath))
        mPlayer?.setLooping(true)
    }

    fun playSound() {
        mPlayer!!.start()
    }

    fun pausePlayer() {
        if (mPlayer!!.isPlaying) {
            mPlayer!!.pause()
        } else {
            mPlayer!!.start()
        }
    }

    fun isPlaying():Boolean{
       return (mPlayer?.isPlaying) ?: false
    }

    fun stopPlaying() {
        mPlayer?.stop()
        mPlayer?.release()
        mPlayer = null
    }

    companion object {
        const val TAG = "UCAudioTrackPlayer"
        const val VERBOSE = false
    }

}