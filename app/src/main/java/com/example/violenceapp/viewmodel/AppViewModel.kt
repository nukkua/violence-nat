package com.example.violenceapp.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.violenceapp.data.SharedPreferencesManager
import com.example.violenceapp.services.VoiceRecognitionService
import kotlinx.coroutines.launch

class AppViewModel(private val prefsManager: SharedPreferencesManager) : ViewModel() {

    // Estado existente (tu código original)
    var keywordState by
            mutableStateOf(
                    KeywordState(
                            keyword = prefsManager.getKeyword(),
                            isConfigured = prefsManager.isKeywordConfigured()
                    )
            )
        private set

    // Estado del servicio (ampliado para incluir reconocimiento de voz)
    var serviceState by mutableStateOf(ServiceState())
        private set

    // Estado de configuración (actualizado para WhatsApp)
    var configState by
            mutableStateOf(
                    ConfigState(
                            isWhatsAppConfigured = prefsManager.isWhatsAppConfigured(),
                            isTelegramConfigured =
                                    prefsManager.isTelegramConfigured(), // Mantener compatibilidad
                            hasEmergencyContacts = prefsManager.hasEmergencyContacts(),
                            emergencyContactsCount = prefsManager.getEmergencyContactsCount()
                    )
            )
        private set

    // NUEVO: Estado para reconocimiento de voz
    var voiceRecognitionState by mutableStateOf(VoiceRecognitionState())
        private set

    // Context para el servicio y broadcast receiver
    private var context: Context? = null

    // BroadcastReceiver para escuchar eventos del servicio (adaptado a tus actions)
    private val voiceReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        "VOICE_RECOGNIZED" -> { // Tu servicio usa estas actions
                            val recognizedText = intent.getStringExtra("recognized_text") ?: ""
                            val count = intent.getIntExtra("recognition_count", 0)

                            viewModelScope.launch {
                                voiceRecognitionState =
                                        voiceRecognitionState.copy(
                                                recognizedTexts =
                                                        voiceRecognitionState.recognizedTexts +
                                                                recognizedText,
                                                recognitionCount = count,
                                                partialText = "" // Limpiar texto parcial
                                        )

                                // Actualizar también el serviceState existente
                                serviceState =
                                        serviceState.copy(
                                                detectionsCount = count,
                                                lastDetection = recognizedText,
                                                lastDetectionTime = System.currentTimeMillis()
                                        )
                            }
                        }
                        "VOICE_PARTIAL" -> { // Tu servicio usa estas actions
                            val partial = intent.getStringExtra("partial_text") ?: ""
                            viewModelScope.launch {
                                voiceRecognitionState =
                                        voiceRecognitionState.copy(partialText = partial)
                            }
                        }
                        "SERVICE_STATUS_CHANGED" -> { // NUEVO: Estado del servicio
                            val isRunning = intent.getBooleanExtra("is_running", false)
                            val isListening = intent.getBooleanExtra("is_listening", false)
                            val statusText = intent.getStringExtra("status_text") ?: "Desconocido"
                            val count = intent.getIntExtra("recognition_count", 0)

                            viewModelScope.launch {
                                serviceState =
                                        serviceState.copy(
                                                isRunning = isRunning,
                                                isListening = isListening
                                        )
                                voiceRecognitionState =
                                        voiceRecognitionState.copy(
                                                recognitionCount = count,
                                                serviceStatus = statusText
                                        )
                            }
                        }
                    }
                }
            }

    // Función para inicializar el contexto y registrar el receiver
    fun initializeContext(context: Context) {
        this.context = context.applicationContext
        registerVoiceReceiver()
    }

    private fun registerVoiceReceiver() {
        context?.let { ctx ->
            val filter =
                    IntentFilter().apply {
                        addAction("VOICE_RECOGNIZED") // Actions que usa tu servicio
                        addAction("VOICE_PARTIAL")
                        addAction("SERVICE_STATUS_CHANGED") // NUEVO: Estado del servicio
                    }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.registerReceiver(voiceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                ctx.registerReceiver(voiceReceiver, filter)
            }
        }
    }

    // Funciones para manejar la palabra clave (tu código original)
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
    }

    // Funciones para manejar el servicio (ACTUALIZADAS para usar VoiceRecognitionService real)
    fun startService() {
        if (!keywordState.isConfigured) {
            // Log para debug
            return
        }

        context?.let { ctx ->
            try {
                val intent =
                        Intent(ctx, VoiceRecognitionService::class.java).apply {
                            action = "START_RECOGNITION"
                        }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }

                // Actualizar estado inmediatamente
                serviceState = serviceState.copy(isRunning = true, isListening = true)
            } catch (e: Exception) {
                // Manejar error - log para debug
                serviceState = serviceState.copy(isRunning = false, isListening = false)
            }
        }
                ?: run {
                    // Context es null - esto no debería pasar
                    serviceState = serviceState.copy(isRunning = false, isListening = false)
                }
    }

    fun stopService() {
        context?.let { ctx ->
            try {
                val intent =
                        Intent(ctx, VoiceRecognitionService::class.java).apply {
                            action = "STOP_RECOGNITION"
                        }
                ctx.startService(intent)

                // Actualizar estado inmediatamente
                serviceState = serviceState.copy(isRunning = false, isListening = false)

                // Limpiar datos de reconocimiento
                voiceRecognitionState = VoiceRecognitionState()
            } catch (e: Exception) {
                // Manejar error silenciosamente pero actualizar estado
                serviceState = serviceState.copy(isRunning = false, isListening = false)
            }
        }
                ?: run {
                    // Context es null pero aún así actualizar estado
                    serviceState = serviceState.copy(isRunning = false, isListening = false)
                }
    }

    // NUEVA: Función para limpiar textos reconocidos
    fun clearRecognizedTexts() {
        voiceRecognitionState =
                voiceRecognitionState.copy(
                        recognizedTexts = emptyList(),
                        recognitionCount = 0,
                        partialText = ""
                )

        // También actualizar serviceState
        serviceState =
                serviceState.copy(
                        detectionsCount = 0,
                        lastDetection = null,
                        lastDetectionTime = null
                )
    }

    // Funciones para configuración de WhatsApp (reemplaza Telegram)
    fun updateWhatsAppConfig(phoneNumber: String, emergencyMessage: String) {
        // Guardar en SharedPreferences
        prefsManager.saveWhatsAppConfig(phoneNumber, emergencyMessage)

        // Actualizar estado
        configState =
                configState.copy(
                        isWhatsAppConfigured =
                                phoneNumber.isNotEmpty() && emergencyMessage.isNotEmpty()
                )
    }

    fun getWhatsAppConfig(): Pair<String, String> {
        return Pair(prefsManager.getWhatsAppNumber(), prefsManager.getWhatsAppMessage())
    }

    // Funciones para configuración de Telegram (mantener compatibilidad)
    fun updateTelegramConfig(botToken: String, chatId: String) {
        // Mapear a WhatsApp para compatibilidad
        prefsManager.saveTelegramConfig(botToken, chatId)

        // Actualizar estado
        configState =
                configState.copy(
                        isTelegramConfigured = botToken.isNotEmpty() && chatId.isNotEmpty(),
                        isWhatsAppConfigured = botToken.isNotEmpty() && chatId.isNotEmpty()
                )
    }

    fun getTelegramConfig(): Pair<String, String> {
        return Pair(prefsManager.getTelegramToken(), prefsManager.getTelegramChatId())
    }

    // Funciones para contactos de emergencia (actualizadas)
    fun updateEmergencyContacts(contacts: List<String>) {
        // Guardar en SharedPreferences
        prefsManager.saveEmergencyContacts(contacts)

        // Actualizar estado
        configState =
                configState.copy(
                        emergencyContactsCount = contacts.size,
                        hasEmergencyContacts = contacts.isNotEmpty()
                )
    }

    fun getEmergencyContacts(): List<String> {
        return prefsManager.getEmergencyContacts()
    }

    // Funciones para contactos de emergencia (tu código original)
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

    // Función para verificar si la app está completamente configurada (actualizada para WhatsApp)
    fun isFullyConfigured(): Boolean {
        return keywordState.isConfigured &&
                (configState.isWhatsAppConfigured || configState.isTelegramConfigured) &&
                configState.hasEmergencyContacts
    }

    // Función para obtener configuración completa (debugging) (tu código original)
    fun getFullConfig(): Map<String, Any?> {
        return prefsManager.getFullConfig()
    }

    // Función para resetear toda la configuración (tu código original)
    fun resetAllConfig() {
        prefsManager.clearAllData()

        // Reinicializar estados
        keywordState = KeywordState()
        serviceState = ServiceState()
        configState = ConfigState()
        voiceRecognitionState = VoiceRecognitionState() // NUEVO
    }

    // Función para recargar configuración desde SharedPreferences (actualizada)
    fun reloadFromPreferences() {
        keywordState =
                KeywordState(
                        keyword = prefsManager.getKeyword(),
                        isConfigured = prefsManager.isKeywordConfigured()
                )

        configState =
                ConfigState(
                        isWhatsAppConfigured = prefsManager.isWhatsAppConfigured(),
                        isTelegramConfigured = prefsManager.isTelegramConfigured(),
                        hasEmergencyContacts = prefsManager.hasEmergencyContacts(),
                        emergencyContactsCount = prefsManager.getEmergencyContactsCount()
                )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context?.unregisterReceiver(voiceReceiver)
        } catch (e: Exception) {
            // Receiver ya no registrado
        }
    }
}

// Tus data classes originales
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
        val lastDetectionTime: Long? = null
)

// ConfigState actualizado para WhatsApp
data class ConfigState(
        val isWhatsAppConfigured: Boolean = false,
        val isTelegramConfigured: Boolean = false, // Mantener compatibilidad
        val hasEmergencyContacts: Boolean = false,
        val emergencyContactsCount: Int = 0,
        val isAudioConfigured: Boolean = true // Por defecto configurado
)

// NUEVA: Data class para reconocimiento de voz
data class VoiceRecognitionState(
        val recognizedTexts: List<String> = emptyList(),
        val partialText: String = "",
        val recognitionCount: Int = 0,
        val serviceStatus: String = "Inactivo"
)
