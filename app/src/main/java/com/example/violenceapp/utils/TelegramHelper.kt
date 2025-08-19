package com.example.violenceapp.utils

import android.content.Context
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TelegramHelper(private val context: Context) {

    companion object {
        private const val TAG = "TelegramHelper"

        // 🚨 TU CONFIGURACIÓN REAL
        private const val BOT_TOKEN = "8397338322:AAGlGZM3p2ZPVjrT68l5RTD8KZvk9vEjS3o"
        private const val CHAT_ID = "1390994727"

        private const val TELEGRAM_API_URL = "https://api.telegram.org/bot"
    }

    /**
     * Envía mensaje automáticamente por Telegram ✅ Completamente automático sin intervención del
     * usuario ✅ Funciona en background
     */
    suspend fun sendAutomaticMessage(message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "📤 Enviando mensaje de emergencia...")

                val url = URL("$TELEGRAM_API_URL$BOT_TOKEN/sendMessage")
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    setRequestProperty("User-Agent", "ViolenceApp/1.0")
                    doOutput = true
                    connectTimeout = 15000 // 15 segundos
                    readTimeout = 20000 // 20 segundos
                }

                // Formatear mensaje con HTML
                val formattedMessage = formatEmergencyMessage(message)

                // Preparar datos para envío
                val postData =
                        "chat_id=${URLEncoder.encode(CHAT_ID, "UTF-8")}" +
                                "&text=${URLEncoder.encode(formattedMessage, "UTF-8")}" +
                                "&parse_mode=HTML" +
                                "&disable_web_page_preview=false"

                // Enviar request
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(postData)
                    writer.flush()
                }

                // Procesar respuesta
                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)

                    if (jsonResponse.getBoolean("ok")) {
                        Log.i(TAG, "✅ MENSAJE DE EMERGENCIA ENVIADO POR TELEGRAM")
                        return@withContext true
                    } else {
                        val errorDesc = jsonResponse.optString("description", "Error desconocido")
                        Log.e(TAG, "❌ Error en respuesta de Telegram: $errorDesc")
                        return@withContext false
                    }
                } else {
                    val errorResponse =
                            connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e(TAG, "❌ Error HTTP $responseCode: $errorResponse")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error enviando mensaje por Telegram: ${e.message}")
                return@withContext false
            }
        }
    }

    /** Envía ubicación en tiempo real por Telegram */
    suspend fun sendLocationMessage(
            latitude: Double,
            longitude: Double,
            caption: String = ""
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "📍 Enviando ubicación: $latitude, $longitude")

                val url = URL("$TELEGRAM_API_URL$BOT_TOKEN/sendLocation")
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    doOutput = true
                    connectTimeout = 15000
                    readTimeout = 20000
                }

                val postData =
                        "chat_id=${URLEncoder.encode(CHAT_ID, "UTF-8")}" +
                                "&latitude=$latitude" +
                                "&longitude=$longitude" +
                                "&live_period=300" + // Ubicación en vivo por 5 minutos
                                if (caption.isNotEmpty())
                                        "&caption=${URLEncoder.encode(caption, "UTF-8")}"
                                else ""

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(postData)
                    writer.flush()
                }

                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)

                    if (jsonResponse.getBoolean("ok")) {
                        Log.i(TAG, "✅ UBICACIÓN ENVIADA POR TELEGRAM")
                        return@withContext true
                    } else {
                        val errorDesc = jsonResponse.optString("description", "Error desconocido")
                        Log.e(TAG, "❌ Error enviando ubicación: $errorDesc")
                        return@withContext false
                    }
                } else {
                    Log.e(TAG, "❌ Error enviando ubicación: HTTP $responseCode")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error enviando ubicación por Telegram: ${e.message}")
                return@withContext false
            }
        }
    }

    /** Valida la configuración del bot */
    suspend fun validateBotConfiguration(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "🔍 Validando configuración del bot...")

                val url = URL("$TELEGRAM_API_URL$BOT_TOKEN/getMe")
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)

                    if (jsonResponse.getBoolean("ok")) {
                        val botInfo = jsonResponse.getJSONObject("result")
                        val botName = botInfo.getString("username")
                        val botFirstName = botInfo.getString("first_name")
                        Log.i(TAG, "✅ Bot configurado: $botFirstName (@$botName)")
                        return@withContext true
                    }
                }

                Log.e(TAG, "❌ Bot token inválido o bot inactivo")
                return@withContext false
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error validando bot: ${e.message}")
                return@withContext false
            }
        }
    }

    /** Envía mensaje de prueba */
    suspend fun sendTestMessage(): Boolean {
        val testMessage =
                """
🧪 <b>MENSAJE DE PRUEBA</b>

✅ Tu bot de Telegram está funcionando correctamente
🕐 Hora: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}
🚨 Listo para enviar alertas de emergencia automáticamente

<i>Este es un mensaje de prueba del sistema de alertas</i>
        """.trimIndent()

        return sendAutomaticMessage(testMessage)
    }

    /** Formatea el mensaje de emergencia con HTML */
    private fun formatEmergencyMessage(message: String): String {
        return """
🚨 <b>ALERTA DE EMERGENCIA</b> 🚨

$message

⚠️ <i>Este mensaje fue enviado automáticamente por el sistema de reconocimiento de voz</i>
        """.trimIndent()
    }

    /** Obtiene información del bot configurado */
    suspend fun getBotInfo(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$TELEGRAM_API_URL$BOT_TOKEN/getMe")
                val connection = url.openConnection() as HttpURLConnection

                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                }

                val responseCode = connection.responseCode

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)

                    if (jsonResponse.getBoolean("ok")) {
                        val botInfo = jsonResponse.getJSONObject("result")
                        val botName = botInfo.getString("username")
                        val botFirstName = botInfo.getString("first_name")
                        return@withContext "$botFirstName (@$botName)"
                    }
                }

                return@withContext null
            } catch (e: Exception) {
                Log.e(TAG, "Error obteniendo info del bot: ${e.message}")
                return@withContext null
            }
        }
    }
}
