package com.example.violenceapp.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.violenceapp.MainActivity
import java.text.DecimalFormat
import java.util.*

class VoiceRecognitionService : Service(), LocationListener {

    companion object {
        private const val TAG = "VoiceRecognitionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_recognition_channel"
        private const val CHANNEL_NAME = "Reconocimiento de Voz + GPS + Telegram"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false
    private var isListening = false
    private var recognitionCount = 0
    private var alertCount = 0
    private var currentStatus = "Iniciando..."

    // Sistema de ubicación
    private var locationManager: LocationManager? = null
    private var currentLocation: Location? = null
    private var lastLocationUpdate = 0L
    private val decimalFormat = DecimalFormat("#.######")
    private var hasLocationPermission = false

    // Sistema de alertas por Telegram
    private lateinit var alertService: AlertService

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎤 Servicio de reconocimiento de voz + GPS creado")
        createNotificationChannel()
        acquireWakeLock()
        setupSpeechRecognizer()
        setupLocationTracking()

        // Inicializar sistema de alertas por Telegram
        alertService = AlertService(this)
        Log.d(TAG, "🚨 Sistema de alertas por Telegram inicializado")

        // Enviar estado inicial
        broadcastServiceStatus()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            "START_RECOGNITION" -> startRecognition()
            "STOP_RECOGNITION" -> stopRecognition()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy called")
        cleanup()
        broadcastServiceStatus()
    }

    private fun setupLocationTracking() {
        hasLocationPermission =
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED

        if (hasLocationPermission) {
            locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
            Log.d(TAG, "📍 Sistema de GPS inicializado")
        } else {
            Log.w(TAG, "⚠️ Sin permisos de ubicación")
        }
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission || locationManager == null) {
            Log.w(TAG, "⚠️ No se pueden iniciar actualizaciones de ubicación")
            return
        }

        try {
            // Intentar GPS primero (más preciso)
            if (locationManager!!.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager!!.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        30000L, // Cada 30 segundos
                        10f, // O cada 10 metros
                        this,
                        Looper.getMainLooper()
                )
                Log.d(TAG, "📍 GPS tracking iniciado")
            }

            // También usar Network (más rápido para ubicación inicial)
            if (locationManager!!.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager!!.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        60000L, // Cada minuto
                        50f, // O cada 50 metros
                        this,
                        Looper.getMainLooper()
                )
                Log.d(TAG, "🌐 Network location iniciado")
            }

            // Obtener última ubicación conocida para empezar
            getLastKnownLocation()
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Error de permisos para ubicación: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando ubicación: ${e.message}")
        }
    }

    private fun stopLocationUpdates() {
        try {
            locationManager?.removeUpdates(this)
            Log.d(TAG, "📍 GPS tracking detenido")
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo ubicación: ${e.message}")
        }
    }

    private fun getLastKnownLocation() {
        if (!hasLocationPermission || locationManager == null) return

        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

            for (provider in providers) {
                if (locationManager!!.isProviderEnabled(provider)) {
                    val location = locationManager!!.getLastKnownLocation(provider)
                    if (location != null && isLocationBetter(location, currentLocation)) {
                        currentLocation = location
                        lastLocationUpdate = System.currentTimeMillis()
                        Log.d(
                                TAG,
                                "📍 Ubicación inicial obtenida: ${formatLocationShort(location)}"
                        )
                        updateNotification()
                        break
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Error obteniendo última ubicación: ${e.message}")
        }
    }

    // Implementación de LocationListener
    override fun onLocationChanged(location: Location) {
        if (isLocationBetter(location, currentLocation)) {
            currentLocation = location
            lastLocationUpdate = System.currentTimeMillis()

            val accuracy = if (location.hasAccuracy()) "${location.accuracy.toInt()}m" else "?"
            Log.d(TAG, "📍 Nueva ubicación: ${formatLocationShort(location)} (±$accuracy)")

            updateNotification()
        }
    }

    override fun onProviderEnabled(provider: String) {
        Log.d(TAG, "📍 Proveedor habilitado: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.w(TAG, "⚠️ Proveedor deshabilitado: $provider")
    }

    @Deprecated("Deprecated in API level 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun isLocationBetter(location: Location, currentBest: Location?): Boolean {
        if (currentBest == null) return true

        val timeDelta = location.time - currentBest.time
        val isNewer = timeDelta > 2 * 60 * 1000 // 2 minutos
        val isMoreAccurate =
                location.hasAccuracy() &&
                        currentBest.hasAccuracy() &&
                        location.accuracy < currentBest.accuracy

        return isNewer || isMoreAccurate
    }

    private fun formatLocationShort(location: Location): String {
        val lat = decimalFormat.format(location.latitude)
        val lon = decimalFormat.format(location.longitude)
        return "$lat,$lon"
    }

    private fun getCurrentLocationString(): String {
        return if (currentLocation != null) {
            val lat = decimalFormat.format(currentLocation!!.latitude)
            val lon = decimalFormat.format(currentLocation!!.longitude)
            val accuracy =
                    if (currentLocation!!.hasAccuracy()) "${currentLocation!!.accuracy.toInt()}m"
                    else "?"
            val provider = currentLocation!!.provider ?: "unknown"
            val age = (System.currentTimeMillis() - currentLocation!!.time) / 1000

            """
Lat: $lat, Lon: $lon
Precisión: ±$accuracy ($provider)
Actualizada hace: ${age}s
Google Maps: https://maps.google.com/?q=$lat,$lon
            """.trimIndent()
        } else {
            "Ubicación no disponible"
        }
    }

    private fun startRecognition() {
        if (!hasAudioPermission()) {
            Log.e(TAG, "❌ Sin permisos de audio")
            currentStatus = "Sin permisos de audio"
            broadcastServiceStatus()
            stopSelf()
            return
        }

        Log.i(TAG, "✅ Iniciando reconocimiento de voz + GPS")
        isRunning = true
        currentStatus = "Iniciando..."

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Iniciar ubicación si tiene permisos
        if (hasLocationPermission) {
            startLocationUpdates()
        }

        // Enviar estado
        broadcastServiceStatus()

        startListening()

        Log.i(
                TAG,
                "✅ Sistema completo iniciado: Voz${if (hasLocationPermission) " + GPS" else ""} + Telegram"
        )
    }

    private fun stopRecognition() {
        Log.i(TAG, "⏹️ Deteniendo reconocimiento de voz + GPS")
        isRunning = false
        isListening = false
        currentStatus = "Detenido"

        speechRecognizer?.stopListening()
        stopLocationUpdates()

        // Enviar estado antes de detener
        broadcastServiceStatus()

        // Arreglar deprecation warning
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }

        stopSelf()

        Log.i(TAG, "⏹️ Sistema completo detenido")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel?.importance == NotificationManager.IMPORTANCE_NONE) {
                notificationManager.deleteNotificationChannel(CHANNEL_ID)
            }

            val channel =
                    NotificationChannel(
                                    CHANNEL_ID,
                                    CHANNEL_NAME,
                                    NotificationManager.IMPORTANCE_DEFAULT
                            )
                            .apply {
                                description =
                                        "Reconocimiento de voz con GPS y alertas automáticas por Telegram"
                                setShowBadge(true)
                                enableLights(false)
                                enableVibration(false)
                                setSound(null, null)
                                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        // Intent para abrir la app
        val openAppIntent =
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
        val openAppPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        val stopIntent =
                Intent(this, VoiceRecognitionService::class.java).apply {
                    action = "STOP_RECOGNITION"
                }
        val stopPendingIntent =
                PendingIntent.getService(
                        this,
                        0,
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        val statusText =
                when {
                    !isRunning -> "🔇 Detenido"
                    alertCount > 0 -> "🚨 Alertas enviadas: $alertCount"
                    isListening -> "🎤 Escuchando..."
                    else -> "⏸️ En pausa"
                }

        val locationText =
                if (hasLocationPermission && currentLocation != null) {
                    val age = (System.currentTimeMillis() - lastLocationUpdate) / 1000
                    val accuracy =
                            if (currentLocation!!.hasAccuracy())
                                    "${currentLocation!!.accuracy.toInt()}m"
                            else "?"
                    "📍 GPS: ±$accuracy (${age}s)"
                } else if (hasLocationPermission) {
                    "📍 GPS: Buscando..."
                } else {
                    "📍 Sin permisos GPS"
                }

        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🎤 VoiceApp + GPS")
                .setContentText(statusText)
                .setSubText("Reconocimientos: $recognitionCount | $locationText")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(isRunning)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(openAppPendingIntent)
                .addAction(android.R.drawable.ic_media_pause, "Detener", stopPendingIntent)
                .setStyle(
                        NotificationCompat.BigTextStyle()
                                .bigText(
                                        "🎤 Estado: $currentStatus\n📍 $locationText\n📱 Telegram: ${if (alertCount > 0) "$alertCount alertas enviadas" else "Listo"}\n🗣️ Reconocimientos: $recognitionCount"
                                )
                                .setBigContentTitle("🎤 VoiceApp - Protección Completa")
                )
                .build()
    }

    private fun setupSpeechRecognizer() {
        if (!hasAudioPermission()) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(createRecognitionListener())
    }

    private fun createRecognitionListener() =
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    currentStatus = "Escuchando..."
                    Log.d(TAG, "✅ Listo para escuchar - Sistema completo activo")
                    updateNotification()
                    broadcastServiceStatus()
                }

                override fun onBeginningOfSpeech() {
                    currentStatus = "Detectando voz..."
                    Log.d(TAG, "🎤 Comenzando a hablar...")
                    updateNotification()
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    currentStatus = "Procesando..."
                    Log.d(TAG, "🔇 Fin del habla")
                    updateNotification()
                }

                override fun onError(error: Int) {
                    val errorMsg =
                            when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                                SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permisos"
                                SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red"
                                SpeechRecognizer.ERROR_NO_MATCH -> "Sin coincidencias"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                                SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout de voz"
                                else -> "Error desconocido: $error"
                            }
                    Log.e(TAG, "❌ Error de reconocimiento: $errorMsg")
                    isListening = false
                    currentStatus = "Reintentando..."
                    updateNotification()
                    broadcastServiceStatus()
                    scheduleRestart()
                }

                override fun onResults(results: Bundle?) {
                    handleResults(results)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    handlePartialResults(partialResults)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

    private fun handleResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.firstOrNull()?.let { recognizedText ->
            recognitionCount++
            currentStatus = "Texto reconocido"
            Log.i(TAG, "📝 Reconocido [$recognitionCount]: $recognizedText")

            // 🚨 PROCESAR ALERTAS NORMALMENTE 🚨
            processAlert(recognizedText)

            // Enviar broadcast para la UI
            val intent =
                    Intent("VOICE_RECOGNIZED").apply {
                        putExtra("recognized_text", recognizedText)
                        putExtra("recognition_count", recognitionCount)
                    }
            sendBroadcast(intent)

            updateNotification()
            broadcastServiceStatus()
        }

        isListening = false
        scheduleRestart()
    }

    private fun processAlert(text: String) {
        try {
            // Si tenemos ubicación actual, loguear que la vamos a usar
            if (currentLocation != null) {
                Log.i(
                        TAG,
                        "🚨 Procesando alerta con GPS actual: ${formatLocationShort(currentLocation!!)}"
                )
            }

            // Procesar con el sistema de alertas normal
            alertService.processRecognizedText(text)

            // Si fue una alerta, incrementar contador
            if (text.contains("alerta", ignoreCase = true) ||
                            text.contains("auxilio", ignoreCase = true) ||
                            text.contains("ayuda", ignoreCase = true)
            ) {
                alertCount++
                updateNotification()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error procesando alerta: ${e.message}")
        }
    }

    private fun handlePartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        matches?.firstOrNull()?.let { partialText ->
            // Procesar texto parcial para alertas más rápidas
            if (partialText.contains("alerta", ignoreCase = true) ||
                            partialText.contains("auxilio", ignoreCase = true) ||
                            partialText.contains("ayuda", ignoreCase = true)
            ) {
                Log.w(TAG, "⚡ Alerta detectada en texto parcial: $partialText")
                processAlert(partialText)
            }

            val intent = Intent("VOICE_PARTIAL").apply { putExtra("partial_text", partialText) }
            sendBroadcast(intent)
        }
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun startListening() {
        if (isListening || !isRunning || !hasAudioPermission()) return

        val intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(
                            RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                            3000
                    )
                    putExtra(
                            RecognizerIntent
                                    .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                            3000
                    )
                }

        try {
            speechRecognizer?.startListening(intent)
            currentStatus = "Iniciando reconocimiento..."
            updateNotification()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error iniciando reconocimiento", e)
            currentStatus = "Error iniciando"
            updateNotification()
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!isRunning) return

        android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(
                        {
                            if (isRunning && !isListening) {
                                currentStatus = "Reintentando..."
                                updateNotification()
                                startListening()
                            }
                        },
                        1000L
                )
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock =
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG::WakeLock").apply {
                    acquire(60 * 60 * 1000L)
                }
    }

    private fun cleanup() {
        isRunning = false
        isListening = false
        speechRecognizer?.destroy()
        stopLocationUpdates()
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.d(TAG, "🧹 Limpieza del servicio completada")
    }

    // Método para enviar estado del servicio
    private fun broadcastServiceStatus() {
        val intent =
                Intent("SERVICE_STATUS_CHANGED").apply {
                    putExtra("is_running", isRunning)
                    putExtra("is_listening", isListening)
                    putExtra("status_text", currentStatus)
                    putExtra("recognition_count", recognitionCount)
                    putExtra("alert_count", alertCount)
                    putExtra("has_location", currentLocation != null)
                    putExtra(
                            "location_age",
                            if (currentLocation != null)
                                    (System.currentTimeMillis() - lastLocationUpdate) / 1000
                            else -1L
                    )
                }
        sendBroadcast(intent)
        Log.d(
                TAG,
                "📡 Estado enviado: running=$isRunning, listening=$isListening, location=${currentLocation != null}"
        )
    }
}
