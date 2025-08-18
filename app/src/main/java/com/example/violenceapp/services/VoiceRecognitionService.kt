package com.example.violenceapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
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
import com.example.violenceapp.data.SharedPreferencesManager
import java.util.*

class VoiceRecognitionService : Service() {

    companion object {
        private const val TAG = "VoiceRecognitionService"

        // Actions
        const val ACTION_START_RECOGNITION = "START_RECOGNITION"
        const val ACTION_STOP_RECOGNITION = "STOP_RECOGNITION"
        const val ACTION_VOICE_RECOGNIZED = "VOICE_RECOGNIZED"
        const val ACTION_VOICE_PARTIAL = "VOICE_PARTIAL"
        const val ACTION_VOICE_ERROR = "VOICE_ERROR"
        const val ACTION_SERVICE_STATUS = "SERVICE_STATUS"

        // Notification
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "VoiceRecognitionChannel"
        private const val CHANNEL_NAME = "Reconocimiento de Voz"

        // Timing
        private const val RECOGNITION_RESTART_DELAY = 1500L
        private const val QUICK_RESTART_DELAY = 500L
        private const val MAX_RESTART_ATTEMPTS = 5
        private const val RESTART_RESET_TIME = 30000L // 30 seconds
    }

    // Core components
    private var wakeLock: PowerManager.WakeLock? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var prefsManager: SharedPreferencesManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // State management
    private var isRunning = false
    private var isListening = false
    private var recognitionCount = 0
    private var currentKeyword = ""
    private var currentStatus = "Iniciando..."

    // Error recovery
    private var restartAttempts = 0
    private var lastRestartTime = 0L

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        prefsManager = SharedPreferencesManager(this)
        currentKeyword = prefsManager?.getKeyword()?.ifEmpty { "alerta" } ?: "alerta"

        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_RECOGNITION -> startVoiceRecognition()
            ACTION_STOP_RECOGNITION -> stopVoiceRecognition()
        }

        return START_STICKY // Auto-restart if killed by system
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startVoiceRecognition() {
        if (isRunning) {
            Log.d(TAG, "Service already running")
            return
        }

        Log.d(TAG, "Starting voice recognition service")

        // Check permissions
        if (!hasAudioPermission()) {
            Log.e(TAG, "Audio permission not granted")
            currentStatus = "❌ Sin permisos de audio"
            stopVoiceRecognition()
            return
        }

        isRunning = true
        currentKeyword = prefsManager?.getKeyword()?.ifEmpty { "alerta" } ?: "alerta"
        currentStatus = "🎤 Iniciando reconocimiento..."

        startForeground(NOTIFICATION_ID, createNotification())

        setupSpeechRecognizer()
        startListening()

        // Broadcast service status
        broadcastServiceStatus("started")
    }

    private fun stopVoiceRecognition() {
        Log.d(TAG, "Stopping voice recognition service")

        isRunning = false
        isListening = false
        currentStatus = "🛑 Detenido"

        stopListening()
        destroySpeechRecognizer()

        stopForeground(STOP_FOREGROUND_REMOVE)
        releaseWakeLock()

        broadcastServiceStatus("stopped")
        stopSelf()
    }

    private fun setupSpeechRecognizer() {
        destroySpeechRecognizer() // Clean up any existing instance

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            Log.d(TAG, "Speech recognizer setup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up speech recognizer: ${e.message}")
            handleRecognitionError("Error al configurar reconocedor: ${e.message}")
        }
    }

    private fun startListening() {
        if (!isRunning || isListening || speechRecognizer == null) {
            return
        }

        val intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("es", "BO")) // <— NEW
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-BO") // <— NEW
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    // Improve recognition settings
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
            isListening = true
            currentStatus = "🎤 Escuchando..."
            updateNotification()
            Log.d(TAG, "Started listening for speech")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition: ${e.message}")
            handleRecognitionError("Error al iniciar reconocimiento: ${e.message}")
        }
    }

    private fun stopListening() {
        if (isListening && speechRecognizer != null) {
            try {
                speechRecognizer?.stopListening()
                isListening = false
                Log.d(TAG, "Stopped listening")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping speech recognition: ${e.message}")
            }
        }
    }

    private fun createRecognitionListener() =
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                    currentStatus = "🎤 Listo para escuchar"
                    updateNotification()
                    resetRestartAttempts()
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Beginning of speech detected")
                    currentStatus = "🗣️ Detectando voz..."
                    updateNotification()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Optional: Could show audio level indicator
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "End of speech")
                    currentStatus = "🔄 Procesando..."
                    updateNotification()
                }

                override fun onError(error: Int) {
                    val errorMessage =
                            when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                                SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                        "Permisos insuficientes"
                                SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red"
                                SpeechRecognizer.ERROR_NO_MATCH -> "Sin coincidencias"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                                SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout de voz"
                                else -> "Error desconocido: $error"
                            }

                    Log.w(TAG, "Speech recognition error: $errorMessage")
                    isListening = false

                    // Handle different errors appropriately
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            // These are normal, just restart quickly
                            scheduleRestart(QUICK_RESTART_DELAY)
                        }
                        else -> {
                            // More serious errors, use normal restart with backoff
                            handleRecognitionError(errorMessage)
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    processResults(results, false)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    processResults(partialResults, true)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

    private fun processResults(results: Bundle?, isPartial: Boolean) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val recognizedText = matches[0].lowercase()

            if (isPartial) {
                // Send partial result
                val intent =
                        Intent(ACTION_VOICE_PARTIAL).apply {
                            putExtra("partial_text", recognizedText)
                            putExtra("keyword", currentKeyword)
                        }
                sendBroadcast(intent)
            } else {
                // Final result
                recognitionCount++
                isListening = false

                val containsKeyword = recognizedText.contains(currentKeyword.lowercase())

                Log.i(
                        TAG,
                        "Recognition [$recognitionCount]: $recognizedText (keyword: $containsKeyword)"
                )

                // Send recognition result
                val intent =
                        Intent(ACTION_VOICE_RECOGNIZED).apply {
                            putExtra("recognized_text", recognizedText)
                            putExtra("recognition_count", recognitionCount)
                            putExtra("contains_keyword", containsKeyword)
                            putExtra("keyword", currentKeyword)
                        }
                sendBroadcast(intent)

                if (containsKeyword) {
                    currentStatus = "🚨 ¡Palabra clave detectada!"
                    updateNotification()
                    // Trigger alert handling here if needed
                }

                // Continue listening
                scheduleRestart(QUICK_RESTART_DELAY)
            }
        }
    }

    private fun handleRecognitionError(reason: String) {
        if (!isRunning) return

        restartAttempts++
        Log.w(TAG, "Recognition error (attempt $restartAttempts): $reason")

        val intent =
                Intent(ACTION_VOICE_ERROR).apply {
                    putExtra("error_message", reason)
                    putExtra("restart_attempts", restartAttempts)
                }
        sendBroadcast(intent)

        if (restartAttempts >= MAX_RESTART_ATTEMPTS) {
            Log.e(TAG, "Max restart attempts reached, stopping service")
            currentStatus = "❌ Error persistente - Detenido"
            updateNotification()
            stopVoiceRecognition()
            return
        }

        val delay = RECOGNITION_RESTART_DELAY * restartAttempts // Exponential backoff
        scheduleRestart(delay)
    }

    private fun scheduleRestart(delay: Long) {
        if (!isRunning) return

        mainHandler.postDelayed(
                {
                    if (isRunning && !isListening) {
                        setupSpeechRecognizer()
                        startListening()
                    }
                },
                delay
        )
    }

    private fun resetRestartAttempts() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastRestartTime > RESTART_RESET_TIME) {
            restartAttempts = 0
        }
        lastRestartTime = currentTime
    }

    private fun destroySpeechRecognizer() {
        speechRecognizer?.let {
            try {
                it.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
            }
        }
        speechRecognizer = null
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock =
                    powerManager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "$TAG::VoiceRecognitionWakeLock"
                    )
            wakeLock?.acquire(60 * 60 * 1000L) // 1 hour maximum
            Log.d(TAG, "WakeLock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            try {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing WakeLock: ${e.message}")
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                    NotificationChannel(
                                    CHANNEL_ID,
                                    CHANNEL_NAME,
                                    NotificationManager.IMPORTANCE_LOW
                            )
                            .apply {
                                description = "Servicio de reconocimiento de voz en segundo plano"
                                setShowBadge(false)
                                setSound(null, null) // Silent
                            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    private fun createNotification(): Notification {
        // Open app intent
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        // Stop service intent
        val stopIntent =
                Intent(this, VoiceRecognitionService::class.java).apply {
                    action = ACTION_STOP_RECOGNITION
                }
        val stopPendingIntent =
                PendingIntent.getService(
                        this,
                        0,
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ViolenceApp Protección")
                .setContentText(currentStatus)
                .setSubText("Reconocimientos: $recognitionCount | Palabra: \"$currentKeyword\"")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(openAppPendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_media_pause, "Detener", stopPendingIntent)
                .addAction(android.R.drawable.ic_menu_view, "Abrir", openAppPendingIntent)
                .setStyle(
                        NotificationCompat.BigTextStyle()
                                .bigText(
                                        "$currentStatus\n\nPalabra clave: \"$currentKeyword\"\nReconocimientos: $recognitionCount"
                                )
                                .setBigContentTitle("🎤 Reconocimiento Activo")
                )
                .build()
    }

    private fun updateNotification() {
        if (isRunning) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        }
    }

    private fun broadcastServiceStatus(status: String) {
        val intent =
                Intent(ACTION_SERVICE_STATUS).apply {
                    putExtra("service_status", status)
                    putExtra("keyword", currentKeyword)
                    putExtra("recognition_count", recognitionCount)
                    putExtra("is_listening", isListening)
                }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopVoiceRecognition()
    }
}
