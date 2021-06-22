package com.shri.karaokesdk.audiomodule.coreaudio;

import android.annotation.SuppressLint;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

import com.shri.karaokesdk.audiomodule.model.UCAudioSample;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Queue;


@SuppressLint("NewApi")
class UCAudioEncoder implements Runnable {
    private static final String TAG = UCAudioEncoder.class.getSimpleName();
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
    private static final long TIMEOUT_USEC = 5000;//10000;
    private static final long AUDIO_TIMESTAMP_CORRECTION = 46439;
    private static final boolean USE_BUFFER_POOL = true;

    public static final boolean VERBOSE = false;

    private volatile boolean mIsAlive;
    private int mTrackIndex;
    private long mPreviousPresentationTime;

    private MediaCodec mAudioEncoder;
    private UCMuxer mMuxer;
    private MediaCodec.BufferInfo mBufferInfo;
    private ByteBuffer[] mEncoderOutputBuffers;
    private Thread mAudioEncThread;

    private final Queue<UCAudioSample> mRawFrames;
    private Queue<UCAudioSample> mFreeFrames;

    public UCAudioEncoder() {
        mIsAlive = false;
        mRawFrames = new LinkedList<>();
        if (USE_BUFFER_POOL) {
            mFreeFrames = new LinkedList<>();
        }
    }

    public void start(UCMuxer muxer, int channelCount, int sampleRateinHZ, int bitRate) {
        mMuxer = muxer;
        int channelConfig = channelCount > 1 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateinHZ,
                channelConfig, AudioFormat.ENCODING_PCM_16BIT);
       MediaFormat audioFormat = new MediaFormat();
        audioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
        Log.e(TAG, "start: "+sampleRateinHZ+" channel count "+channelCount + " bit rate "+bitRate);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectERLC);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRateinHZ);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
//    		int bitRate = sampleRateInHz * 2 * 8;
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSizeInBytes);
        try {
            mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
            if (mAudioEncoder == null) {
                throw new IOException("Failed to create mediacoded " + AUDIO_MIME_TYPE);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialise Audio encoder, " + e.getMessage());
            return;
        }
        mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.start();
        mIsAlive = true;
        mAudioEncThread = new Thread(this, "AudioEncThr");
        mAudioEncThread.setPriority(Thread.MAX_PRIORITY - 1);
        mAudioEncThread.start();
    }

    public boolean start(UCMuxer muxer, int channelCount, int sampleRateinHZ, int bitRate, String mime) {
        mMuxer = muxer;
        int channelConfig = channelCount > 1 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateinHZ,
                channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        MediaFormat audioFormat = new MediaFormat();
        audioFormat.setString(MediaFormat.KEY_MIME, AUDIO_MIME_TYPE);
        Log.e(TAG, "start: "+sampleRateinHZ+" channel count "+channelCount + " bit rate "+bitRate);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectERLC);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRateinHZ);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelCount);
//    		int bitRate = sampleRateInHz * 2 * 8;
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSizeInBytes);
        try {
            mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
            if (mAudioEncoder == null) {
                throw new IOException("Failed to create mediacoded " + AUDIO_MIME_TYPE);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialise Audio encoder, " + e.getMessage());
            return false;
        }
        mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.start();
        mIsAlive = true;
        mAudioEncThread = new Thread(this, "AudioEncThr");
        mAudioEncThread.setPriority(Thread.MAX_PRIORITY - 1);
        mAudioEncThread.start();
        return true;
    }
    public void stopEncoder() {
        mIsAlive = false;
        Log.i(TAG, "Stop audio encoder: " + mRawFrames.size());
        try {
            mAudioEncThread.join();
        } catch (InterruptedException e) {
            Log.w(TAG, "Failed to join audio encoder thread");
        }
    }

    public synchronized boolean write(byte[] inputFrame, long presentaionTime, boolean isEof) {
        if (!mIsAlive) {
            Log.e(TAG, "write: AudioEncoder is not alive");
            return false;
        }
        try {
            UCAudioSample rawFrame = mFreeFrames.remove();
            rawFrame.setValue(inputFrame, presentaionTime, isEof);
            mRawFrames.add(rawFrame);
        } catch (NoSuchElementException e) {
            mRawFrames.add(new UCAudioSample(inputFrame, presentaionTime, isEof));
        }
        return true;
    }

    @Override
    public void run() {
        ByteBuffer[] inputBuffers = mAudioEncoder.getInputBuffers();
        mEncoderOutputBuffers = mAudioEncoder.getOutputBuffers();
        mBufferInfo = new MediaCodec.BufferInfo();
        int inputBufferIndex;
        UCAudioSample currentFrame;
        if (VERBOSE) {
            Log.i(TAG, "DZAudioEncoder started");
        }
        while (true) {
//            long startTimeMs = System.currentTimeMillis();

            try {
//                synchronized (mRawFrames) {
                currentFrame = mRawFrames.remove();
//                }
            } catch (NoSuchElementException e) {
                //Raw frame queue is empty
                if (!mIsAlive) {
                    break;
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e1) {
                    //need not to do anything
                }
                continue;
            }

            // write onto encoder buffer
            try {
                inputBufferIndex = mAudioEncoder.dequeueInputBuffer(TIMEOUT_USEC);
            } catch (Exception e) {
                Log.w(TAG, "Failed to write onto input buffer");
                continue;
            }

            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(currentFrame.getInputFrame());
                long presentationTimeUs = currentFrame.getPresentaionTime() / 1000;
                if (currentFrame.isEof()) {
                    if (VERBOSE) {
                        Log.i(TAG, "EOS received in offerEncoder");
                    }
                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0,
                            currentFrame.getInputFrame().length, presentationTimeUs,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    if (VERBOSE)
                        Log.i(TAG, "offer :" + currentFrame.getPresentaionTime());
                    mAudioEncoder.queueInputBuffer(inputBufferIndex, 0,
                            currentFrame.getInputFrame().length, presentationTimeUs, 0);
                }
            }

            // encode and read from output buffer
            drainEncoder(currentFrame.isEof());
            if (USE_BUFFER_POOL) {
                mFreeFrames.add(currentFrame);
            }
//            long encodeTime = (System.currentTimeMillis() - startTimeMs);
//            Log.v(TAG, "Audio Encode time:" + encodeTime);

        }// end of encode loop

        try {
            mAudioEncoder.stop();
            mRawFrames.clear();
            if (USE_BUFFER_POOL) {
                mFreeFrames.clear();
            }
            mAudioEncoder.release();
            mAudioEncoder = null;
            Log.d(TAG, "Audio encoder released");
        } catch (NullPointerException e) {
            Log.e(TAG, "Release audio Encoder: illegal state");
        }
    }

    private void drainEncoder(boolean isEof) {
        if (VERBOSE) {
            if (isEof) {
                Log.d(TAG, "sending EOS to encoder");
            }
        }

        while (true) {
            int encoderStatus = mAudioEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus < 0) {
                if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // should happen before receiving buffers, and should only happen once
                    MediaFormat newFormat = mAudioEncoder.getOutputFormat();
                    mTrackIndex = mMuxer.addTrack(newFormat, AUDIO_MIME_TYPE);
                    if (VERBOSE)
                        Log.d(TAG, " audio encoder output format changed: " + newFormat
                                + ". Added track index: " + mTrackIndex);
                } else {
                    if (VERBOSE) {
                        Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                                encoderStatus + ", EOF status: " + isEof);
                    }
                }
                return;
            } else {
                if (handleEncodedFrames(isEof, encoderStatus)) return; // out of while
            }
        }//end of while
    }

    private boolean handleEncodedFrames(boolean isEof, int encoderStatus) {
        ByteBuffer encodedData = mEncoderOutputBuffers[encoderStatus];

        if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status. Ignore it.
            if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
            mAudioEncoder.releaseOutputBuffer(encoderStatus, false);
            return false;
        }

        if (mBufferInfo.size != 0) {
            if (mBufferInfo.presentationTimeUs < mPreviousPresentationTime) {
                Log.w(TAG, "audio Modification");
                mBufferInfo.presentationTimeUs = mPreviousPresentationTime + AUDIO_TIMESTAMP_CORRECTION;
            }
            mPreviousPresentationTime = mBufferInfo.presentationTimeUs;
            encodedData.position(0);
//            encodedData.position(mBufferInfo.offset);
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
            mMuxer.write(encodedData, mTrackIndex, mBufferInfo);
            if (VERBOSE)
                Log.v("info", "A write Presentation Time : " + mBufferInfo.presentationTimeUs + " -");
        }

        if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
            if (VERBOSE) {
                if (!isEof) {
                    Log.e(TAG, " audio reached end of stream unexpectedly");
                } else {

                    Log.d(TAG, "reached end of stream");
                }
            }
            mAudioEncoder.releaseOutputBuffer(encoderStatus, false);
            return true;
        }
        mAudioEncoder.releaseOutputBuffer(encoderStatus, false);
        return false;
    }

    private boolean handleEncodedFrames2(boolean isEof, int encoderStatus) {
        ByteBuffer encodedData = mEncoderOutputBuffers[encoderStatus];

        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // The codec config data was pulled out and fed to the muxer when we got
            // the INFO_OUTPUT_FORMAT_CHANGED status. Ignore it.
            if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
            mAudioEncoder.releaseOutputBuffer(encoderStatus, false);
            return false;
        }

        if (mBufferInfo.size != 0) {
            if (mBufferInfo.presentationTimeUs < mPreviousPresentationTime) {
                Log.w(TAG, "audio Modification");
                mBufferInfo.presentationTimeUs = mPreviousPresentationTime + AUDIO_TIMESTAMP_CORRECTION;
            }
            mPreviousPresentationTime = mBufferInfo.presentationTimeUs;
            encodedData.position(0);
//            encodedData.position(mBufferInfo.offset);
            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
            mMuxer.write(encodedData, mTrackIndex, mBufferInfo);
            if (VERBOSE)
                Log.v("info", "A write Presentation Time : " + mBufferInfo.presentationTimeUs + " -");
        }

        if (mBufferInfo.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
            if (VERBOSE) {
                if (!isEof) {
                    Log.e(TAG, " audio reached end of stream unexpectedly");
                } else {

                    Log.d(TAG, "reached end of stream");
                }
            }
            mAudioEncoder.releaseOutputBuffer(encoderStatus, false);
            return true;
        }
        mAudioEncoder.releaseOutputBuffer(encoderStatus, false);
        return false;
    }

    /**
     * 'drainEncoder()' is optimized version of this method
     */
    private void drainEncoder2(UCAudioSample currentFrame) {
        if (currentFrame.isEof()) {
            if (VERBOSE) Log.d(TAG, "sending EOS to encoder");
        }
        if (VERBOSE) Log.d(TAG, "drainEncoder(" + currentFrame.isEof() + ")");
        ByteBuffer[] encoderOutputBuffers = mAudioEncoder.getOutputBuffers();

        while (true) {

            int encoderStatus = mAudioEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!currentFrame.isEof()) {
                    return; // out of while
                } else {
                    if (VERBOSE)
                        Log.d(TAG, " audio :no output available, spinning to await EOS");
                    return;
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = mAudioEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                MediaFormat newFormat = mAudioEncoder.getOutputFormat();
                mTrackIndex = mMuxer.addTrack(newFormat, AUDIO_MIME_TYPE);
                if (VERBOSE) Log.d(TAG, " audio encoder output format changed: " + newFormat
                        + ". Added track index: " + mTrackIndex);
            } else if (encoderStatus < 0) {
                if (VERBOSE) {
                    Log.w(TAG,
                            "unexpected result from encoder.dequeueOutputBuffer: "
                                    + encoderStatus);
                }
                return;
                // let's ignore it
            } else {
                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    // The codec config data was pulled out and fed to the muxer
                    // when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status. Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (mBufferInfo.presentationTimeUs < mPreviousPresentationTime) {
                        Log.e(TAG, "audio Modification");
                        mBufferInfo.presentationTimeUs = mPreviousPresentationTime + 46439;
                    }
                    mPreviousPresentationTime = mBufferInfo.presentationTimeUs;

//					Log.i("loop",""+loop + " "+mBufferInfo.presentationTimeUs);
                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);
                    if (VERBOSE)
                        Log.i("info", "A write Presentation Time : " + mBufferInfo.presentationTimeUs + " -");
                    mMuxer.write(encodedData, mTrackIndex, mBufferInfo);
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!currentFrame.isEof()) {
                        if (VERBOSE) Log.d(TAG, " audio reached end of stream unexptectedly");
                    } else {

                        if (VERBOSE) Log.d(TAG, "reached end of stream reached");
                    }
                    mAudioEncoder.releaseOutputBuffer(encoderStatus, false);
                    return; // out of while
                }
                mAudioEncoder.releaseOutputBuffer(encoderStatus, false);
            }
        }//end of while
    }
}
