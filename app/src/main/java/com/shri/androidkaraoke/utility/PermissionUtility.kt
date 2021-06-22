package com.shri.dslrphotoeditor.Utility

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.core.app.ActivityCompat


class PermissionUtility{
    companion object {
        val PERMISSION_ALL = 1
        val  PERMISSIONS = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )
         fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
             for (permission in permissions) {
                 if( !(ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)){
                     return false;
                 }
             }
             return true
        }

        fun showToast(context: Context,message: String){
            Toast.makeText(context,message,Toast.LENGTH_SHORT).show()
        }

    }
}