package com.example.violenceapp.data

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesManager(val context: Context) {
    companion object {
        private const val PREF_NAME = "ViolenceAppPrefs"
        private const val KEY_KEYWORD = "keyword"
        private const val KEY_IS_KEYWORD_CONFIGURED = "is_keyword_configured"

        // Cambiar de Telegram a WhatsApp
        private const val KEY_WHATSAPP_NUMBER = "whatsapp_number"
        private const val KEY_WHATSAPP_MESSAGE = "whatsapp_message"
        private const val KEY_IS_WHATSAPP_CONFIGURED = "is_whatsapp_configured"

        private const val KEY_EMERGENCY_CONTACTS_COUNT = "emergency_contacts_count"
        private const val KEY_FIRST_TIME_SETUP = "first_time_setup"

        // Lista de contactos de emergencia (números de WhatsApp)
        private const val KEY_EMERGENCY_CONTACTS = "emergency_contacts"
    }

    private val sharedPreferences: SharedPreferences =
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Palabra clave
    fun saveKeyword(keyword: String) {
        sharedPreferences
                .edit()
                .putString(KEY_KEYWORD, keyword.lowercase().trim())
                .putBoolean(KEY_IS_KEYWORD_CONFIGURED, keyword.isNotEmpty())
                .apply()
    }

    fun getKeyword(): String {
        return sharedPreferences.getString(KEY_KEYWORD, "") ?: ""
    }

    fun isKeywordConfigured(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_KEYWORD_CONFIGURED, false) &&
                getKeyword().isNotEmpty()
    }

    fun clearKeyword() {
        sharedPreferences
                .edit()
                .remove(KEY_KEYWORD)
                .putBoolean(KEY_IS_KEYWORD_CONFIGURED, false)
                .apply()
    }

    // Configuración de WhatsApp (reemplaza Telegram)
    fun saveWhatsAppConfig(phoneNumber: String, emergencyMessage: String) {
        sharedPreferences
                .edit()
                .putString(KEY_WHATSAPP_NUMBER, phoneNumber)
                .putString(KEY_WHATSAPP_MESSAGE, emergencyMessage)
                .putBoolean(
                        KEY_IS_WHATSAPP_CONFIGURED,
                        phoneNumber.isNotEmpty() && emergencyMessage.isNotEmpty()
                )
                .apply()
    }

    fun getWhatsAppNumber(): String {
        return sharedPreferences.getString(KEY_WHATSAPP_NUMBER, "") ?: ""
    }

    fun getWhatsAppMessage(): String {
        return sharedPreferences.getString(
                KEY_WHATSAPP_MESSAGE,
                "🚨 EMERGENCIA: Se ha detectado la palabra clave de alerta"
        )
                ?: ""
    }

    fun isWhatsAppConfigured(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_WHATSAPP_CONFIGURED, false)
    }

    // Métodos de compatibilidad con tu AppViewModel existente (mantener nombres de Telegram)
    fun saveTelegramConfig(botToken: String, chatId: String) {
        // Mapear a WhatsApp para compatibilidad
        saveWhatsAppConfig(chatId, botToken) // Usar chatId como número y token como mensaje
    }

    fun getTelegramToken(): String {
        return getWhatsAppMessage() // Mapear mensaje a token para compatibilidad
    }

    fun getTelegramChatId(): String {
        return getWhatsAppNumber() // Mapear número a chatId para compatibilidad
    }

    fun isTelegramConfigured(): Boolean {
        return isWhatsAppConfigured() // Mapear para compatibilidad
    }

    // Contactos de emergencia (lista de números de WhatsApp)
    fun saveEmergencyContacts(contacts: List<String>) {
        val contactsString = contacts.joinToString(",")
        sharedPreferences
                .edit()
                .putString(KEY_EMERGENCY_CONTACTS, contactsString)
                .putInt(KEY_EMERGENCY_CONTACTS_COUNT, contacts.size)
                .apply()
    }

    fun getEmergencyContacts(): List<String> {
        val contactsString = sharedPreferences.getString(KEY_EMERGENCY_CONTACTS, "") ?: ""
        return if (contactsString.isNotEmpty()) {
            contactsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }
    }

    fun saveEmergencyContactsCount(count: Int) {
        sharedPreferences.edit().putInt(KEY_EMERGENCY_CONTACTS_COUNT, count).apply()
    }

    fun getEmergencyContactsCount(): Int {
        return sharedPreferences.getInt(KEY_EMERGENCY_CONTACTS_COUNT, 0)
    }

    fun hasEmergencyContacts(): Boolean {
        return getEmergencyContactsCount() > 0
    }

    // Primera vez
    fun isFirstTimeSetup(): Boolean {
        return sharedPreferences.getBoolean(KEY_FIRST_TIME_SETUP, true)
    }

    fun markFirstTimeSetupComplete() {
        sharedPreferences.edit().putBoolean(KEY_FIRST_TIME_SETUP, false).apply()
    }

    // Limpiar toda la configuración
    fun clearAllData() {
        sharedPreferences.edit().clear().apply()
    }

    // Exportar configuración completa (para debugging)
    fun getFullConfig(): Map<String, Any?> {
        return mapOf(
                "keyword" to getKeyword(),
                "isKeywordConfigured" to isKeywordConfigured(),
                "whatsappNumber" to getWhatsAppNumber(),
                "whatsappMessage" to getWhatsAppMessage(),
                "isWhatsAppConfigured" to isWhatsAppConfigured(),
                "emergencyContacts" to getEmergencyContacts(),
                "emergencyContactsCount" to getEmergencyContactsCount(),
                "hasEmergencyContacts" to hasEmergencyContacts(),
                "isFirstTimeSetup" to isFirstTimeSetup(),
                // Mantener compatibilidad con nombres antiguos
                "telegramToken" to getTelegramToken(),
                "telegramChatId" to getTelegramChatId(),
                "isTelegramConfigured" to isTelegramConfigured()
        )
    }
}
