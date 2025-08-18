package com.example.violenceapp

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializar managers
        biometricAuthManager = BiometricAuthManager(this)
        sharedPreferencesManager = SharedPreferencesManager(this)

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

        if (isAuthenticated) {
            // Una vez autenticado, mostrar la navegación principal
            ViolenceAppNavigation(viewModel = appViewModel)
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
}
