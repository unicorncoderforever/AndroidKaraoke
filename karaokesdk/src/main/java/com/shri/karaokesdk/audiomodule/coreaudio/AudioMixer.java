package com.shri.karaokesdk.audiomodule.coreaudio;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.shri.karaokesdk.AudioRecorderCallback;
import com.shri.karaokesdk.audiomodule.IMusicExpoterCallback;
import com.shri.karaokesdk.audiomodule.filter.Delay;
import com.shri.karaokesdk.audiomodule.filter.GainProcessor;
import com.shri.karaokesdk.audiomodule.model.KaroukeAudioSample;
import com.shri.karaokesdk.audiomodule.model.UCAudioInfo;
import com.shri.karaokesdk.audiomodule.utility.Utils;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import static com.shri.karaokesdk.audiomodule.utility.Utils.mix;
import static com.shri.karaokesdk.audiomodule.utility.Utils.scaleSamples;


 class AudioMixer  implements Runnable, IMusicExpoterCallback {

    private static final String TAG = AudioMixer.class.getSimpleName();
    private static final boolean VERBOSE = false;
    private static UCAudioInfo mAudioInfo = null;

    /* If Handler is used to notify audio filter change,
     * recorder/extractor receives notification before audio processor.
     */
    private static final boolean USE_HANDLER = true;

    //Constants for handleMsg

    private static final int MSG_QUIT = 6;
    private static final int MSG_PREPARE_ENCODER = 0;
    private static final int MSG_PREPARE_ENCODER_AUDIO_INFO = 4;
    private static final int MSG_STOP_AUDIO_RECORDING = 2;
    private static final int MIX_AUDIO_SAMPLES = 3;


    private UCVoiceExporter mMovieExporter;


    // ----- accessed by multiple threads -----
    private volatile AudioMixer.UCRecorderHandler mHandler;
    private final Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;

    private int mSampleRate = 44100;


    private long mInitialAudioTimestamp = -1;


    int currentFilter = -1;

    private String mOutputPath;

    private GainProcessor gainProcessor;
    private Context mContext;
    private AudioRecorderCallback mAudioRecorderCallback;

    public AudioMixer(int sampleRate, Context context, AudioRecorderCallback recorderCallback){
        mSampleRate = sampleRate;
        mContext = context;
        mAudioRecorderCallback = recorderCallback;
    }

    public void startEncoderThread(File inOutputFile, UCAudioInfo audioInfo) {
        if (VERBOSE) Log.d(TAG, "Encoder: startRecording()");
        mAudioInfo = audioInfo;
        mOutputPath = inOutputFile.getAbsolutePath();
        if (mRunning) {
            if (VERBOSE) Log.w(TAG, "Encoder thread already running");
            return;
        }
        Thread encoderThread = new Thread(this, "UCVoiceEncoder");
        encoderThread.start();
        mRunning = true;
        while (!mReady) {
            synchronized (mReadyFence) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        if (VERBOSE) Log.i(TAG, "Current filter: " + currentFilter);
        mInitialAudioTimestamp = -1;
        if (USE_HANDLER) {
            mHandler.sendMessage(mHandler.obtainMessage(MSG_PREPARE_ENCODER_AUDIO_INFO, inOutputFile));
        } else {
            handlePrepareEncoder(inOutputFile,mAudioInfo);
        }
    }


    public boolean isRecording() {
        return mRunning;
    }


    public void handleStopAudioRecording() {
        mMovieExporter.stopAudioRecording();
        Log.e(TAG, "stop audio");
        checkForEndOfRecording();

    }

    private void checkForEndOfRecording() {
        releaseEncoder();
        mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
    }


    public void handleAudioSampleAvailable(byte[] sData, long audioPresentationTimeNs) {
        if (mMovieExporter != null) {
            if (VERBOSE) Log.i("sample", "handleAudioSample");
            mMovieExporter.drainAudioEncoder(sData, audioPresentationTimeNs, false);
        }
    }


    private void handlePrepareEncoder(File inOutputFile) {
        try {
            mMovieExporter = new UCVoiceExporter(inOutputFile, null, mSampleRate);
            mMovieExporter.initExporter();
            mMovieExporter.prepareVideoEncoder();
        }catch (IOException e) {
            mAudioRecorderCallback.onRecordingError();
            e.printStackTrace();
        }

    }
    private void handlePrepareEncoder(File inOutputFile, UCAudioInfo audioInfo) {
        try {
            Log.e(TAG, "handlePrepareEncoder: with audio info"+inOutputFile.getAbsolutePath() );
            mMovieExporter = new UCVoiceExporter(inOutputFile,audioInfo,mSampleRate);
            mMovieExporter.initExporter();
            gainProcessor = new GainProcessor(Delay.defaultInputGain/100);
            mMovieExporter.prepareVideoEncoder();
        } catch (IOException e) {
            mAudioRecorderCallback.onRecordingError();
            e.printStackTrace();
        }
    }

    private void releaseEncoder() {
        mMovieExporter.release();
        addAudioToMediaStore();
    }

    /**
     * todo this method need to be changes we need to add the output path to the database
     */
    private void addAudioToMediaStore() {
        MediaScannerConnection.scanFile(mContext, new String[]{mOutputPath}, null, new MediaScannerConnection.OnScanCompletedListener() {
            @Override
            public void onScanCompleted(String path, Uri uri) {
            mAudioRecorderCallback.onRecordingComplete(path,uri);
            }
        });
    }


    public void startEncoderThread(String videoFilePath) {
        handlePrepareEncoder(new File(videoFilePath));
    }

    public void stopAudioRecording() {
        if (USE_HANDLER) {
            if (mHandler != null) {
                mHandler.sendMessage(mHandler
                        .obtainMessage(MSG_STOP_AUDIO_RECORDING));
            }
        } else {
            handleStopAudioRecording();
        }
    }

    public void onSampleAvailable(byte[] samples, long presentationTime) {
        if (!mReady) {
            return;
        }
         if (mInitialAudioTimestamp == -1) {
            mInitialAudioTimestamp = presentationTime;
        }
        presentationTime = (presentationTime - mInitialAudioTimestamp);
        handleAudioSampleAvailable(samples, presentationTime);

    }


    public void onSampleAvailable(KaroukeAudioSample sample) {
        Log.e(TAG, "onSampleAvailable: "+sample.sampleData.length );
        if (!mReady) {
            return;
        }
        if (mInitialAudioTimestamp == -1) {
            mInitialAudioTimestamp = sample.audioPresentationTime;
        }

        sample.audioPresentationTime = (sample.audioPresentationTime - mInitialAudioTimestamp) * 1000;
        if (USE_HANDLER) {
            mHandler.sendMessage(mHandler.obtainMessage(MIX_AUDIO_SAMPLES, sample));
        } else {
            mixAudioData(sample.sampleData,sample.vocalData,sample.audioPresentationTime);
        }
    }

    @Override
    public void onRawSampleReceive(short[] samples, long presentayionTime) {
        onSampleAvailable(Utils.ShortToByte_ByteBuffer_Method(samples), presentayionTime);
    }

    @Override
    public void onRecordingStopped() {
        stopAudioRecording();

    }

    /**
     * Encoder thread entry point.  Establishes Looper/Handler and waits for messages.
     * <p/>
     *
     * @see java.lang.Thread#run()
     */
    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare();
        synchronized (mReadyFence) {
            mHandler = new AudioMixer.UCRecorderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        if (VERBOSE) Log.d(TAG, "Encoder thread exiting");
        mReady = mRunning = false;
        mHandler = null;
    }

    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    public static class UCRecorderHandler extends Handler {
        private WeakReference<AudioMixer> mWeakEncoder;

        public UCRecorderHandler(AudioMixer encoder) {
            mWeakEncoder = new WeakReference<AudioMixer>(encoder);
        }
        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            if (VERBOSE) Log.d(TAG, "Entered handle message : " + what);
            Object obj = inputMessage.obj;

            try {

                AudioMixer encoder = mWeakEncoder.get();
                if (encoder == null) {
                    if (VERBOSE) Log.w(TAG, "EncoderHandler.handleMessage: encoder is null");
                    return;
                }
                if (VERBOSE)
                    Log.v(TAG, "UCVoiceEncoderHandler.handleMessage : " + what);

                switch (what) {
                    case MSG_QUIT:
                        Looper.myLooper().quit();
                        break;
                    case MSG_PREPARE_ENCODER:
                        File outputFile = (File) inputMessage.obj;
                        encoder.handlePrepareEncoder(outputFile);
                        break;

                    case MSG_STOP_AUDIO_RECORDING:
                        encoder.handleStopAudioRecording();
                        break;
                    case MSG_PREPARE_ENCODER_AUDIO_INFO:
                        File outputFile1 = (File) inputMessage.obj;
                        encoder.handlePrepareEncoder(outputFile1,mAudioInfo);
                        break;
                    case MIX_AUDIO_SAMPLES:
                        KaroukeAudioSample rawSample = (KaroukeAudioSample) inputMessage.obj;
                        encoder.mixAudioData(rawSample.sampleData,rawSample.vocalData,rawSample.audioPresentationTime);
                        break;
                    default:
                        throw new RuntimeException("Unhandled msg what=" + what);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (VERBOSE) Log.d(TAG, "Handle message end");
        }



    }

    private void mixAudioData(byte[] audioData, short[] vocalDataShort, long audioPresentationTime) {
        if(VERBOSE)
        Log.e(TAG, "mixAudioData: "+audioData.length);

        short[] shortAudioData = Utils.byteToShort(audioData);
        float[] vocalData = new float[vocalDataShort.length];
        Utils.shortToFloat(vocalDataShort, vocalData);
        gainProcessor.process(vocalData);
        float[] backgroudAudio = new float[shortAudioData.length];
        if(true) {
            short[] vocalLessAudio = Utils.getVocalessAudio(shortAudioData);
            scaleSamples(vocalLessAudio, 0, 1024, 0.5f);
            Utils.shortToFloat(vocalLessAudio, backgroudAudio);
        }else{
            Utils.shortToFloat(shortAudioData, backgroudAudio);
        }
        float[] vocalDataResule = new float[vocalData.length];
        for(int i = 0;i < backgroudAudio.length;i = i+2) {
            vocalDataResule[i] = mix(vocalData[i],backgroudAudio[i]);
            vocalDataResule[i +1] = mix(vocalData[i+1],backgroudAudio[i+1]);
        }
        Utils.floatToShort(vocalDataResule,vocalDataResule.length,shortAudioData);
        onSampleAvailable(Utils.ShortToByte_ByteBuffer_Method(shortAudioData), audioPresentationTime);
    }




}
