package com.shri.karaokesdk.audiomodule.coreaudio

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.InvalidParameterException
import java.util.*
import kotlin.jvm.Throws

/**
 * Created by preethirao on 14/05/16 AD.
 */
@SuppressLint("NewApi")
class UCMuxer(private val TOTAL_TRACKS: Int) : Runnable {

    @Volatile
    private var mIsAlive = false
    private var mTracksAdded = 0
    private var mMuxerThread: Thread? = null
    private var mMuxer: MediaMuxer? = null
    private var mEncodedFrames: Queue<UCEncodedFrame>? = null
    private var mFreeFrames: Queue<UCEncodedFrame>? = null
    fun init(outputFile: File) {
        if (USE_BUF_POOL) {
            mFreeFrames = LinkedList()
        }
        mEncodedFrames = LinkedList()
        try {
            mMuxer = MediaMuxer(
                outputFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            mIsAlive = true
            if (VERBOSE) {
                Log.i(
                    TAG,
                    "Starting Media muxer for " + outputFile.absolutePath + " with " + TOTAL_TRACKS + " tracks"
                )
            }
        } catch (e: IOException) {
            throw IOException("Failed to create muxer")
        }
    }

    @Synchronized
    @Throws(IllegalStateException::class, InvalidParameterException::class)
    fun addTrack(format: MediaFormat?, type: String): Int {
        return try {
            val trackIndex = mMuxer!!.addTrack(format!!)
            Log.v(
                TAG,
                "Added $type track with index $trackIndex"
            )
            mTracksAdded++
            if (mTracksAdded == TOTAL_TRACKS) {
                if (VERBOSE) Log.v(
                    TAG,
                    "Starting Muxer"
                )
                startMuxer()
            }
            trackIndex
        } catch (e: NullPointerException) {
            if (mMuxer == null) {
                Log.w(TAG, "Muxer is not initialized")
                throw IllegalStateException("MediaMuxer is not initialized")
            } else {
                Log.w(TAG, "Invalid MediaFormat $type")
                throw InvalidParameterException("Invalid MediaFormat $type")
            }
        }
    }

    @Synchronized
    fun write(
        inputBuffer: ByteBuffer?,
        trackIndex: Int, bufferInfo: MediaCodec.BufferInfo
    ) {
        if (!mIsAlive) {
            Log.w(
                TAG,
                "writing on muxer after closing, trackIndex:$trackIndex"
            )
            return
        }
        if (USE_BUF_POOL) {
            try {
                var freeFrame: UCEncodedFrame
                synchronized(mFreeFrames!!) { freeFrame = mFreeFrames!!.remove() }
                freeFrame.setValue(inputBuffer, trackIndex, bufferInfo)
                synchronized(mEncodedFrames!!) { mEncodedFrames!!.add(freeFrame) }
            } catch (e: NoSuchElementException) {
                if (VERBOSE) {
                    Log.w(
                        TAG,
                        "Add muxer buffer: " + mEncodedFrames!!.size
                    )
                }
                val tempBufferInfo = MediaCodec.BufferInfo()
                tempBufferInfo[bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs] =
                    bufferInfo.flags
                // TODO: handle OutOfMemoryError
                val tempBuf =
                    ByteBuffer.allocate(bufferInfo.offset + bufferInfo.size)
                tempBuf.put(inputBuffer)
                tempBuf.position(bufferInfo.offset)
                tempBuf.limit(bufferInfo.offset + bufferInfo.size)
                synchronized(
                    mEncodedFrames!!
                ) { mEncodedFrames!!.add(UCEncodedFrame(tempBuf, trackIndex, tempBufferInfo)) }
            }
        } else {
            if (VERBOSE) {
                if (mEncodedFrames!!.size > 10) {
                    Log.e(
                        TAG,
                        "Muxer buffer overflow, trackIndex:$trackIndex"
                    )
                }
            }
            val tempBufferInfo = MediaCodec.BufferInfo()
            tempBufferInfo[bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs] =
                bufferInfo.flags
            val tempBuf =
                ByteBuffer.allocate(bufferInfo.offset + bufferInfo.size)
            tempBuf.put(inputBuffer)
            tempBuf.position(bufferInfo.offset)
            tempBuf.limit(bufferInfo.offset + bufferInfo.size)
            mEncodedFrames!!.add(UCEncodedFrame(tempBuf, trackIndex, tempBufferInfo))
        }
    }

    @Synchronized
    fun close() {
        mIsAlive = false
        try {
            mMuxerThread!!.join()
        } catch (e: InterruptedException) {
            Log.w(TAG, "Failed to join muxer thread")
        }
    }

    override fun run() {
        var currentFrame: UCEncodedFrame
        while (true) {
            if (mEncodedFrames!!.size > 0) {
                try {
                    synchronized(mEncodedFrames!!) { currentFrame = mEncodedFrames!!.remove() }
                } catch (e: NoSuchElementException) {
                    e.printStackTrace()
                    if (!mIsAlive) {
                        break
                    }
                    continue
                }
            } else {
                if (!mIsAlive) {
                    break
                }
                try {
                    Thread.sleep(5)
                } catch (e1: InterruptedException) {
                    e1.printStackTrace()
                }
                continue
            }
            try {
                mMuxer!!.writeSampleData(
                    currentFrame.mTrackIndex,
                    currentFrame.mInputBuffer,
                    currentFrame.mBufferInfo
                )
            } catch (e: NullPointerException) {
                Log.w(TAG, "Muxer is not initialized")
                //Some audio frames are received even after closing muxer, so below line is commented
                //throw new NullPointerException("MediaMuxer is not initialized");
            } catch (e: IllegalStateException) {
                Log.e(
                    TAG,
                    "Failed to write on muxer, track:" + currentFrame.mTrackIndex + ", Size" + currentFrame.mBufferInfo.size
                )
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
            currentFrame.mInputBuffer.clear()
            if (USE_BUF_POOL) {
                synchronized(mFreeFrames!!) { mFreeFrames!!.add(currentFrame) }
            }
        } //End of while
        /* Muxer is closed now release the resources */try {
            if (USE_BUF_POOL) {
                Log.v(
                    TAG,
                    "Releasing muxer:" + mFreeFrames!!.size + "/" + mEncodedFrames!!.size
                )
                mFreeFrames!!.clear()
                mFreeFrames = null
            }
            mEncodedFrames!!.clear()
            mEncodedFrames = null
            mMuxer!!.stop()
            mMuxer!!.release()
            mMuxer = null
            Log.i(TAG, "Muxer closed")
        } catch (e: NullPointerException) {
            Log.w(TAG, "Muxer is already closed")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Unable to close muxer, " + e.message)
            e.printStackTrace()
        }
    }

    private fun startMuxer() {
        try {
            mMuxer!!.start()
            mMuxerThread = Thread(this, "DZMuxer")
            mMuxerThread!!.start()
        } catch (e: NullPointerException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Unable to start muxer, ")
            e.printStackTrace()
        }
    }

    companion object {
        private val TAG = UCMuxer::class.java.simpleName
        private const val VERBOSE = true
        private const val USE_BUF_POOL = true
    }

}