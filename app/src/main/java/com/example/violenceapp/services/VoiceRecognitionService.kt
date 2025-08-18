package com.example.violenceapp.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.violenceapp.MainActivity
import java.util.*

class VoiceRecognitionService : Service() {

    companion object {
        private const val TAG = "VoiceRecognitionService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "voice_recognition_channel"
        private const val CHANNEL_NAME = "Reconocimiento de Voz"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var isRunning = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var recognitionCount = 0
    private var currentStatus = "Grabando..."

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        acquireWakeLock()
        setupSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")

        when (intent?.action) {
            "START_RECOGNITION" -> {
                Log.d(TAG, "Starting voice recognition")
                startForegroundService()
                isRunning = true
                startVoiceRecognition()
            }
            "STOP_RECOGNITION" -> {
                Log.d(TAG, "Stopping voice recognition")
                isRunning = false
                stopVoiceRecognition()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        isRunning = false
        stopVoiceRecognition()
        releaseWakeLock()
        speechRecognizer?.destroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // Verificar si el canal ya existe
            val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (existingChannel != null) {
                Log.d(TAG, "Channel exists with importance: ${existingChannel.importance}")
                // Si existe pero con importancia baja, eliminarlo para recrearlo
                if (existingChannel.importance < NotificationManager.IMPORTANCE_DEFAULT) {
                    notificationManager.deleteNotificationChannel(CHANNEL_ID)
                    Log.d(TAG, "Deleted existing low-importance channel")
                }
            }

            val channel =
                    NotificationChannel(
                                    CHANNEL_ID,
                                    CHANNEL_NAME,
                                    NotificationManager.IMPORTANCE_HIGH
                            )
                            .apply {
                                description =
                                        "IMPORTANTE: Notificaciones de reconocimiento de voz en segundo plano"
                                setShowBadge(true)
                                enableLights(true)
                                lightColor = android.graphics.Color.RED
                                enableVibration(true)
                                vibrationPattern = longArrayOf(100, 200, 300, 400)
                                setSound(
                                        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                                        android.media.AudioAttributes.Builder()
                                                .setUsage(
                                                        android.media.AudioAttributes
                                                                .USAGE_NOTIFICATION
                                                )
                                                .setContentType(
                                                        android.media.AudioAttributes
                                                                .CONTENT_TYPE_SONIFICATION
                                                )
                                                .build()
                                )
                                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                                setBypassDnd(false)
                            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Created notification channel with ID: $CHANNEL_ID")

            // Verificar que se creó correctamente
            val createdChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
            Log.d(TAG, "Channel created with importance: ${createdChannel?.importance}")

            // Para Samsung, forzar configuraciones especiales
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                Log.d(TAG, "Samsung device detected - applying special settings")
                channel.importance = NotificationManager.IMPORTANCE_HIGH
            }
        } else {
            Log.d(TAG, "Android version < O, no channel creation needed")
        }
    }

    private fun startForegroundService() {
        currentStatus = "Grabando..."
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "Foreground service started")
    }

    private fun createNotification(): Notification {
        // Intent para abrir la app
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        // Intent para detener el servicio
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

        // Construir notificación expandible
        val builder =
                NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("🎤 Reconocimiento de Voz")
                        .setContentText(currentStatus)
                        .setSubText("Reconocimientos: $recognitionCount")
                        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                        .setOngoing(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_SERVICE)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setContentIntent(openAppPendingIntent)
                        .addAction(android.R.drawable.ic_media_pause, "Detener", stopPendingIntent)
                        .addAction(
                                android.R.drawable.ic_menu_view,
                                "Abrir App",
                                openAppPendingIntent
                        )
                        .setStyle(
                                NotificationCompat.BigTextStyle()
                                        .bigText(
                                                "$currentStatus\n\nReconocimientos totales: $recognitionCount\nEstado: ${if (isListening) "Escuchando" else "En espera"}"
                                        )
                                        .setBigContentTitle("🎤 Reconocimiento de Voz Activo")
                        )

        return builder.build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock =
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG::WakeLock").apply {
                    acquire(60 * 60 * 1000L)
                }
        Log.d(TAG, "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
    }

    private fun setupSpeechRecognizer() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Audio permission not granted")
            currentStatus = "❌ Sin permisos de audio"
            updateNotification()
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(
                object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        Log.d(TAG, "Ready for speech")
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {
                        Log.d(TAG, "Beginning of speech")
                    }

                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        Log.d(TAG, "End of speech")
                    }

                    override fun onError(error: Int) {
                        Log.e(TAG, "Speech recognition error: $error")
                        isListening = false

                        android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(
                                        {
                                            if (isRunning && !isListening) {
                                                startListening()
                                            }
                                        },
                                        2000
                                )
                    }

                    override fun onResults(results: Bundle?) {
                        val matches =
                                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        matches?.let { resultList ->
                            if (resultList.isNotEmpty()) {
                                val recognizedText = resultList[0]
                                recognitionCount++

                                Log.i(TAG, "Recognized [$recognitionCount]: $recognizedText")

                                // Enviar broadcast a la app
                                val broadcastIntent =
                                        Intent("VOICE_RECOGNIZED").apply {
                                            putExtra("recognized_text", recognizedText)
                                            putExtra("recognition_count", recognitionCount)
                                        }
                                sendBroadcast(broadcastIntent)
                            }
                        }

                        isListening = false

                        // Continuar escuchando después de 1 segundo
                        android.os.Handler(android.os.Looper.getMainLooper())
                                .postDelayed(
                                        {
                                            if (isRunning) {
                                                startListening()
                                            }
                                        },
                                        1000
                                )
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches =
                                partialResults?.getStringArrayList(
                                        SpeechRecognizer.RESULTS_RECOGNITION
                                )
                        matches?.let {
                            if (it.isNotEmpty()) {
                                Log.d(TAG, "Partial result: ${it[0]}")
                                // Enviar resultado parcial a la app
                                val broadcastIntent =
                                        Intent("VOICE_PARTIAL").apply {
                                            putExtra("partial_text", it[0])
                                        }
                                sendBroadcast(broadcastIntent)
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                }
        )
    }

    private fun startVoiceRecognition() {
        Log.d(TAG, "Starting voice recognition")
        currentStatus = "🚀 Iniciando reconocimiento..."
        updateNotification()
        startListening()
    }

    private fun startListening() {
        if (isListening || !isRunning) return

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
            Log.d(TAG, "Speech recognizer started listening")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognizer: ${e.message}")
            currentStatus = "❌ Error al iniciar: ${e.message}"
            updateNotification()
        }
    }

    private fun stopVoiceRecognition() {
        Log.d(TAG, "Stopping voice recognition")
        isListening = false
        speechRecognizer?.stopListening()
        currentStatus = "🛑 Detenido"
        updateNotification()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
