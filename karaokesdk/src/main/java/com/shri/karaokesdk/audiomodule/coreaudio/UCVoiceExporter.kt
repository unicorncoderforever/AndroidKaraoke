package com.shri.karaokesdk.audiomodule.coreaudio

import android.util.Log
import com.shri.karaokesdk.audiomodule.model.UCAudioInfo
import java.io.File
import java.io.IOException
import kotlin.Throws as KotlinThrows
import kotlin.jvm.Throws as Throws

/**
 * Created by preethirao on 14/05/16 AD.
 */
internal class UCVoiceExporter(val outputFile: File?,val mAudioInfo: UCAudioInfo? = null,val mSampleRate: Int = 44100) {

    private var mMuxer: UCMuxer? = null
    private var mAudioEncoder: UCAudioEncoder? = null
    /**
     * Configures encoder and muxer state, and prepares the input Surface.
     */
    init {
        mMuxer = if (IS_AUDIO_ENABLED && IS_VIDEO_ENABLED) {
            //2 tracks
            UCMuxer(2)
        } else {
            //1 track
            UCMuxer(1)
        }
        mAudioEncoder = UCAudioEncoder()

    }


    fun prepareVideoEncoder() {
        if (mAudioInfo == null) {
            Log.e(
                TAG,
                "prepareVideoEncoder: audio info null"
            )
            prepareAudioEncoder(
                AUDIO_CHANNELS,
                mSampleRate,
                AUDIO_BIT_RATE
            )
        } else {
            Log.e(
                TAG,
                "prepareVideoEncoder: audio info not null"
            )
            prepareAudioEncoder(
                mAudioInfo!!.channelCount,
                mSampleRate,
                mAudioInfo!!.mBitRate,
                mAudioInfo!!.mMimeType
            )
        }
    }

    /**
     * these the method regarding encoding the audio file don't touch these
     */
    fun prepareAudioEncoder(channelCount: Int, sampleRateinHZ: Int, bitRate: Int) {
        if (IS_AUDIO_ENABLED) {
            mAudioEncoder!!.start(mMuxer, channelCount, sampleRateinHZ, bitRate)
        }
    }

    fun prepareAudioEncoder(
        channelCount: Int,
        sampleRateinHZ: Int,
        bitRate: Int,
        mime: String?
    ) {
        Log.e(
            TAG,
            "prepareAudioEncoder: " + mAudioInfo!!.mSampleRate
        )
        if (IS_AUDIO_ENABLED) {
            mAudioEncoder!!.start(mMuxer, channelCount, sampleRateinHZ, bitRate, mime)
        }
    }

    /**
     * Releases encoder resources.
     */
    fun release() {
        if (VERBOSE) Log.d(
            TAG,
            "releasing encoder objects"
        )
        if (IS_AUDIO_ENABLED) {
            if (mAudioEncoder != null) {
                mAudioEncoder!!.stopEncoder()
                mAudioEncoder = null
            }
        }
        if (mMuxer != null) {
            mMuxer!!.close()
            mMuxer = null
        }
        Log.d(TAG, "Encoder resources released")
    }

    fun drainAudioEncoder(
        input: ByteArray,
        presentationTimeNs: Long,
        endOfStream: Boolean
    ) {
        Log.e(TAG, "drainAudioEncoder: " + input.size)
        mAudioEncoder!!.write(input, presentationTimeNs, false)
    }

    fun stopAudioRecording() {
        mAudioEncoder!!.write(ByteArray(1), 0, true)
    }

    @Throws(IOException::class)
    fun initExporter() {
        outputFile?.let {
            mMuxer?.init(outputFile)
        }
    }

    companion object {
        private val TAG = UCVoiceExporter::class.java.simpleName
        private const val VERBOSE = false
        private const val AUDIO_CHANNELS = 2
        private const val AUDIO_BIT_RATE = 96000
        const val IS_AUDIO_ENABLED = true
        const val IS_VIDEO_ENABLED = false
    }
}