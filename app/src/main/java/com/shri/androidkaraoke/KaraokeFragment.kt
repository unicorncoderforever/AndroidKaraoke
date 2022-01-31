package com.shri.androidkaraoke

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.shri.androidkaraoke.musicpicker.model.UCMusicListModel
import com.shri.androidkaraoke.utility.UCAudioTrackPlayer
import com.shri.androidkaraoke.utility.Utils
import com.shri.karaokesdk.AudioRecorderCallback
import com.shri.karaokesdk.ErrorMessage
import com.shri.karaokesdk.KaraokeAudioManager

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class KaraokeFragment : Fragment() {

    private lateinit var musicModel :UCMusicListModel
    private lateinit var karaokeAudioManager: KaraokeAudioManager
    private lateinit var karaokeAudio: TextView
    private lateinit var mStartRecording:Button
    private lateinit var mPauseRecording:Button
    private lateinit var mStopRecording:Button
    private var  trackPlayer:UCAudioTrackPlayer? = null
    private lateinit var  controllerView :View

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.karaoke_fragment, container, false)
        controllerView = view.findViewById(R.id.player_container)
        karaokeAudio = view.findViewById(R.id.karaoke_name)
        mPauseRecording = view.findViewById(R.id.pause_recording)
        mStartRecording = view.findViewById(R.id.start_recording)
        mStopRecording = view.findViewById(R.id.stop_recording)
        return  view
    }


    private  val TAG = "KaraokeFragment"
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        musicModel = arguments?.getSerializable("music") as UCMusicListModel
        view.findViewById<Button>(R.id.start_recording).setOnClickListener {
            it.visibility = View.GONE
            mStopRecording.visibility = View.VISIBLE
            mPauseRecording.visibility = View.VISIBLE
            karaokeAudioManager = KaraokeAudioManager(this.requireContext(),object:AudioRecorderCallback{
                override fun onRecordingComplete(path:String,uri: Uri) {
                    val handlerThread =  Handler(Looper.getMainLooper());
                    handlerThread.post({
                        controllerView.visibility = View.VISIBLE
                        karaokeAudio.text = path
                        trackPlayer = UCAudioTrackPlayer(path)
                        trackPlayer?.initializeTrack(context)
                        trackPlayer?.playSound()
                    })
                }

                override fun onRecordingError(errorMessage: ErrorMessage) {
                //TODO show some error message
                    Log.e(TAG, "onRecordingError: "+errorMessage.errorMessage)
                }

            })
            Utils.getNextOutputFile(this.requireContext()).let {
                controllerView.visibility = View.GONE
                trackPlayer?.let {
                    it.stopPlaying()
                }
                musicModel.musicPath?.let {path->
                    karaokeAudioManager.recordAudio(path,it)
                }

            }
        }
        view.findViewById<Button>(R.id.stop_recording).setOnClickListener {
                it.visibility = View.GONE
                mStartRecording.visibility = View.VISIBLE
                mPauseRecording.visibility = View.GONE
                karaokeAudioManager.stopAudioPlaying()
        }

        view.findViewById<Button>(R.id.play_stop).setOnClickListener{
            trackPlayer?.let {player->
                if(player.isPlaying()) {
                    player.pausePlayer()
                    it as Button
                    it.text = "Play"
                }else{
                    it as Button
                    it.text  =  "pause/stop"
                    player.pausePlayer()
                }
            }
        }

        view.findViewById<Button>(R.id.pause_recording).setOnClickListener {
            if(karaokeAudioManager.isAudioRecording()){
                it as Button
                it.text = "Resume Recording"
                karaokeAudioManager.pauseAudioRecording()
            }else{
                it as Button
                it.text = "pause Recording"
                karaokeAudioManager.resumeAudioRecording()
            }
        }

    }
}