package com.example.violenceapp.data

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesManager(context: Context) {

    companion object {
        private const val PREF_NAME = "ViolenceAppPrefs"

        private const val KEY_KEYWORD = "keyword"
        private const val KEY_IS_KEYWORD_CONFIGURED = "is_keyword_configured"
        private const val KEY_TELEGRAM_TOKEN = "8397338322:AAGlGZM3p2ZPVjrT68l5RTD8KZvk9vEjS3o"
        private const val KEY_TELEGRAM_CHAT_ID = "1390994727"
        private const val KEY_IS_TELEGRAM_CONFIGURED = "is_telegram_configured"
        private const val KEY_EMERGENCY_CONTACTS_COUNT = "emergency_contacts_count"
        private const val KEY_FIRST_TIME_SETUP = "first_time_setup"
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

    // Configuración de Telegram
    fun saveTelegramConfig(botToken: String, chatId: String) {
        sharedPreferences
                .edit()
                .putString(KEY_TELEGRAM_TOKEN, botToken)
                .putString(KEY_TELEGRAM_CHAT_ID, chatId)
                .putBoolean(
                        KEY_IS_TELEGRAM_CONFIGURED,
                        botToken.isNotEmpty() && chatId.isNotEmpty()
                )
                .apply()
    }

    fun getTelegramToken(): String {
        return sharedPreferences.getString(KEY_TELEGRAM_TOKEN, "") ?: ""
    }

    fun getTelegramChatId(): String {
        return sharedPreferences.getString(KEY_TELEGRAM_CHAT_ID, "") ?: ""
    }

    fun isTelegramConfigured(): Boolean {
        return sharedPreferences.getBoolean(KEY_IS_TELEGRAM_CONFIGURED, false)
    }

    // Contactos de emergencia
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
                "telegramToken" to getTelegramToken(),
                "telegramChatId" to getTelegramChatId(),
                "isTelegramConfigured" to isTelegramConfigured(),
                "emergencyContactsCount" to getEmergencyContactsCount(),
                "hasEmergencyContacts" to hasEmergencyContacts(),
                "isFirstTimeSetup" to isFirstTimeSetup()
        )
    }
}
