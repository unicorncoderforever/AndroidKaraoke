package com.shri.karaokesdk.audiomodule.coreaudio

import android.media.*
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.File
import java.io.IOException

internal  class AudioPlayer(inSourceFile: File, sampleRate: Int) {
    @JvmField
    var mAudioTrack: AudioTrack? = null
    val sampleRate = 0
    fun play() {
        if (mAudioTrack != null) {
            mAudioTrack!!.play()
        }
    }

    fun stop() {
        if (mAudioTrack != null) {
            mAudioTrack!!.pause()
            mAudioTrack!!.flush()
            mAudioTrack!!.stop()
        }
    }

    fun cleanup() {
        if (mAudioTrack != null) {
            mAudioTrack!!.flush()
            mAudioTrack!!.release()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    fun loadAudioData(
        audioData: ByteArray?, offsetInBytes: Int,
        sizeInBytes: Int, presentationTimeUsec: Long
    ) {
        mAudioTrack!!.write(audioData!!, offsetInBytes, sizeInBytes, AudioTrack.WRITE_BLOCKING)
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    fun setPlaybackPositonReachListener(
        listener: AudioTrack.OnPlaybackPositionUpdateListener?
    ) {
        mAudioTrack!!.setPlaybackPositionUpdateListener(listener)
        mAudioTrack!!.positionNotificationPeriod = 1024
        mAudioTrack!!.notificationMarkerPosition = 1024
    }

    companion object {
        private const val TAG = "AudioPlayer"
        private const val VERBOSE = false
        private fun selectAudioTrack(extractor: MediaExtractor): Int {
            // Select the first audio track we find, ignore the rest.
            val numTracks = extractor.trackCount
            for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime!!.startsWith("audio/")) {
                    if (VERBOSE) {
                        Log.d(
                            TAG,
                            "Audio: Extractor selected track $i ($mime): $format"
                        )
                    }
                    return i
                }
            }
            return -1
        }
    }

    init {
        val extractor: MediaExtractor
        val format: MediaFormat
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(inSourceFile.toString())
            val audioTrackIndex =
                selectAudioTrack(
                    extractor
                )
            if (audioTrackIndex < 0) {
                throw RuntimeException("No video track found in $inSourceFile")
            }
            extractor.selectTrack(audioTrackIndex)
            format = extractor.getTrackFormat(audioTrackIndex)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val channelConfig = AudioFormat.CHANNEL_OUT_STEREO
            val encodingFormat = AudioFormat.ENCODING_PCM_16BIT
            //TODO: Check for a way to extract this information <==================
            val bufferSizeInBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                channelConfig, encodingFormat
            )
            Log.e(
                TAG,
                "AudioPlayer: $bufferSizeInBytes"
            )
            Log.i(
                TAG,
                "channelCount:" + channelCount + ", sampleRateInHz:" + this.sampleRate +
                        ", encodingFormat:" + encodingFormat + ", channelConfig:" + channelConfig + "buff size" + bufferSizeInBytes
            )
            mAudioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate, channelConfig,
                encodingFormat, bufferSizeInBytes, AudioTrack.MODE_STREAM
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}