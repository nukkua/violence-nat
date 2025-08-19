package com.example.violenceapp.permissions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: ComponentActivity) {

    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val permissionLauncher: ActivityResultLauncher<String> =
            activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                    isGranted ->
                onPermissionResult?.invoke(isGranted)
            }

    /** Verifica si el permiso de micrófono está concedido */
    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Solicita el permiso de micrófono
     * @param onResult callback que se ejecuta con el resultado del permiso
     */
    fun requestMicrophonePermission(onResult: (Boolean) -> Unit) {
        onPermissionResult = onResult

        if (hasMicrophonePermission()) {
            // Ya tiene el permiso
            onResult(true)
        } else {
            // Pedir el permiso
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    /** Verifica si se debe mostrar la explicación del permiso */
    fun shouldShowRequestPermissionRationale(): Boolean {
        return activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
    }

    /** Verifica y solicita automáticamente el permiso si es necesario */
    fun ensureMicrophonePermission(onResult: (Boolean) -> Unit) {
        if (hasMicrophonePermission()) {
            onResult(true)
        } else {
            requestMicrophonePermission(onResult)
        }
    }
}
