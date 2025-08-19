package com.example.violenceapp.services

import android.content.Context
import android.util.Log
import com.example.violenceapp.data.AlertConfiguration
import com.example.violenceapp.utils.LocationHelper
import com.example.violenceapp.utils.TelegramHelper
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlertService(private val context: Context) {

    companion object {
        private const val TAG = "AlertService"
    }

    private val locationHelper = LocationHelper(context)
    private val telegramHelper = TelegramHelper(context)
    private val alertConfig = AlertConfiguration()

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    /**
     * Procesa texto reconocido y activa alerta si detecta palabra clave
     */
    fun processRecognizedText(text: String) {
        if (!alertConfig.isEnabled) {
            Log.d(TAG, "Alertas deshabilitadas")
            return
        }

        // Verificar si el texto contiene la palabra clave (case insensitive)
        if (text.contains(alertConfig.triggerWord, ignoreCase = true)) {
            Log.w(TAG, "🚨 PALABRA CLAVE DETECTADA: '$text'")
            triggerEmergencyAlert(text)
        }
    }

    /**
     * Activa alerta de emergencia completa
     */
    private fun triggerEmergencyAlert(triggeredText: String) {
        Log.w(TAG, "🚨 ACTIVANDO ALERTA DE EMERGENCIA 🚨")

        coroutineScope.launch {
            try {
                // Obtener ubicación actual
                val locationString = locationHelper.getCurrentLocation() ?: "Ubicación no disponible"

                // Obtener tiempo actual
                val currentTime = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())

                // Preparar mensaje usando la plantilla
                val emergencyMessage = alertConfig
                        .messageTemplate
                        .replace("{location}", locationString)
                        .replace("{time}", currentTime)
                        .replace("{trigger}", triggeredText)

                Log.i(TAG, "Mensaje de emergencia preparado")

                // 🚨 ENVÍO AUTOMÁTICO POR TELEGRAM 🚨
                val messageSent = telegramHelper.sendAutomaticMessage(emergencyMessage)

                if (messageSent) {
                    Log.i(TAG, "✅ ALERTA ENVIADA EXITOSAMENTE POR TELEGRAM")
                    
                    // Intentar enviar ubicación en tiempo real si está disponible
                    sendLiveLocationIfAvailable()
                    
                    // Mostrar notificación de éxito
                    launch(Dispatchers.Main) { 
                        showAlertSentNotification(emergencyMessage)
                    }
                } else {
                    Log.e(TAG, "❌ FALLO AL ENVIAR ALERTA POR TELEGRAM")
                    
                    // Mostrar notificación de error
                    launch(Dispatchers.Main) { 
                        showAlertFailedNotification("Error de conexión o configuración")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error procesando alerta de emergencia: ${e.message}")
                launch(Dispatchers.Main) { 
                    showAlertFailedNotification("Error interno: ${e.message}")
                }
            }
        }
    }

    /**
     * Envía ubicación en tiempo real si está disponible
     */
    private suspend fun sendLiveLocationIfAvailable() {
        try {
            if (!locationHelper.hasLocationPermission()) {
                Log.w(TAG, "Sin permisos de ubicación para envío en tiempo real")
                return
            }

            // Intentar obtener coordenadas precisas para ubicación en vivo
            val locationString = locationHelper.getCurrentLocation()
            
            if (locationString != null && locationString.contains("Lat:")) {
                // Extraer coordenadas del string de ubicación
                val latRegex = Regex("Lat: ([+-]?\\d*\\.?\\d+)")
                val lonRegex = Regex("Lon: ([+-]?\\d*\\.?\\d+)")
                
                val latMatch = latRegex.find(locationString)
                val lonMatch = lonRegex.find(locationString)
                
                if (latMatch != null && lonMatch != null) {
                    val latitude = latMatch.groupValues[1].toDouble()
                    val longitude = lonMatch.groupValues[1].toDouble()
                    
                    val locationSent = telegramHelper.sendLocationMessage(
                        latitude, 
                        longitude, 
                        "📍 Ubicación en tiempo real - Emergencia activa"
                    )
                    
                    if (locationSent) {
                        Log.i(TAG, "✅ Ubicación en tiempo real enviada")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando ubicación en tiempo real: ${e.message}")
        }
    }

    /**
     * Muestra notificación de alerta enviada exitosamente
     */
    private fun showAlertSentNotification(message: String) {
        try {
            val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as
                            android.app.NotificationManager

            // Crear canal para alertas críticas
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel =
                        android.app.NotificationChannel(
                                        "emergency_alerts",
                                        "Alertas de Emergencia",
                                        android.app.NotificationManager.IMPORTANCE_HIGH
                                )
                                .apply {
                                    description = "Notificaciones de alertas de emergencia enviadas por Telegram"
                                    enableLights(true)
                                    lightColor = android.graphics.Color.GREEN
                                    enableVibration(true)
                                    vibrationPattern = longArrayOf(0, 300, 200, 300, 200, 300)
                                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification =
                    androidx.core.app.NotificationCompat.Builder(context, "emergency_alerts")
                            .setContentTitle("✅ Alerta Enviada por Telegram")
                            .setContentText("Tu mensaje de emergencia fue enviado automáticamente")
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                            .setVibrate(longArrayOf(0, 300, 200, 300))
                            .setAutoCancel(true)
                            .setStyle(
                                    androidx.core.app.NotificationCompat.BigTextStyle()
                                            .bigText("✅ Alerta enviada exitosamente por Telegram:\n\n${message.take(100)}...")
                            )
                            .build()

            notificationManager.notify(9999, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando notificación de éxito: ${e.message}")
        }
    }

    /**
     * Muestra notificación de error al enviar alerta
     */
    private fun showAlertFailedNotification(errorDetails: String) {
        try {
            val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as
                            android.app.NotificationManager

            val notification =
                    androidx.core.app.NotificationCompat.Builder(context, "emergency_alerts")
                            .setContentTitle("❌ Error Enviando Alerta")
                            .setContentText("No se pudo enviar el mensaje por Telegram")
                            .setSmallIcon(android.R.drawable.ic_dialog_alert)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ERROR)
                            .setAutoCancel(true)
                            .setStyle(
                                    androidx.core.app.NotificationCompat.BigTextStyle()
                                            .bigText("❌ Error: $errorDetails\n\nVerifica tu conexión y configuración de Telegram")
                            )
                            .build()

            notificationManager.notify(9998, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando notificación de error: ${e.message}")
        }
    }

    /**
     * Valida la configuración de Telegram
     */
    suspend fun validateTelegramConfiguration(): Boolean {
        return try {
            telegramHelper.validateBotConfiguration()
        } catch (e: Exception) {
            Log.e(TAG, "Error validando configuración de Telegram: ${e.message}")
            false
        }
    }

    /**
     * Envía mensaje de prueba
     */
    suspend fun sendTestAlert(): Boolean {
        return try {
            telegramHelper.sendTestMessage()
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando mensaje de prueba: ${e.message}")
            false
        }
    }

    /**
     * Obtiene información del bot configurado
     */
    suspend fun getBotInfo(): String? {
        return try {
            telegramHelper.getBotInfo()
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo información del bot: ${e.message}")
            null
        }
    }
}
