package com.example.violenceapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.violenceapp.data.SharedPreferencesManager

class AppViewModel(private val prefsManager: SharedPreferencesManager) : ViewModel() {

    var keywordState by
            mutableStateOf(
                    KeywordState(
                            keyword = prefsManager.getKeyword(),
                            isConfigured = prefsManager.isKeywordConfigured()
                    )
            )
        private set

    // Estado del servicio
    var serviceState by mutableStateOf(ServiceState())
        private set

    // Estado de configuración
    var configState by
            mutableStateOf(
                    ConfigState(
                            isTelegramConfigured = prefsManager.isTelegramConfigured(),
                            hasEmergencyContacts = prefsManager.hasEmergencyContacts(),
                            emergencyContactsCount = prefsManager.getEmergencyContactsCount()
                    )
            )
        private set

    // Funciones para manejar la palabra clave
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

    // Funciones para manejar el servicio
    fun startService() {
        if (keywordState.isConfigured) {
            serviceState = serviceState.copy(isRunning = true, isListening = true)
        }
    }

    fun stopService() {
        serviceState = serviceState.copy(isRunning = false, isListening = false)
    }

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

        // Reinicializar estados
        keywordState = KeywordState()
        serviceState = ServiceState()
        configState = ConfigState()
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
        val lastDetectionTime: Long? = null
)

data class ConfigState(
        val isTelegramConfigured: Boolean = false,
        val hasEmergencyContacts: Boolean = false,
        val emergencyContactsCount: Int = 0,
        val isAudioConfigured: Boolean = true // Por defecto configurado
)
