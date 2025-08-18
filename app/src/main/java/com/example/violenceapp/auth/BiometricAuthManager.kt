package com.example.violenceapp.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthManager(private val activity: FragmentActivity) {

    private var authenticationCallback: AuthenticationCallback? = null

    interface AuthenticationCallback {
        fun onAuthenticationSucceeded()
        fun onAuthenticationFailed()
        fun onAuthenticationError(errorMessage: String)
    }
    fun hasAnyAuthenticationMethod(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun setAuthenticationCallback(callback: AuthenticationCallback) {
        authenticationCallback = callback
    }

    fun isBiometricAvailable(): BiometricStatus {
        val biometricManager = BiometricManager.from(activity)

        return when (biometricManager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
        ) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                    BiometricStatus.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricStatus.UNSUPPORTED
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> BiometricStatus.UNKNOWN
            else -> BiometricStatus.UNKNOWN
        }
    }

    fun authenticate() {
        val status = isBiometricAvailable()

        if (status != BiometricStatus.AVAILABLE) {
            authenticationCallback?.onAuthenticationError(getStatusMessage(status))
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt =
                BiometricPrompt(
                        activity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {

                            override fun onAuthenticationError(
                                    errorCode: Int,
                                    errString: CharSequence
                            ) {
                                super.onAuthenticationError(errorCode, errString)
                                authenticationCallback?.onAuthenticationError(errString.toString())
                            }

                            override fun onAuthenticationSucceeded(
                                    result: BiometricPrompt.AuthenticationResult
                            ) {
                                super.onAuthenticationSucceeded(result)
                                authenticationCallback?.onAuthenticationSucceeded()
                            }

                            override fun onAuthenticationFailed() {
                                super.onAuthenticationFailed()
                                authenticationCallback?.onAuthenticationFailed()
                            }
                        }
                )

        val promptInfo =
                BiometricPrompt.PromptInfo.Builder()
                        .setTitle("🔒 Autenticación Requerida")
                        .setSubtitle("Usa tu huella dactilar, reconocimiento facial, patrón o PIN")
                        .setDescription(
                                "Esta aplicación requiere autenticación para proteger tu seguridad"
                        )
                        // No ponemos setNegativeButtonText cuando usamos DEVICE_CREDENTIAL
                        // porque Android maneja automáticamente el botón "Usar patrón/PIN"
                        .setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun getStatusMessage(status: BiometricStatus): String {
        return when (status) {
            BiometricStatus.NO_HARDWARE ->
                    "Este dispositivo no tiene sensor biométrico, pero puedes usar patrón/PIN"
            BiometricStatus.HARDWARE_UNAVAILABLE ->
                    "El sensor biométrico no está disponible, usa patrón/PIN"
            BiometricStatus.NONE_ENROLLED ->
                    "No hay datos biométricos registrados. Configura huella/face ID o usa patrón/PIN"
            BiometricStatus.SECURITY_UPDATE_REQUIRED -> "Se requiere una actualización de seguridad"
            BiometricStatus.UNSUPPORTED -> "Autenticación biométrica no soportada, usa patrón/PIN"
            BiometricStatus.UNKNOWN -> "Estado de biometría desconocido"
            BiometricStatus.AVAILABLE -> "Biometría disponible con fallback a patrón/PIN"
        }
    }

    enum class BiometricStatus {
        AVAILABLE,
        NO_HARDWARE,
        HARDWARE_UNAVAILABLE,
        NONE_ENROLLED,
        SECURITY_UPDATE_REQUIRED,
        UNSUPPORTED,
        UNKNOWN
    }
}
