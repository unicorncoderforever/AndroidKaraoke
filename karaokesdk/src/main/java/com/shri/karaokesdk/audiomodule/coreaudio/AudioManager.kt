package com.shri.karaokesdk.audiomodule.coreaudio

import android.content.Context
import android.media.AudioManager
import android.media.AudioTrack
import android.net.Uri
import android.util.Log
import com.shri.karaokesdk.AudioController
import com.shri.karaokesdk.AudioRecorderCallback

import com.shri.karaokesdk.audiomodule.coreaudio.AudioReader.IAudioStatusProvider
import com.shri.karaokesdk.audiomodule.coreaudio.MediaMetaDataRetriever.getAudioMetaData
import com.shri.karaokesdk.audiomodule.model.KaroukeAudioSample
import com.shri.karaokesdk.audiomodule.utility.Utils
import java.io.File
import java.util.*

/**
 * this class is responsible for handling all the task required for the recording the karaoke
 */
internal class AudioManager(private val mContext: Context,val audioRecorderCallback: AudioRecorderCallback) : IAudioStatusProvider, AudioRecorderCallback,
    AudioController {
    private var sampleRate = 44100
    private var mAdudioReader: AudioReader? = null
    private var mAudioRecorder: AudioRecorder? = null
    private var mAudioMixer: AudioMixer? = null
    private var mAudioPlayer: AudioPlayer? = null
    private var mPeriodicNotificationReceived = false
    private var mTimerReader: Timer? = null
    private var mSampleTime: Long = 0
    private var pauseAudio = false;

    private fun startRecoridng(inputFile: File?,outputFile: File) {
        val audioInfo = getAudioMetaData(inputFile!!)
        mAdudioReader = AudioReader(sampleRate, this)
        mAdudioReader!!.setupAudioExtractorForFile(inputFile)
        mAudioMixer = AudioMixer(sampleRate, mContext,this)
        //todo incase audioinfo not required then should see what to do
        mAudioMixer!!.startEncoderThread(outputFile, audioInfo)
        //this is fine
        mAudioPlayer = AudioPlayer(inputFile, sampleRate)
        //this is fine
        val timeRate = Utils.SAMPLE_GENERATED.toFloat() / sampleRate.toFloat() * 1000
        mSampleTime = Math.round(timeRate).toLong()
        mAudioPlayer!!.play()
        //this is also fine
        mAudioRecorder = AudioRecorder(sampleRate)
        mAudioRecorder!!.setStartRecording(mAudioMixer)
        //this is fine too
        mAdudioReader!!.startFetching()
    }

    /**
     * this is method is responsible for the the buffer delay anyways if someone could comeup with the audio latency this logic could be discarded
     */
    private fun computeBufferDelay() {
        mTimerReader = Timer()
        mTimerReader!!.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                Log.e(
                    TAG,
                    "run: "
                )
                if(!pauseAudio)
                readSamples()
            }
        }, 0, mSampleTime)
    }

    //write now the logic is fetching every sample at mSampleTime time
    //write now this feels more accurate compared on periodic update cause its taking time
    //todo please someone come up with computing audio latency, it would be great
    private fun readSamples() {
        val audioData = mAdudioReader!!.audioSamples
        if (audioData != null) {
            mAudioRecorder!!.readSamplesFor(
                KaroukeAudioSample(
                    audioData.sampleData,
                    null,
                    audioData.audioPresentationTime
                )
            )
            mAudioPlayer!!.mAudioTrack!!.write(
                audioData.sampleData,
                0,
                audioData.sampleData.size,
                AudioTrack.WRITE_BLOCKING
            )
        }
    }

    /**
     * stop recording this method will be called
     */
    fun stopRecording() {

        mTimerReader!!.cancel()
        mAdudioReader!!.stopAudioReading()
        mAudioPlayer!!.stop()
        mAudioRecorder!!.setStopRecording()
    }

    /**
     * when the audio reaches end of stream this method will be called
     * this we need to handle properly
     * TODO cause when audio reached EOS still our buffer might hold some data
     */
    override fun audioReachedEndOfStream() {
        mAudioPlayer!!.stop()
    }

    /**
     * when buffer is ready then only we are starting our playeer
     */
    override fun onInitialBufferingComplete() {
        computeBufferDelay()
    }

    override fun onDecoderError() {
        audioRecorderCallback.onRecordingError()
        stopRecording()
    }

    fun getSampleRate(context: Context): Int {
        val am =
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRateStr =
            am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        return sampleRateStr.toInt()
    }

    companion object {
        private const val TAG = "AudioManager"
    }

    init {
        mPeriodicNotificationReceived = false
        sampleRate = getSampleRate(mContext)
    }

    override fun onRecordingComplete(path:String,uri: Uri) {
        audioRecorderCallback.onRecordingComplete(path,uri)
    }

    override fun onRecordingError() {
       audioRecorderCallback.onRecordingError()
    }


    override fun pauseAudioRecording() {
        pauseAudio = true
    }

    override fun resumeAudioRecording() {
        pauseAudio = false
    }

    override fun stopAudioPlaying() {
        stopRecording()
    }

    override fun recordAudio(path: String,outputFile: File) {
      startRecoridng(File(path),outputFile)
    }

    override fun isAudioRecording(): Boolean {
        TODO("Not yet implemented")
    }
}