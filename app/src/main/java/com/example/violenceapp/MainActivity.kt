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

    // Estados para permisos (agregado ubicación)
    var hasMicrophonePermission by mutableStateOf(false)
        private set
    var hasLocationPermission by mutableStateOf(false)
        private set

    // Códigos de solicitud para diferentes tipos de permisos
    private val MICROPHONE_PERMISSION_REQUEST_CODE = 1001
    private val LOCATION_PERMISSION_REQUEST_CODE = 1002

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
                    hasLocationPermission = hasLocationPermission,
                    onRequestMicrophonePermission = ::requestMicrophonePermissionFromCompose,
                    onRequestLocationPermission = ::requestLocationPermissionFromCompose
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

        hasLocationPermission =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
    }

    // Función específica para pedir permisos de micrófono
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
                        MICROPHONE_PERMISSION_REQUEST_CODE
                )
            } else {
                hasMicrophonePermission = hasMicrophone
                Toast.makeText(
                                this,
                                "El permiso de micrófono ya está concedido",
                                Toast.LENGTH_SHORT
                        )
                        .show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                            this,
                            "Error al solicitar permiso de micrófono: ${e.message}",
                            Toast.LENGTH_LONG
                    )
                    .show()
        }
    }

    // Nueva función para pedir permisos de ubicación
    private fun requestLocationPermissionFromCompose() {
        try {
            val permissions = mutableListOf<String>()

            // Verificar permisos actuales de ubicación
            val hasFineLocation =
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

            val hasCoarseLocation =
                    ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

            // Solo agregar permisos que necesitamos
            if (!hasFineLocation) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (!hasCoarseLocation) {
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }

            // Pedir permisos de ubicación
            if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                        this,
                        permissions.toTypedArray(),
                        LOCATION_PERMISSION_REQUEST_CODE
                )
            } else {
                hasLocationPermission = hasFineLocation
                Toast.makeText(
                                this,
                                "Los permisos de ubicación ya están concedidos",
                                Toast.LENGTH_SHORT
                        )
                        .show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                            this,
                            "Error al solicitar permisos de ubicación: ${e.message}",
                            Toast.LENGTH_LONG
                    )
                    .show()
        }
    }

    // Manejar resultado de permisos con el método tradicional (actualizado)
    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            MICROPHONE_PERMISSION_REQUEST_CODE -> {
                var microphoneGranted = false

                // Revisar resultados de micrófono
                for (i in permissions.indices) {
                    if (permissions[i] == Manifest.permission.RECORD_AUDIO) {
                        microphoneGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    }
                }

                hasMicrophonePermission = microphoneGranted

                if (microphoneGranted) {
                    Toast.makeText(this, "🎤 Permiso de micrófono concedido", Toast.LENGTH_SHORT)
                            .show()
                } else {
                    Toast.makeText(
                                    this,
                                    "🎤 Permiso de micrófono necesario para el reconocimiento de voz",
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
            }
            LOCATION_PERMISSION_REQUEST_CODE -> {
                var locationGranted = false

                // Revisar resultados de ubicación
                for (i in permissions.indices) {
                    if (permissions[i] == Manifest.permission.ACCESS_FINE_LOCATION) {
                        locationGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED
                    }
                }

                hasLocationPermission = locationGranted

                if (locationGranted) {
                    Toast.makeText(this, "📍 Permisos de ubicación concedidos", Toast.LENGTH_SHORT)
                            .show()
                } else {
                    Toast.makeText(
                                    this,
                                    "📍 Permisos de ubicación recomendados para enviar ubicación por Telegram",
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
            }
        }
    }
}
