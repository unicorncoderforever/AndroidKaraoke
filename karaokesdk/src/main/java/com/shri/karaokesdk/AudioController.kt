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


public data class ErrorMessage(val errorMessage: String?, val errorValue: ErrorValues)

public enum class ErrorValues(name: String, value: Int){
    DECODER_ERROR("Decoder SetupError", 0), RECORDER_ERROR("AudioRecorder Error",1), ENCODER_ERROR("Encoder Error ", 2),FILE_NOT_FOUND("File not found Exception",3)
}
