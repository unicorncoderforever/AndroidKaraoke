package com.shri.androidkaraoke.musicpicker.model

import java.io.Serializable

class UCMusicListModel : Serializable {
    var musicTitle: String? = null
    var musicPath: String? = null
    var musicArtist: String? = null
    var albumId: Long = 0
    /**
     * @param duration in ms.
     */


}

