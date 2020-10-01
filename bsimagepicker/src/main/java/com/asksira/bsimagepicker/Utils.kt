package com.asksira.bsimagepicker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

object Utils {
    @JvmStatic
    fun dp2px(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), Resources.getSystem().displayMetrics).toInt()
    }

    @JvmStatic
    fun checkPermission(fragment: Fragment, permissionString: String, permissionCode: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || fragment.context == null) return
        val existingPermissionStatus = ContextCompat.checkSelfPermission(fragment.context!!,
            permissionString)
        if (existingPermissionStatus == PackageManager.PERMISSION_GRANTED) return
        fragment.requestPermissions(arrayOf(permissionString), permissionCode)
    }

    @JvmStatic
    fun isReadStorageGranted(context: Context?): Boolean {
        val storagePermissionGranted = ContextCompat.checkSelfPermission(context!!,
            Manifest.permission.READ_EXTERNAL_STORAGE)
        return storagePermissionGranted == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun isWriteStorageGranted(context: Context?): Boolean {
        val storagePermissionGranted = ContextCompat.checkSelfPermission(context!!,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return storagePermissionGranted == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun isCameraGranted(context: Context?): Boolean {
        val cameraPermissionGranted = ContextCompat.checkSelfPermission(context!!,
            Manifest.permission.CAMERA)
        return cameraPermissionGranted == PackageManager.PERMISSION_GRANTED
    }
}