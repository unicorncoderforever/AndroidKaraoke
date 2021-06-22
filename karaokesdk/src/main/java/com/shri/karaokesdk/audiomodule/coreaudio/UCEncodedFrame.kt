package com.shri.karaokesdk.audiomodule.coreaudio

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.util.Log
import java.nio.BufferOverflowException
import java.nio.ByteBuffer

/**
 * Created by dinesha on 06/01/16.
 */
@SuppressLint("NewApi")
internal  class UCEncodedFrame {
    @JvmField
    var mInputBuffer: ByteBuffer
    @JvmField
    var mTrackIndex: Int
    @JvmField
    var mBufferInfo: MediaCodec.BufferInfo

    constructor() {
        mInputBuffer = ByteBuffer.allocate(100000)
        mTrackIndex = -1
        mBufferInfo = MediaCodec.BufferInfo()
    }

    constructor(
        inputBuffer: ByteBuffer,
        trackIndex: Int, bufferInfo: MediaCodec.BufferInfo
    ) {
        mInputBuffer = inputBuffer
        mTrackIndex = trackIndex
        mBufferInfo = bufferInfo
    }

    fun setValue(
        inputBuffer: ByteBuffer?,
        trackIndex: Int, bufferInfo: MediaCodec.BufferInfo
    ) {
        mTrackIndex = trackIndex
        mBufferInfo[bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs] =
            bufferInfo.flags
        try {
            mInputBuffer.put(inputBuffer)
        } catch (e: BufferOverflowException) {
            if (VERBOSE) {
                Log.i(
                    TAG,
                    "Modifying buffer size, " + mInputBuffer.capacity() + " -> " + bufferInfo.size
                )
            }
            mInputBuffer = ByteBuffer.allocate(bufferInfo.size)
            mInputBuffer.put(inputBuffer)
        }
        mInputBuffer.position(bufferInfo.offset)
        mInputBuffer.limit(bufferInfo.offset + bufferInfo.size)
    }

    companion object {
        private val TAG = UCEncodedFrame::class.java.simpleName
        private const val VERBOSE = false
    }
}