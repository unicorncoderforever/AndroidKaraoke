package com.shri.karaokesdk.audiomodule.coreaudio;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.shri.karaokesdk.audiomodule.model.KaroukeAudioSample;

import java.lang.ref.WeakReference;
import java.util.Objects;

 class AudioRecorder implements  Runnable{

    private AudioRecord recorder;

    private double timeForSamples;
    private AudioMixer mAudioMixer;
    private final Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;

    private static final int SAMPLES_PER_FRAME = 2048; // want to play 2048 (2K) since 2 bytes we use only 1024
    //audio info

    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static final int RECORD_SAMPLE   = 0;
    private static final int START_RECORDING = 1;
    private static final int MSG_QUIT = 6;
    private static final int STOP_RECORDING  = 2;
    private static final boolean VERBOSE  = false;
    private static final String TAG = "AudioRecorder";

    private static final boolean USE_HANDLER = true;

    private int mSampleRate;

    private volatile UCRecorderHandler mHandler;
    public AudioRecorder(int sampleRate){
        mSampleRate = sampleRate;
        timeForSamples = ((1024.0/ mSampleRate) * 1000000.0);
    }



    private void startRecording(){
        Log.e(TAG, "startRecording: "+mSampleRate );
        int bufferSizeInBytes = AudioRecord.getMinBufferSize(mSampleRate,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
              mSampleRate, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, bufferSizeInBytes);
        recorder.startRecording();
    }


    private  void stopRecording(){
        recorder.stop();
        recorder.release();
        mAudioMixer.onRecordingStopped();
    }

    private void readSamples(KaroukeAudioSample sample){
        short[] data = new short[SAMPLES_PER_FRAME];
        recorder.read(data, 0, SAMPLES_PER_FRAME);
        sample.vocalData = data;
        sample.audioPresentationTime = getAudioPresentationTime();
        mAudioMixer.onSampleAvailable(sample);
    }

    public static class UCRecorderHandler extends Handler {
        private WeakReference<AudioRecorder> mWeakEncoder;

        public UCRecorderHandler(AudioRecorder encoder) {
            mWeakEncoder = new WeakReference<AudioRecorder>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            try {

                AudioRecorder recorder = mWeakEncoder.get();
                if (recorder == null) {
                    return;
                }
                switch (what) {
                    case START_RECORDING:
                        recorder.startRecording();
                        break;
                    case STOP_RECORDING:
                        recorder.stopRecording();
                        break;
                    case RECORD_SAMPLE:
                        KaroukeAudioSample sample = (KaroukeAudioSample) inputMessage.obj;
                        recorder.readSamples(sample);
                        break;
                    case MSG_QUIT:
                        Objects.requireNonNull(Looper.myLooper()).quit();
                        break;
                    default:
                        throw new RuntimeException("Unhandled msg what=" + what);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


        }
    }

    @Override
    public void run() {
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new UCRecorderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();
        mReady = mRunning = false;
        mHandler = null;
    }


    public void setStartRecording(AudioMixer audioMixer){
        if (mRunning) {
            if (VERBOSE) Log.w(TAG, "Encoder thread already running");
            return;
        }
        Thread encoderThread = new Thread(this, "AudioRecorder");
        encoderThread.start();
        mRunning = true;
        mAudioMixer = audioMixer;
        while (!mReady) {
            synchronized (mReadyFence) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (USE_HANDLER) {
            mHandler.sendMessage(mHandler.obtainMessage(START_RECORDING));
        } else {
            startRecording();
        }
    }

    public void setStopRecording(){
        if (USE_HANDLER) {
            mHandler.sendMessage(mHandler.obtainMessage(STOP_RECORDING));
        } else {
            stopRecording();
        }

    }

    public void readSamplesFor(KaroukeAudioSample samples){
        if (!mReady) {
            return;
        }
        if (USE_HANDLER) {
            mHandler.sendMessage(mHandler.obtainMessage(RECORD_SAMPLE, samples));
        } else {
            readSamples(samples);
        }
    }
    long mAudioPresentationTime = -1;
    private long getAudioPresentationTime() {
        if (mAudioPresentationTime == -1) {
            mAudioPresentationTime = System.nanoTime() / 1000;
        } else {
            mAudioPresentationTime = (long) (mAudioPresentationTime + timeForSamples);
        }
        return mAudioPresentationTime;
    }



}


