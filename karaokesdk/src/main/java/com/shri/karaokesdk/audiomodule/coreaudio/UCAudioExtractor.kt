package com.shri.karaokesdk.audiomodule.coreaudio

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.shri.karaokesdk.ErrorMessage
import com.shri.karaokesdk.ErrorValues
import com.shri.karaokesdk.audiomodule.model.UCAudioInfo
import com.shri.karaokesdk.audiomodule.resample.Resampler
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.IllegalStateException
import kotlin.jvm.Throws


/**
 * resposible for reading mp3 files and decoding data and passing buffer chunks
 */
@SuppressLint("NewApi")
internal class UCAudioExtractor(
    val requiredSampleRate: Int,
    val mSourceFile: File,
    val mAudioCallback: UCAudioSampleCallback,
    private val mLoop: Boolean = false
) {
    /**
     * Selects the audio track, if any.
     *
     * @return The track index, or -1 if no audio track is found.
     */
    var audioInfo: UCAudioInfo? = null
        private set

    private var mExtractor: MediaExtractor? = null
    private var mDecoder: MediaCodec? = null

    // Declare this here to reduce allocations.
    private val mBufferInfo = MediaCodec.BufferInfo()
    private var isDecoding = true

    private var isMono = false
    var mcacheBuffer: ByteArray? = null
    private var mTrackSampleRate = 0
    private var mAudioTrackIndex = 0
    private var mIsResampleRequired = false

    init {
        openAudioFile(mSourceFile)
    }

    private var mConstantSample =
        SAMPLE_COUNT_NORMAL

    @Throws(IllegalStateException::class)
    fun doExtractionForRecorder(): Boolean {

            mcacheBuffer?.let {
                if (it.size > mConstantSample) {
                    var copy1 = ByteArray(mConstantSample)
                    System.arraycopy(it, 0, copy1, 0, mConstantSample)
                    val copy2 = ByteArray(it.size - mConstantSample)
                    System.arraycopy(
                        it, mConstantSample, copy2, 0,
                        it.size - mConstantSample
                    )
                    mcacheBuffer = copy2
                    if (isMono) {
                        Log.e(TAG, "doExtractionForRecorder: ")
                        copy1 = convertToStereoData(copy1)
                    }
                    mAudioCallback.preAudioRender(
                        copy1, 0, mConstantSample,
                        System.currentTimeMillis()
                    )
                    return false
                }
            }
            if (!isDecoding) {
                if (VERBOSE) Log.d(
                    TAG,
                    "Audio: Stop requested"
                )
                releaseDecoder()
                return true
            }
            val decoderInputBuffers = mDecoder!!.inputBuffers
            val decoderOutputBuffers = mDecoder!!.outputBuffers
            var sawInputEOS = false
            val inputBufIndex =
                mDecoder!!.dequeueInputBuffer(DQ_INPUT_BUFFER_TIMEOUT.toLong())
            if (inputBufIndex >= 0) {
                val inputBuf = decoderInputBuffers[inputBufIndex]
                var chunkSize = mExtractor!!.readSampleData(inputBuf, 0)
                var presentationTimeUs: Long = 0
                if (chunkSize < 0) {
                    sawInputEOS = true
                    chunkSize = 0
                    if (VERBOSE) Log.d(
                        TAG,
                        "Audio: sent input EOS"
                    )
                } else {
                    if (mExtractor!!.sampleTrackIndex != mAudioTrackIndex) {
                        Log.e(
                            TAG, "Audio: WEIRD: got sample from track " +
                                    mExtractor!!.sampleTrackIndex + ", expected " +
                                    mAudioTrackIndex
                        )
                    }
                    presentationTimeUs = mExtractor!!.sampleTime
                }
                mDecoder!!.queueInputBuffer(
                    inputBufIndex,
                    0,  // offset
                    chunkSize, presentationTimeUs,
                    if (sawInputEOS) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                )
                if (!sawInputEOS) {
                    mExtractor!!.advance()
                }
            } else {
                Log.w(TAG, "Decoder input buffer not available")
                return false
            }
            val decoderStatus = mDecoder!!.dequeueOutputBuffer(
                mBufferInfo,
                DQ_OUTPUT_BUFFER_TIMEOUT.toLong()
            )
            if (decoderStatus < 0) {
                if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (VERBOSE) Log.d(
                        TAG,
                        "Audio: no output from decoder available"
                    )
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    if (VERBOSE) Log.d(
                        TAG,
                        "Audio: decoder output buffers changed"
                    )
                } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val oformat = mDecoder!!.outputFormat
                    mAudioCallback.outputFormatChanged(oformat)
                    if (VERBOSE) Log.d(
                        TAG, "Audio: decoder output format changed: "
                                + oformat
                    )
                } else {
                    throw RuntimeException(
                        "unexpected result from decoder.dequeueOutputBuffer: "
                                + decoderStatus
                    )
                }
                return false
            } else { // if (decoderStatus >= 0) {
                val buf = decoderOutputBuffers[decoderStatus]
                val chunk = ByteArray(mBufferInfo.size)
                buf[chunk] // Read the buffer all at once
                buf.clear() // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET
                // THIS SAME BUFFER BAD THINGS WILL HAPPEN
                val doRender = chunk.isNotEmpty()
                if (isDecoding) {
                    if (doRender) {
                        if (chunk.size != mConstantSample
                            || mIsResampleRequired
                        ) {
                            doResampling(
                                mAudioCallback, chunk, mBufferInfo.size,
                                mBufferInfo.presentationTimeUs
                            )
                        } else {
                            mAudioCallback.preAudioRender(
                                chunk, 0,
                                chunk.size,
                                mBufferInfo.presentationTimeUs
                            )
                        }
                    } else {
                        mBufferInfo.presentationTimeUs = -1
                    }
                    mDecoder!!.releaseOutputBuffer(decoderStatus, false /* render */)
                    if (doRender) {
                        mAudioCallback.postAudioRender()
                    }
                    if (sawInputEOS) {
                        if (VERBOSE) Log.d(
                            TAG,
                            "Audio: output EOS"
                        )
                        if (mLoop) {
                            mExtractor!!.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                            mDecoder!!.flush()
                        } else {
                            mAudioCallback.audioReachedEndOfStream()
                            return true
                        }
                    }
                } else {
                    mBufferInfo.presentationTimeUs = -1
                }
            }
            return false

    }


    @Throws(IOException::class)
    fun setupDecoder() {
        // The MediaExtractor error messages aren't very useful.  Check to see if the input
        // file exists so we can throw a better one if it's not there.
        if (!mSourceFile.canRead()) {
            throw FileNotFoundException("Unable to read $mSourceFile")
        }
        mDecoder!!.start()
        isDecoding = true
    }
    /**
     * Callback invoked when rendering audio frames.  The MoviePlayer client must
     * provide one of these.
     */
    interface UCAudioSampleCallback {
        /**
         * Called immediately before the sample is rendered.
         *
         * @param presentationTimeUsec The desired presentation time, in microseconds.
         */
        fun preAudioRender(
            audioData: ByteArray?,
            offsetInBytes: Int,
            sizeInBytes: Int,
            presentationTimeUsec: Long
        )

        /**
         * Called immediately after the frame render call returns.  The frame may not have
         * actually been rendered yet.
         */
        fun postAudioRender()

        /**
         * Called when the output format is changed.
         */
        fun outputFormatChanged(newFormat: MediaFormat?)

        /**
         * Called after the last frame of a looped movie has been rendered.  This allows the
         * callback to adjust its expectations of the next presentation time stamp.
         */
        //        void loopReset();
        fun audioReachedEndOfStream()
        fun onReleaseAudioDecoder()
        fun onDecoderError(errorMessage: ErrorMessage)
    }

    /**
     * constructor initiates the call
     */


    private fun openAudioFile(inSourceFile: File) {
        // Pop the file open and pull out the video characteristics.
        mExtractor = null
        try {
            mExtractor = MediaExtractor()
            mExtractor!!.setDataSource(inSourceFile.toString())
            mAudioTrackIndex =
                selectAudioTrack(
                    mExtractor!!
                )
            if (mAudioTrackIndex < 0) {
                throw RuntimeException("No audio track found in $mSourceFile")
            }
            mExtractor!!.selectTrack(mAudioTrackIndex)
            val format = mExtractor!!.getTrackFormat(mAudioTrackIndex)
            mTrackSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val birtare = format.getInteger(MediaFormat.KEY_BIT_RATE)
            audioInfo = UCAudioInfo()
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            Log.i("audio", "sample rate $mTrackSampleRate")
            Log.i("audio", "channel count $channelCount")
            Log.i("audio", "mime " + format.getString(MediaFormat.KEY_MIME))
            Log.i(TAG, "openAudioFile: ")
            val mime = format.getString(MediaFormat.KEY_MIME)
            mDecoder = MediaCodec.createDecoderByType(mime!!)
            audioInfo!!.mSampleRate = mTrackSampleRate
            audioInfo!!.mBitRate = birtare
            audioInfo!!.channelCount = channelCount
            audioInfo!!.mMimeType = mime
            mDecoder!!.configure(format, null, null, 0)
            //            mResampler = new Resampler();

            if (mTrackSampleRate != requiredSampleRate) {
                Log.i(
                    TAG,
                    "sampleRate: $mTrackSampleRate"
                )
                mIsResampleRequired = true
            }
            if (format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != CHANNEL_COUNT) {
                isMono = false
                Log.e(
                    TAG,
                    "openAudioFile: $mTrackSampleRate  it is mono bitrate $birtare"
                )
                mConstantSample /= 2
            } else {
                Log.e(
                    TAG,
                    "openAudioFile: $mTrackSampleRate  it is stereo bitrate $birtare"
                )
            }
        } catch (e: IOException) {
            mAudioCallback.onDecoderError(ErrorMessage("error while opening audio file in method @openAudioFile",ErrorValues.DECODER_ERROR))
            e.printStackTrace()
        }


    }

    /**
     * Stops decoding the video <br></br>
     * Can be resumed by calling startDecoding()
     */
    @Throws(IllegalStateException::class)
    fun stopDecoding() {
            isDecoding = false
            Log.i(TAG, "stop decoding")
            releaseDecoder()

    }


    @Throws(IllegalStateException::class)
    private fun releaseDecoder() {
        if (mDecoder != null) {
            mDecoder!!.stop()
            mDecoder!!.release()
            mDecoder = null
            if (VERBOSE) {
                Log.v(TAG, "decoder released")
            }
        }
        if (mExtractor != null) {
            mExtractor!!.release()
            mExtractor = null
            if (VERBOSE) {
                Log.v(TAG, "mExtractor released")
            }
        }
        mAudioCallback!!.onReleaseAudioDecoder()
        Log.d(TAG, "Audio extractor released")
    }

    private fun doSingleExtraction(): Boolean {
        mcacheBuffer?.let {
            if(it.size >= mConstantSample){
                var copy1 = ByteArray(mConstantSample)
                System.arraycopy(it, 0, copy1, 0, mConstantSample)
                val copy2 = ByteArray(it.size - mConstantSample)
                System.arraycopy(
                    it, mConstantSample, copy2, 0,
                    it.size - mConstantSample
                )
                mcacheBuffer = copy2
                if (isMono) {
                    copy1 = convertToStereoData(copy1)
                }
                mAudioCallback.preAudioRender(
                    copy1, 0, mConstantSample,
                    System.currentTimeMillis()
                )
                return false
            }
        }
        if (!isDecoding) {
            if (VERBOSE) Log.d(
                TAG,
                "Audio: Stop requested"
            )
            releaseDecoder()
            return true
        }
        val decoderInputBuffers = mDecoder!!.inputBuffers
        val decoderOutputBuffers = mDecoder!!.outputBuffers
        var sawInputEOS = false
        val inputBufIndex =
            mDecoder!!.dequeueInputBuffer(DQ_INPUT_BUFFER_TIMEOUT.toLong())
        if (inputBufIndex >= 0) {
            val inputBuf = decoderInputBuffers[inputBufIndex]
            var chunkSize = mExtractor!!.readSampleData(inputBuf, 0)
            var presentationTimeUs: Long = 0
            if (chunkSize < 0) {
                sawInputEOS = true
                chunkSize = 0
                if (VERBOSE) Log.d(
                    TAG,
                    "Audio: sent input EOS"
                )
            } else {
                if (mExtractor!!.sampleTrackIndex != mAudioTrackIndex) {
                    Log.e(
                        TAG, "Audio: WEIRD: got sample from track " +
                                mExtractor!!.sampleTrackIndex + ", expected " +
                                mAudioTrackIndex
                    )
                }
                presentationTimeUs = mExtractor!!.sampleTime
            }
            mDecoder!!.queueInputBuffer(
                inputBufIndex,
                0,  // offset
                chunkSize, presentationTimeUs,
                if (sawInputEOS) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            )
            if (!sawInputEOS) {
                mExtractor!!.advance()
            }
        } else {
            Log.w(TAG, "Decoder input buffer not available")
            return false
        }

//        while (true) {
        val decoderStatus = mDecoder!!.dequeueOutputBuffer(
            mBufferInfo,
            DQ_OUTPUT_BUFFER_TIMEOUT.toLong()
        )
        if (decoderStatus < 0) {
            if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (VERBOSE) Log.d(
                    TAG,
                    "Audio: no output from decoder available"
                )
            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
//                decoderOutputBuffers = decoder.getOutputBuffers();
                if (VERBOSE) Log.d(
                    TAG,
                    "Audio: decoder output buffers changed"
                )
            } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val oformat = mDecoder!!.outputFormat
                mAudioCallback!!.outputFormatChanged(oformat)
                if (VERBOSE) Log.d(
                    TAG, "Audio: decoder output format changed: "
                            + oformat
                )
            } else {
                throw RuntimeException(
                    "unexpected result from decoder.dequeueOutputBuffer: "
                            + decoderStatus
                )
            }
            return false
        } else { // if (decoderStatus >= 0) {
            val buf = decoderOutputBuffers[decoderStatus]
            val chunk = ByteArray(mBufferInfo.size)
            buf[chunk] // Read the buffer all at once
            buf.clear() // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET
            // THIS SAME BUFFER BAD THINGS WILL HAPPEN
            val doRender = chunk.size != 0
            if (isDecoding) {
                if (doRender) {
                    mAudioCallback.preAudioRender(
                        chunk, 0,
                        chunk.size,
                        mBufferInfo.presentationTimeUs
                    )
                    Log.i("RESAMPLE", "false")
                    //                    }
                } else {
                    mBufferInfo.presentationTimeUs = -1
                }
                mDecoder!!.releaseOutputBuffer(decoderStatus, false /* render */)
                if (doRender) {
                    mAudioCallback.postAudioRender()
                }
                if (sawInputEOS) {
                    if (VERBOSE) Log.d(
                        TAG,
                        "Audio: output EOS"
                    )
                    if (mLoop) {
                        mExtractor!!
                            .seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                        mDecoder!!.flush()
                    } else {
                        mAudioCallback.audioReachedEndOfStream()
                        return true
                    }
                }
            } else {
                mBufferInfo.presentationTimeUs = -1
            }
        }
        return false
    }

    private fun convertToStereoData(samples: ByteArray): ByteArray {
        val stereoGeneratedSnd = ByteArray(samples.size * 2)
        var i = 0
        while (i < samples.size) {
            stereoGeneratedSnd[i * 2] = samples[i]
            stereoGeneratedSnd[i * 2 + 1] = samples[i + 1]
            stereoGeneratedSnd[i * 2 + 2] = samples[i]
            stereoGeneratedSnd[i * 2 + 3] = samples[i + 1]
            i += 2
        }
        return stereoGeneratedSnd
    }

    private fun doResampling(
        audioCallback: UCAudioSampleCallback,
        chunk: ByteArray, len: Int, presentationTimeUs: Long
    ) {

        val resampledWaveData: ByteArray
        if (mIsResampleRequired) {
            resampledWaveData = Resampler.reSample(
                chunk, len, 16,
                mTrackSampleRate, requiredSampleRate, isMono
            )
            Log.e(
                TAG,
                "doResampling: " + resampledWaveData.size
            )
        } else {
            resampledWaveData = chunk
        }
        if (mcacheBuffer == null) {
            if (resampledWaveData.size >= mConstantSample) {
                val copy1 = ByteArray(mConstantSample)
                System.arraycopy(
                    resampledWaveData, 0, copy1, 0,
                    mConstantSample
                )
                mcacheBuffer = ByteArray(
                    resampledWaveData.size
                            - mConstantSample
                )
                System.arraycopy(
                    resampledWaveData, mConstantSample,
                    mcacheBuffer, 0, mcacheBuffer!!.size
                )
                audioCallback.preAudioRender(
                    copy1, 0, copy1.size,
                    presentationTimeUs
                )
            } else {
                mcacheBuffer = ByteArray(resampledWaveData.size)
                System.arraycopy(
                    resampledWaveData, 0, mcacheBuffer, 0,
                    resampledWaveData.size
                )
                doExtractionForRecorder()
            }
        } else {
            if (mcacheBuffer!!.size >= mConstantSample) {
                var copy1 = ByteArray(mConstantSample)
                System.arraycopy(mcacheBuffer, 0, copy1, 0, mConstantSample)
                val cacheBuffer = ByteArray(
                    mcacheBuffer!!.size
                            - mConstantSample + resampledWaveData.size
                )
                System.arraycopy(
                    mcacheBuffer, mConstantSample, cacheBuffer, 0,
                    mcacheBuffer!!.size - mConstantSample
                )
                System.arraycopy(
                    resampledWaveData, 0, cacheBuffer,
                    mcacheBuffer!!.size - mConstantSample,
                    cacheBuffer.size
                )
                if (isMono) {
                    copy1 = convertToStereoData(copy1)
                }
                audioCallback.preAudioRender(
                    copy1, 0, copy1.size,
                    presentationTimeUs
                )
                mcacheBuffer = cacheBuffer
            } else if (resampledWaveData.size + mcacheBuffer!!.size >= mConstantSample) {
                var copy1 = ByteArray(mConstantSample)
                System.arraycopy(mcacheBuffer, 0, copy1, 0, mcacheBuffer!!.size)
                System.arraycopy(
                    resampledWaveData, 0, copy1,
                    mcacheBuffer!!.size, mConstantSample
                            - mcacheBuffer!!.size
                )
                val size = (resampledWaveData.size - mConstantSample
                        + mcacheBuffer!!.size)
                val copy2 = ByteArray(size)
                System.arraycopy(
                    resampledWaveData,
                    mConstantSample
                            - mcacheBuffer!!.size,
                    copy2,
                    0,
                    resampledWaveData.size - mConstantSample
                            + mcacheBuffer!!.size
                )
                if (isMono) {
                    copy1 = convertToStereoData(copy1)
                }
                audioCallback.preAudioRender(
                    copy1, 0, copy1.size,
                    presentationTimeUs
                )
                mcacheBuffer = copy2
            } else {
                val newCacheBuffer = ByteArray(
                    mcacheBuffer!!.size
                            + resampledWaveData.size
                )
                System.arraycopy(
                    mcacheBuffer, 0, newCacheBuffer, 0,
                    mcacheBuffer!!.size
                )
                System.arraycopy(
                    resampledWaveData, 0, newCacheBuffer,
                    mcacheBuffer!!.size, resampledWaveData.size
                )
                mcacheBuffer = newCacheBuffer
                doSingleExtraction()
            }
        }
    }

    companion object {
        private val TAG = UCAudioExtractor::class.java.simpleName
        private var VERBOSE = true
        private const val MIME_TYPE = "audio/"
        private const val DQ_OUTPUT_BUFFER_TIMEOUT = 1000
        private const val DQ_INPUT_BUFFER_TIMEOUT = 1000
        const val SAMPLE_COUNT_NORMAL = 4096
        private const val CHANNEL_COUNT = 2
        private fun selectAudioTrack(extractor: MediaExtractor): Int {
            val numTracks = extractor.trackCount
            for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME)
                if (mime!!.startsWith(MIME_TYPE)) {
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
}