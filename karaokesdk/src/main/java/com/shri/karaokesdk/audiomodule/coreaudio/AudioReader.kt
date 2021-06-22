package com.shri.karaokesdk.audiomodule.coreaudio

import android.media.MediaFormat
import android.util.Log
import com.shri.karaokesdk.audiomodule.coreaudio.UCAudioExtractor.UCAudioSampleCallback
import com.shri.karaokesdk.audiomodule.model.AudioSample
import java.io.File
import java.io.IOException
import java.lang.IllegalStateException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * this class is resposible for collecting audio inputs from the mp3 files ,
 * it has list which buffers the chunk of data so that the audio plays smoothly
 */
internal class AudioReader(private val requiredSampleRate: Int, private val mCallback: IAudioStatusProvider) : UCAudioSampleCallback {
    var isStopRecording = false
        get() {
            synchronized(lock) { return field }
        }
        set(recording) {
            synchronized(lock) { field = recording }
        }
    private val lock = Any()
    interface IAudioStatusProvider {
        fun audioReachedEndOfStream()
        fun onInitialBufferingComplete()
        fun onDecoderError()
    }

    private var mAudioExtractor: UCAudioExtractor? = null
    private var mSamples: CopyOnWriteArrayList<AudioSample>? = null
    fun setupAudioExtractorForFile(inputAudio: File) {
        mAudioExtractor =
            UCAudioExtractor(
                requiredSampleRate,
                inputAudio,
                this
            )
        isStopRecording = false
        try {
            mAudioExtractor!!.setupDecoder()
            mSamples = CopyOnWriteArrayList()
        } catch (e: IOException) {
            mCallback.onDecoderError();
            e.printStackTrace()
        }
    }

    fun startFetching() {
        Log.e(TAG, "startFetching: ")
        mSamples?.let {
            if (it != null && it.size < NUMBER_OF_SAMPLES_BUFFERED) {
                startFeedingSamples()
            }
            if (it.size >= NUMBER_OF_SAMPLES_BUFFERED) {
                mCallback.onInitialBufferingComplete()
            }
        }

    }

    fun startFeedingSamples() {
        try {
            while (mSamples != null && mSamples!!.size < NUMBER_OF_SAMPLES_BUFFERED) {
                mAudioExtractor!!.doExtractionForRecorder()
            }
        }catch (exception:IllegalStateException){
            mCallback.onDecoderError()
        }

    }

    fun stopAudioReading() {
        try {
            isStopRecording = true
            if (mAudioExtractor != null) {
                mAudioExtractor!!.stopDecoding()
            }
        }catch (exception:IllegalStateException){
            mCallback.onDecoderError()
        }
    }

    override fun preAudioRender(
        audioData: ByteArray?,
        offsetInBytes: Int,
        sizeInBytes: Int,
        presentationTimeUsec: Long
    ) {
        if (audioData!!.size != 4096) {
            NullPointerException("exceded").printStackTrace()
        }
        Log.e(TAG, "preAudioRender: " + mSamples!!.size)
        val sample = AudioSample(audioData, presentationTimeUsec)
        mSamples!!.add(sample)
    }

    override fun postAudioRender() {

    }

    override fun outputFormatChanged(newFormat: MediaFormat?) {
        if (mSamples != null && mSamples!!.size < NUMBER_OF_SAMPLES_BUFFERED) {
            mAudioExtractor!!.doExtractionForRecorder()
        }
    }

    override fun audioReachedEndOfStream() {
        isStopRecording = true
        if (mAudioExtractor != null) mAudioExtractor!!.stopDecoding()
        mCallback.audioReachedEndOfStream()
    }

    override fun onReleaseAudioDecoder() {

    }

    override fun onDecoderError() {
        mCallback.onDecoderError()
    }

    val audioSamples: AudioSample?
        get() {
            if (mSamples!!.size > 0) {
                val samples = mSamples!!.removeAt(0)
                if (mSamples!!.size < NUMBER_OF_SAMPLES_BUFFERED) {
                    startFeedingSamples()
                }
                return samples
            } else if (mSamples!!.size < NUMBER_OF_SAMPLES_BUFFERED && !isStopRecording) {
                startFeedingSamples()
                return null
            }
            return null
        }

    companion object {
        private const val NUMBER_OF_SAMPLES_BUFFERED = 40
        private const val TAG = "AudioReader"
    }


}