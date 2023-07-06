package com.taknikiniga.documentscanner

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

object Permission {

    fun hasReadExternalStoragePermission(context: Context) = ActivityCompat.checkSelfPermission(context,android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    fun hasWriteExternalStoragePermission(context: Context) = ActivityCompat.checkSelfPermission(context,android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    fun hasCameraPermission(context: Context) = ActivityCompat.checkSelfPermission(context,android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED


    fun checkPermission(context: Context){
        val permissionList = mutableListOf<String>()
        if (!hasCameraPermission(context)) permissionList.add(android.Manifest.permission.CAMERA)
        if (!hasReadExternalStoragePermission(context)) permissionList.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        if (!hasWriteExternalStoragePermission(context)) permissionList.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (permissionList.isNotEmpty()){
            ActivityCompat.requestPermissions(context as Activity,permissionList.toTypedArray(),0)
        }
    }
}