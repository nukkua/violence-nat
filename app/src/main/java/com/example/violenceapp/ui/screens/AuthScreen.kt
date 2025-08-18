package com.example.violenceapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.violenceapp.auth.BiometricAuthManager

@Composable
fun AuthScreen(
        biometricManager: BiometricAuthManager,
        onAuthenticationSuccess: () -> Unit,
        onShowSettings: () -> Unit = {}
) {
    var authState by remember { mutableStateOf(AuthState.WAITING) }
    var errorMessage by remember { mutableStateOf("") }
    val biometricStatus = remember { biometricManager.isBiometricAvailable() }

    LaunchedEffect(Unit) {
        biometricManager.setAuthenticationCallback(
                object : BiometricAuthManager.AuthenticationCallback {
                    override fun onAuthenticationSucceeded() {
                        // Ir directo al HomeScreen sin mostrar estado de éxito
                        onAuthenticationSuccess()
                    }

                    override fun onAuthenticationFailed() {
                        authState = AuthState.FAILED
                        errorMessage = "Autenticación fallida. Inténtalo de nuevo."
                    }

                    override fun onAuthenticationError(errorMsg: String) {
                        authState = AuthState.ERROR
                        errorMessage = errorMsg
                    }
                }
        )

        // Iniciar autenticación automáticamente si está disponible
        if (biometricStatus == BiometricAuthManager.BiometricStatus.AVAILABLE) {
            authState = AuthState.AUTHENTICATING
            biometricManager.authenticate()
        }
    }

    Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
    ) {
        // App Logo/Header
        Card(
                modifier = Modifier.size(120.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "🛡️", fontSize = 60.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // App Title
        Text(
                text = "ViolenceApp",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
        )

        Text(
                text = "Protección Personal Inteligente",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Biometric Status Card
        BiometricStatusCard(
                status = biometricStatus,
                authState = authState,
                errorMessage = errorMessage
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Action Buttons
        when (biometricStatus) {
            BiometricAuthManager.BiometricStatus.AVAILABLE -> {
                Button(
                        onClick = {
                            authState = AuthState.AUTHENTICATING
                            biometricManager.authenticate()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = authState != AuthState.AUTHENTICATING
                ) {
                    if (authState == AuthState.AUTHENTICATING) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Autenticando...")
                    } else {
                        Text("👆 Autenticar (Huella/Patrón/PIN)")
                    }
                }
            }
            BiometricAuthManager.BiometricStatus.NONE_ENROLLED -> {
                OutlinedButton(onClick = onShowSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("⚙️ Configurar Biometría")
                }
            }
            else -> {
                OutlinedButton(
                        onClick = { /* Fallback o bypass para desarrollo */},
                        modifier = Modifier.fillMaxWidth()
                ) { Text("➡️ Continuar sin Biometría") }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Retry button for failed authentication
        if (authState == AuthState.FAILED || authState == AuthState.ERROR) {
            TextButton(
                    onClick = {
                        authState = AuthState.WAITING
                        errorMessage = ""
                    }
            ) { Text("🔄 Intentar de nuevo") }
        }
    }
}

@Composable
fun BiometricStatusCard(
        status: BiometricAuthManager.BiometricStatus,
        authState: AuthState,
        errorMessage: String
) {
    val (containerColor, contentColor, emoji, title, description) =
            when {
                authState == AuthState.FAILED || authState == AuthState.ERROR ->
                        Tuple5(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.error,
                                "❌",
                                "Error de Autenticación",
                                errorMessage.ifEmpty { "Intenta de nuevo" }
                        )
                authState == AuthState.AUTHENTICATING ->
                        Tuple5(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.secondary,
                                "🔍",
                                "Autenticando...",
                                "Usa tu huella, face ID o toca 'Usar patrón/PIN'"
                        )
                status == BiometricAuthManager.BiometricStatus.AVAILABLE ->
                        Tuple5(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primary,
                                "🔒",
                                "Autenticación Requerida",
                                "Usa tu huella, face ID, patrón o PIN para continuar"
                        )
                else ->
                        Tuple5(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.error,
                                "⚠️",
                                "Autenticación No Disponible",
                                when (status) {
                                    BiometricAuthManager.BiometricStatus.NONE_ENROLLED ->
                                            "Configura huella, face ID o patrón/PIN en Configuración"
                                    BiometricAuthManager.BiometricStatus.NO_HARDWARE ->
                                            "Este dispositivo necesita patrón/PIN configurado"
                                    else -> "Configura un método de seguridad en tu dispositivo"
                                }
                        )
            }

    Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = emoji, fontSize = 48.sp, color = contentColor)

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                    text = description,
                    fontSize = 14.sp,
                    color = contentColor.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
            )
        }
    }
}

data class Tuple5<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
)

enum class AuthState {
    WAITING,
    AUTHENTICATING,
    FAILED,
    ERROR
}
