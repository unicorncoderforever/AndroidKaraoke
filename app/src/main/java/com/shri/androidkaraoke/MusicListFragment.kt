package com.shri.androidkaraoke

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.observe
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shri.androidkaraoke.musicpicker.HomeViewModel
import com.shri.androidkaraoke.musicpicker.MusicListAdapter
import com.shri.androidkaraoke.musicpicker.model.UCMusicListModel
import com.shri.dslrphotoeditor.Utility.PermissionUtility


class MusicListFragment : Fragment() {

    private var isPermssionGranted:Boolean = false
    private lateinit var mViewModel:HomeViewModel
    private lateinit var  recyclerView:RecyclerView
    private lateinit var mMusicListAdapter: MusicListAdapter

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.music_list_fragment, container, false)
        if (!PermissionUtility.hasPermissions(activity as Context, PermissionUtility.PERMISSIONS)) {
        this.requestPermissions(PermissionUtility.PERMISSIONS, PermissionUtility.PERMISSION_ALL)
        }
        recyclerView = view.findViewById(R.id.music_list);
        val linearLayoutManager = LinearLayoutManager(activity)
        recyclerView.layoutManager = linearLayoutManager
        mMusicListAdapter =  MusicListAdapter(activity as Context, emptyList<UCMusicListModel>()){
            val bundle = bundleOf("music" to it)
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)
        }
        recyclerView.adapter = mMusicListAdapter
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mViewModel = GenericFactory(application = activity?.application as Application).create(HomeViewModel::class.java)
        mViewModel.model.observe(viewLifecycleOwner,{
            mMusicListAdapter.setData(it)
        })
        view.findViewById<Button>(R.id.load_files).setOnClickListener{
            it.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            loadMusicFiles()
       }
    }

    private fun loadMusicFiles() {
        mViewModel.loadMusicFiles()

    }


    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
         isPermssionGranted = PermissionUtility.hasPermissions(activity as Context,
            PermissionUtility.PERMISSIONS
        )

    }
}