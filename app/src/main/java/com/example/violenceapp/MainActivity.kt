package com.example.violenceapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.violenceapp.auth.BiometricAuthManager
import com.example.violenceapp.data.SharedPreferencesManager
import com.example.violenceapp.ui.navigation.ViolenceAppNavigation
import com.example.violenceapp.ui.screens.AuthScreen
import com.example.violenceapp.ui.theme.ViolenceAppTheme
import com.example.violenceapp.viewmodel.AppViewModel
import com.example.violenceapp.viewmodel.AppViewModelFactory

class MainActivity : FragmentActivity() {
    private lateinit var biometricAuthManager: BiometricAuthManager
    private lateinit var sharedPreferencesManager: SharedPreferencesManager

    // Estados para permisos
    var hasMicrophonePermission by mutableStateOf(false)
        private set

    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        biometricAuthManager = BiometricAuthManager(this)
        sharedPreferencesManager = SharedPreferencesManager(this)

        checkInitialPermissions()

        setContent {
            ViolenceAppTheme {
                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) { AppNavigationWrapper() }
            }
        }
    }

    @Composable
    fun AppNavigationWrapper() {
        var isAuthenticated by remember { mutableStateOf(false) }

        // Crear ViewModel con Factory
        val appViewModel: AppViewModel =
                viewModel(factory = AppViewModelFactory(sharedPreferencesManager))

        // IMPORTANTE: Inicializar el contexto en el ViewModel
        LaunchedEffect(appViewModel) { appViewModel.initializeContext(this@MainActivity) }

        if (isAuthenticated) {
            // Una vez autenticado, mostrar la navegación principal
            ViolenceAppNavigation(
                    viewModel = appViewModel,
                    hasMicrophonePermission = hasMicrophonePermission,
                    onRequestMicrophonePermission = ::requestMicrophonePermissionFromCompose
            )
        } else {
            // Mostrar pantalla de autenticación
            AuthScreen(
                    biometricManager = biometricAuthManager,
                    onAuthenticationSuccess = { isAuthenticated = true },
                    onShowSettings = {
                        val intent = Intent(Settings.ACTION_BIOMETRIC_ENROLL)
                        startActivity(intent)
                    }
            )
        }
    }

    private fun checkInitialPermissions() {
        hasMicrophonePermission =
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
    }

    // Función específica para llamadas desde Compose
    private fun requestMicrophonePermissionFromCompose() {
        try {
            val permissions = mutableListOf<String>()

            // Verificar permisos actuales
            val hasMicrophone =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED

            val hasNotifications =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    } else true

            // Solo agregar permisos que realmente necesitamos
            if (!hasMicrophone) {
                permissions.add(Manifest.permission.RECORD_AUDIO)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifications) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            // Pedir permisos
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                        this,
                        permissions.toTypedArray(),
                        PERMISSION_REQUEST_CODE
                )
            } else {
                hasMicrophonePermission = hasMicrophone
                Toast.makeText(this, "Los permisos ya están concedidos", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al solicitar permisos: ${e.message}", Toast.LENGTH_LONG)
                    .show()
        }
    }

    // Manejar resultado de permisos con el método tradicional
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            var microphoneGranted = false

            // Revisar resultados
            for (i in permissions.indices) {
                if (permissions[i] == Manifest.permission.RECORD_AUDIO) {
                    microphoneGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                }
            }

            hasMicrophonePermission = microphoneGranted

            if (microphoneGranted) {
                Toast.makeText(this, "Permiso de micrófono concedido", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                                this,
                                "Permiso de micrófono necesario para el funcionamiento",
                                Toast.LENGTH_LONG
                        )
                        .show()
            }
        }
    }
}
