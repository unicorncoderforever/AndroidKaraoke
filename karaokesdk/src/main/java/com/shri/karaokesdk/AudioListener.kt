package com.shri.karaokesdk

import android.net.Uri

/**
 * callback
 */
interface AudioRecorderCallback {
    fun onRecordingComplete(path:String,uri: Uri);
    fun onRecordingError(errorMessage: ErrorMessage);
}