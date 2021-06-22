package com.shri.androidkaraoke.musicpicker

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shri.androidkaraoke.R
import com.shri.androidkaraoke.musicpicker.model.UCMusicListModel
import java.util.*


// Extends the Adapter class to RecyclerView.Adapter
// and implement the unimplemented methods
class MusicListAdapter // Constructor for initialization
    (
    var context: Context,
    var musicList: List<UCMusicListModel>,
    val onItemClicked: (UCMusicListModel) -> Unit

) :
    RecyclerView.Adapter<MusicListAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View =
            LayoutInflater.from(parent.context).inflate(R.layout.music_layout, parent, false)
        // Passing view to ViewHolder
        return ViewHolder(view){
            onItemClicked(musicList[it])
        }
    }

    // Binding data to the into specified position
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // TypeCast Object to int type

        holder.text.text = musicList[position].musicTitle
    }

    override fun getItemCount(): Int {
        // Returns number of items
        // currently available in Adapter
        return musicList.size
    }

    fun setData(it: ArrayList<UCMusicListModel>) {
        this.musicList = it
        notifyDataSetChanged()
    }

    // Initializing the Views
    inner class ViewHolder(view: View, onItemClicked: (Int) -> Unit) : RecyclerView.ViewHolder(view) {
        var text: TextView
        init {
            itemView.setOnClickListener {
                onItemClicked(adapterPosition)
            }
            text = view.findViewById<View>(R.id.courseName) as TextView
        }
    }

}