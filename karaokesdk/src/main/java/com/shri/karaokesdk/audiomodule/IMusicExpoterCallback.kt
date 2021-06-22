package com.shri.karaokesdk.audiomodule

interface IMusicExpoterCallback {
    fun onRawSampleReceive(
        samples: ShortArray?,
        presentayionTime: Long
    )
    fun onRecordingStopped()
}