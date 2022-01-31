package com.shri.karaokesdk

import android.content.Context
import android.net.Uri
import com.shri.karaokesdk.audiomodule.coreaudio.AudioManager
import com.shri.karaokesdk.audiomodule.coreaudio.IAudioPlayerStatus
import java.io.File

/**
 * class exposed to the outside world, so wanted implement state machine ,but no time
 * sometime in future definitely will clear this up
 */
class KaraokeAudioManager(context: Context,val recorderCallback: AudioRecorderCallback):AudioController,AudioRecorderCallback{
    private var mAudioManager:AudioManager
    private var playerStatus:IAudioPlayerStatus = IAudioPlayerStatus.UNINITIALIZED

    init {
      mAudioManager = AudioManager(context,this)
    }


    override fun pauseAudioRecording() {
        if(playerStatus == IAudioPlayerStatus.PLAY) {
            playerStatus = IAudioPlayerStatus.PAUSE
            mAudioManager.pauseAudioRecording()
        }
    }

    override fun resumeAudioRecording() {
        if(playerStatus == IAudioPlayerStatus.PAUSE){
            playerStatus = IAudioPlayerStatus.PLAY
            mAudioManager.resumeAudioRecording()
        }
    }

    override fun stopAudioPlaying() {
        if (playerStatus == IAudioPlayerStatus.PAUSE || playerStatus == IAudioPlayerStatus.PLAY) {
            playerStatus = IAudioPlayerStatus.STOP
            mAudioManager.stopAudioPlaying()
        }
    }

    override fun recordAudio(path: String,outputFile: File) {
        if(playerStatus == IAudioPlayerStatus.UNINITIALIZED) {
            playerStatus = IAudioPlayerStatus.PLAY
            mAudioManager.recordAudio(path,outputFile)
        }
    }

    override fun isAudioRecording() :Boolean{
        if(playerStatus == IAudioPlayerStatus.PLAY){
            return true
        }else{
            return false
        }
    }

    override fun onRecordingComplete(path:String,uri: Uri) {
        recorderCallback.onRecordingComplete(path,uri)
    }

    override fun onRecordingError(errorMessage: ErrorMessage) {
        recorderCallback.onRecordingError(errorMessage)
    }

}