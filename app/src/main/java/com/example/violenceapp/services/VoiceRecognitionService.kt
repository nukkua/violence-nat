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
        private const val CHANNEL_NAME = "Reconocimiento de Voz + Telegram"
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false
    private var isListening = false
    private var recognitionCount = 0
    private var alertCount = 0
    private var currentStatus = "Iniciando..."

    // Sistema de alertas por Telegram
    private lateinit var alertService: AlertService

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎤 Servicio de reconocimiento de voz creado")
        createNotificationChannel()
        acquireWakeLock()
        setupSpeechRecognizer()

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
        broadcastServiceStatus() // Enviar estado final
    }

    private fun startRecognition() {
        if (!hasAudioPermission()) {
            Log.e(TAG, "❌ Sin permisos de audio")
            currentStatus = "Sin permisos de audio"
            broadcastServiceStatus()
            stopSelf()
            return
        }

        Log.i(TAG, "✅ Iniciando reconocimiento de voz")
        isRunning = true
        currentStatus = "Iniciando..."

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Enviar estado
        broadcastServiceStatus()

        startListening()

        Log.i(TAG, "✅ Reconocimiento de voz iniciado - Sistema de alertas por Telegram activo")
    }

    private fun stopRecognition() {
        Log.i(TAG, "⏹️ Deteniendo reconocimiento de voz")
        isRunning = false
        isListening = false
        currentStatus = "Detenido"

        speechRecognizer?.stopListening()

        // Enviar estado antes de detener
        broadcastServiceStatus()

        // Arreglar deprecation warning
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }

        stopSelf()

        Log.i(TAG, "⏹️ Reconocimiento de voz detenido - Sistema de alertas desactivado")
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
                                        "Reconocimiento de voz con alertas automáticas por Telegram"
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🎤 Reconocimiento de Voz")
                .setContentText(statusText)
                .setSubText("Reconocimientos: $recognitionCount")
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
                                        "🎤 Estado: $currentStatus\n📱 Telegram: ${if (alertCount > 0) "$alertCount alertas enviadas" else "Listo"}\n🗣️ Reconocimientos: $recognitionCount"
                                )
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
                    Log.d(TAG, "✅ Listo para escuchar - Sistema de alertas activo")
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

            // 🚨 PROCESAR ALERTAS POR TELEGRAM 🚨
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

    private fun processAlert(text: String) {
        try {
            // Procesar con el sistema de alertas
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
        wakeLock?.let { if (it.isHeld) it.release() }
        Log.d(TAG, "🧹 Limpieza del servicio completada")
    }

    // NUEVO: Método para enviar estado del servicio
    private fun broadcastServiceStatus() {
        val intent =
                Intent("SERVICE_STATUS_CHANGED").apply {
                    putExtra("is_running", isRunning)
                    putExtra("is_listening", isListening)
                    putExtra("status_text", currentStatus)
                    putExtra("recognition_count", recognitionCount)
                    putExtra("alert_count", alertCount)
                }
        sendBroadcast(intent)
        Log.d(
                TAG,
                "📡 Estado enviado: running=$isRunning, listening=$isListening, status=$currentStatus"
        )
    }
}
