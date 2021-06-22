package com.shri.karaokesdk.audiomodule.coreaudio

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.shri.karaokesdk.audiomodule.model.UCAudioInfo
import java.io.File

/**
 * Created by preethirao on 8/27/17.
 */
internal object MediaMetaDataRetriever {
    private const val MIME_TYPE = "audio/"
    private const val VERBOSE = false
    private const val TAG = "MediaMetaDataRetriever"
    @JvmStatic
    fun getAudioMetaData(inSourceFile: File): UCAudioInfo? {
        var mExtractor: MediaExtractor? = null
        val mAudioInfo: UCAudioInfo
        val mAudioTrackIndex: Int
        try {
            mExtractor = MediaExtractor()
            mExtractor.setDataSource(inSourceFile.toString())
            mAudioTrackIndex =
                selectAudioTrack(
                    mExtractor
                )
            if (mAudioTrackIndex < 0) {
                throw RuntimeException("No audio track found in $inSourceFile")
            }
            mExtractor.selectTrack(mAudioTrackIndex)
            val format = mExtractor.getTrackFormat(mAudioTrackIndex)
            val trackSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE)
            mAudioInfo = UCAudioInfo()
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mime = format.getString(MediaFormat.KEY_MIME)
            mAudioInfo.mSampleRate = trackSampleRate
            mAudioInfo.mBitRate = bitrate
            mAudioInfo.channelCount = channelCount
            mAudioInfo.mMimeType = mime
            return mAudioInfo
        } catch (e: Exception) {
        }
        return null
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        // Select the first audio track we find, ignore the rest.
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