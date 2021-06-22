package com.shri.karaokesdk

import java.io.File

/**
 *
 * this is controller methods exposed to the outside world
 * I can add here more, ask for it
 */
interface AudioController{
    fun pauseAudioRecording()
    fun resumeAudioRecording()
    fun stopAudioPlaying()
    fun recordAudio(path:String,outputFile: File)
    fun isAudioRecording():Boolean
}