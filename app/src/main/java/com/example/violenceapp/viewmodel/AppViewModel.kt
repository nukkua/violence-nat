package com.example.violenceapp.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.violenceapp.data.SharedPreferencesManager
import com.example.violenceapp.services.VoiceRecognitionService

class AppViewModel(
        private val prefsManager: SharedPreferencesManager,
        private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "AppViewModel"
        private const val PREFS_SERVICE_STATE = "service_state_prefs"
        private const val KEY_SERVICE_WAS_RUNNING = "service_was_running"
        private const val KEY_SERVICE_START_TIME = "service_start_time"
        private const val KEY_APP_PAUSED_TIME = "app_paused_time"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    // SharedPreferences específicas para el estado del servicio
    private val serviceStatePrefs =
            context.getSharedPreferences(PREFS_SERVICE_STATE, Context.MODE_PRIVATE)

    var keywordState by
            mutableStateOf(
                    KeywordState(
                            keyword = prefsManager.getKeyword(),
                            isConfigured = prefsManager.isKeywordConfigured()
                    )
            )
        private set

    var serviceState by mutableStateOf(ServiceState())
        private set

    var configState by
            mutableStateOf(
                    ConfigState(
                            isTelegramConfigured = prefsManager.isTelegramConfigured(),
                            hasEmergencyContacts = prefsManager.hasEmergencyContacts(),
                            emergencyContactsCount = prefsManager.getEmergencyContactsCount()
                    )
            )
        private set

    fun setKeyword(keyword: String) {
        val cleanKeyword = keyword.lowercase().trim()

        // Guardar en SharedPreferences
        prefsManager.saveKeyword(cleanKeyword)

        // Actualizar estado
        keywordState =
                keywordState.copy(
                        keyword = cleanKeyword,
                        isConfigured = cleanKeyword.isNotEmpty(),
                        lastModified = System.currentTimeMillis()
                )

        Log.d(TAG, "Keyword set: $cleanKeyword")
    }

    fun clearKeyword() {
        // Limpiar de SharedPreferences
        prefsManager.clearKeyword()

        // Actualizar estado
        keywordState =
                keywordState.copy(
                        keyword = "",
                        isConfigured = false,
                        lastModified = System.currentTimeMillis()
                )

        // Detener servicio si estaba corriendo
        if (serviceState.isRunning) {
            stopService()
        }

        Log.d(TAG, "Keyword cleared")
    }

    private val serviceReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent == null) return
                    Log.d(TAG, "Received broadcast: ${intent.action}")

                    when (intent.action) {
                        VoiceRecognitionService.ACTION_VOICE_PARTIAL -> {
                            val partial = intent.getStringExtra("partial_text").orEmpty()
                            mainHandler.post {
                                serviceState =
                                        serviceState.copy(
                                                isListening = true,
                                                partialText = partial,
                                                errorMessage = null
                                        )
                            }
                        }
                        VoiceRecognitionService.ACTION_VOICE_RECOGNIZED -> {
                            val text = intent.getStringExtra("recognized_text").orEmpty()
                            val contains = intent.getBooleanExtra("contains_keyword", false)
                            val newHistory = (serviceState.recognizedHistory + text).takeLast(50)
                            mainHandler.post {
                                serviceState =
                                        serviceState.copy(
                                                isListening = false,
                                                detectionsCount = serviceState.detectionsCount + 1,
                                                lastDetection = text,
                                                lastDetectionTime = System.currentTimeMillis(),
                                                partialText = "",
                                                recognizedHistory = newHistory,
                                                lastHitWasKeyword = contains,
                                                errorMessage = null
                                        )
                            }
                        }
                        VoiceRecognitionService.ACTION_VOICE_ERROR -> {
                            val err = intent.getStringExtra("error_message")
                            Log.w(TAG, "Voice recognition error: $err")
                            mainHandler.post {
                                serviceState =
                                        serviceState.copy(isListening = false, errorMessage = err)
                            }
                        }
                        VoiceRecognitionService.ACTION_SERVICE_STATUS -> {
                            val running = intent.getStringExtra("service_status") == "started"
                            val listening = intent.getBooleanExtra("is_listening", false)
                            Log.d(
                                    TAG,
                                    "Service status update - Running: $running, Listening: $listening"
                            )
                            mainHandler.post {
                                serviceState =
                                        serviceState.copy(
                                                isRunning = running,
                                                isListening = listening
                                        )
                            }
                        }
                    }
                }
            }

    init {
        Log.d(TAG, "AppViewModel initialized")

        // Registra receiver para acciones del servicio
        val filter =
                IntentFilter().apply {
                    addAction(VoiceRecognitionService.ACTION_VOICE_PARTIAL)
                    addAction(VoiceRecognitionService.ACTION_VOICE_RECOGNIZED)
                    addAction(VoiceRecognitionService.ACTION_VOICE_ERROR)
                    addAction(VoiceRecognitionService.ACTION_SERVICE_STATUS)
                }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION") context.registerReceiver(serviceReceiver, filter)
        }

        // Verificar estado inicial del servicio
        checkServiceStatus()
    }

    override fun onCleared() {
        try {
            context.unregisterReceiver(serviceReceiver)
            Log.d(TAG, "Service receiver unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver", e)
        }
        super.onCleared()
    }

    fun startService() {
        if (!keywordState.isConfigured) {
            Log.w(TAG, "Cannot start service - keyword not configured")
            return
        }

        try {
            Log.d(TAG, "Starting voice recognition service")
            val intent =
                    Intent(context, VoiceRecognitionService::class.java).apply {
                        action = VoiceRecognitionService.ACTION_START_RECOGNITION
                    }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }

            // Actualizar estado inmediatamente
            serviceState =
                    serviceState.copy(isRunning = true, isListening = true, errorMessage = null)

            // Guardar estado para persistencia
            saveServiceState(true)

            Log.d(TAG, "Service start command sent successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Error starting service, retrying in 600ms", t)

            // Retry con delay
            Handler(Looper.getMainLooper())
                    .postDelayed(
                            {
                                try {
                                    val retryIntent =
                                            Intent(context, VoiceRecognitionService::class.java)
                                                    .apply {
                                                        action =
                                                                VoiceRecognitionService
                                                                        .ACTION_START_RECOGNITION
                                                    }

                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(retryIntent)
                                    } else {
                                        context.startService(retryIntent)
                                    }

                                    serviceState =
                                            serviceState.copy(
                                                    isRunning = true,
                                                    isListening = true,
                                                    errorMessage = null
                                            )
                                    saveServiceState(true)

                                    Log.d(TAG, "Service retry successful")
                                } catch (retryError: Throwable) {
                                    Log.e(TAG, "Service retry failed", retryError)
                                    serviceState =
                                            serviceState.copy(
                                                    isRunning = false,
                                                    isListening = false,
                                                    errorMessage =
                                                            "Error al iniciar: ${retryError.message}"
                                            )
                                }
                            },
                            600
                    )
        }
    }

    fun stopService() {
        try {
            Log.d(TAG, "Stopping voice recognition service")
            val intent =
                    Intent(context, VoiceRecognitionService::class.java).apply {
                        action = VoiceRecognitionService.ACTION_STOP_RECOGNITION
                    }
            context.startService(intent)

            // Actualizar estado
            serviceState =
                    serviceState.copy(
                            isRunning = false,
                            isListening = false,
                            partialText = "",
                            errorMessage = null
                    )

            // Limpiar estado guardado
            saveServiceState(false)

            Log.d(TAG, "Service stop command sent successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service", e)
        }
    }

    fun clearTranscript() {
        serviceState =
                serviceState.copy(
                        recognizedHistory = emptyList(),
                        partialText = "",
                        lastDetection = null,
                        errorMessage = null
                )
        Log.d(TAG, "Transcript cleared")
    }

    fun updateDetection(text: String, containsKeyword: Boolean) {
        val newHistory = (serviceState.recognizedHistory + text).takeLast(50)
        serviceState =
                serviceState.copy(
                        detectionsCount = serviceState.detectionsCount + 1,
                        lastDetection = text,
                        lastDetectionTime = System.currentTimeMillis(),
                        recognizedHistory = newHistory,
                        lastHitWasKeyword = containsKeyword
                )
    }

    // =================== NUEVAS FUNCIONES DE PERSISTENCIA ===================

    /** Guarda el estado del servicio en SharedPreferences */
    private fun saveServiceState(isRunning: Boolean) {
        try {
            serviceStatePrefs
                    .edit()
                    .putBoolean(KEY_SERVICE_WAS_RUNNING, isRunning)
                    .putLong(
                            KEY_SERVICE_START_TIME,
                            if (isRunning) System.currentTimeMillis() else 0
                    )
                    .apply()

            Log.d(TAG, "Service state saved - Running: $isRunning")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving service state", e)
        }
    }

    /** Verifica si el servicio debería estar corriendo basado en el estado guardado */
    fun checkServiceStatus() {
        try {
            val wasRunning = serviceStatePrefs.getBoolean(KEY_SERVICE_WAS_RUNNING, false)
            val startTime = serviceStatePrefs.getLong(KEY_SERVICE_START_TIME, 0)

            Log.d(TAG, "Checking service status - Was running: $wasRunning, Start time: $startTime")

            if (wasRunning && startTime > 0) {
                // Si han pasado menos de 10 minutos, probablemente el servicio debería seguir
                // corriendo
                val timeDiff = System.currentTimeMillis() - startTime
                val tenMinutes = 10 * 60 * 1000L

                if (timeDiff < tenMinutes) {
                    Log.d(TAG, "Service should be running (started ${timeDiff / 1000}s ago)")

                    // Actualizar estado para reflejar que debería estar corriendo
                    serviceState = serviceState.copy(isRunning = true, isListening = true)
                } else {
                    Log.d(
                            TAG,
                            "Service was running too long ago (${timeDiff / 1000}s), not restoring"
                    )
                    saveServiceState(false) // Limpiar estado obsoleto
                }
            } else {
                Log.d(TAG, "Service was not running previously")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking service status", e)
        }
    }

    /** Llamar cuando la app va a background */
    fun onAppPaused() {
        try {
            if (serviceState.isRunning) {
                serviceStatePrefs
                        .edit()
                        .putLong(KEY_APP_PAUSED_TIME, System.currentTimeMillis())
                        .apply()
                Log.d(TAG, "App paused while service running")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving app pause time", e)
        }
    }

    /** Llamar cuando la app vuelve a foreground */
    fun onAppResumed() {
        try {
            val pauseTime = serviceStatePrefs.getLong(KEY_APP_PAUSED_TIME, 0)
            if (pauseTime > 0) {
                val pauseDuration = System.currentTimeMillis() - pauseTime
                Log.d(TAG, "App resumed after ${pauseDuration}ms")

                // Si el servicio debería estar corriendo, verificar su estado
                if (serviceState.isRunning ||
                                serviceStatePrefs.getBoolean(KEY_SERVICE_WAS_RUNNING, false)
                ) {
                    checkServiceStatus()
                }

                // Limpiar timestamp
                serviceStatePrefs.edit().remove(KEY_APP_PAUSED_TIME).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling app resume", e)
        }
    }

    /** Inicialización completa del ViewModel - llamar desde MainActivity.onCreate */
    fun initialize() {
        Log.d(TAG, "Initializing AppViewModel")
        checkServiceStatus()
        reloadFromPreferences()
        Log.d(TAG, "AppViewModel initialization complete")
    }

    // =================== FUNCIONES EXISTENTES (sin cambios) ===================

    // Funciones para configuración de Telegram
    fun updateTelegramConfig(botToken: String, chatId: String) {
        // Guardar en SharedPreferences
        prefsManager.saveTelegramConfig(botToken, chatId)

        // Actualizar estado
        configState =
                configState.copy(
                        isTelegramConfigured = botToken.isNotEmpty() && chatId.isNotEmpty()
                )
    }

    fun getTelegramConfig(): Pair<String, String> {
        return Pair(prefsManager.getTelegramToken(), prefsManager.getTelegramChatId())
    }

    // Funciones para contactos de emergencia
    fun updateContactsConfig(contactsCount: Int) {
        // Guardar en SharedPreferences
        prefsManager.saveEmergencyContactsCount(contactsCount)

        // Actualizar estado
        configState =
                configState.copy(
                        emergencyContactsCount = contactsCount,
                        hasEmergencyContacts = contactsCount > 0
                )
    }

    // Función para verificar si la app está completamente configurada
    fun isFullyConfigured(): Boolean {
        return keywordState.isConfigured &&
                configState.isTelegramConfigured &&
                configState.hasEmergencyContacts
    }

    // Función para obtener configuración completa (debugging)
    fun getFullConfig(): Map<String, Any?> {
        return prefsManager.getFullConfig()
    }

    // Función para resetear toda la configuración
    fun resetAllConfig() {
        prefsManager.clearAllData()

        // Limpiar también el estado del servicio
        serviceStatePrefs.edit().clear().apply()

        // Reinicializar estados
        keywordState = KeywordState()
        serviceState = ServiceState()
        configState = ConfigState()

        Log.d(TAG, "All configuration reset")
    }

    // Función para recargar configuración desde SharedPreferences
    fun reloadFromPreferences() {
        keywordState =
                KeywordState(
                        keyword = prefsManager.getKeyword(),
                        isConfigured = prefsManager.isKeywordConfigured()
                )

        configState =
                ConfigState(
                        isTelegramConfigured = prefsManager.isTelegramConfigured(),
                        hasEmergencyContacts = prefsManager.hasEmergencyContacts(),
                        emergencyContactsCount = prefsManager.getEmergencyContactsCount()
                )

        Log.d(TAG, "Configuration reloaded from preferences")
    }
}

// Estados de datos (sin cambios)
data class KeywordState(
        val keyword: String = "",
        val isConfigured: Boolean = false,
        val lastModified: Long = System.currentTimeMillis()
)

data class ServiceState(
        val isRunning: Boolean = false,
        val isListening: Boolean = false,
        val detectionsCount: Int = 0,
        val lastDetection: String? = null,
        val lastDetectionTime: Long? = null,
        val partialText: String = "",
        val recognizedHistory: List<String> = emptyList(),
        val lastHitWasKeyword: Boolean = false,
        val errorMessage: String? = null
)

data class ConfigState(
        val isTelegramConfigured: Boolean = false,
        val hasEmergencyContacts: Boolean = false,
        val emergencyContactsCount: Int = 0,
        val isAudioConfigured: Boolean = true // Por defecto configurado
)
