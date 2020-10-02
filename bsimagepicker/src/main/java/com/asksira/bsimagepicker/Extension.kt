package com.asksira.bsimagepicker

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

fun Number.toPx(): Int {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), Resources.getSystem().displayMetrics).toInt()
}

fun Fragment.checkPermission(permission: String, permissionCode: Int) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || context == null) return
    val existingPermissionStatus = ContextCompat.checkSelfPermission(requireContext(), permission)
    if (existingPermissionStatus == PackageManager.PERMISSION_GRANTED) return
    requestPermissions(arrayOf(permission), permissionCode)
}

fun Fragment.isCameraGranted(): Boolean {
    val cameraPermissionGranted = ContextCompat.checkSelfPermission(requireContext(),
        Manifest.permission.CAMERA)
    return cameraPermissionGranted == PackageManager.PERMISSION_GRANTED
}

fun Fragment.isWriteStorageGranted(): Boolean {
    val storagePermissionGranted = ContextCompat.checkSelfPermission(requireContext(),
        Manifest.permission.WRITE_EXTERNAL_STORAGE)
    return storagePermissionGranted == PackageManager.PERMISSION_GRANTED
}

fun Fragment.isReadStorageGranted(): Boolean {
    val storagePermissionGranted = ContextCompat.checkSelfPermission(requireContext(),
        Manifest.permission.READ_EXTERNAL_STORAGE)
    return storagePermissionGranted == PackageManager.PERMISSION_GRANTED
}

fun tryCatch(body: () -> Unit) {
    try {
        body()
    } catch (ex: Exception) {
    }
}
